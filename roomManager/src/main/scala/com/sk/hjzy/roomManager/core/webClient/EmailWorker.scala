package com.sk.hjzy.roomManager.core.webClient


import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.roomManager.utils.EmailUtil
import org.slf4j.LoggerFactory
import com.sk.hjzy.roomManager.Boot.{executor, scheduler, timeout}

import concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Author: wqf
  * Date: 2020/1/18
  * Time: 23:59
  */
object EmailWorker {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object WorkStopKey extends Command

  case class GetVerifyCode4Register(replyTo: ActorRef[Boolean]) extends Command

  case class Verify4Register(code: String, replyTo: ActorRef[Boolean]) extends Command

  case class GenVerifyCode4Login(replyTo: ActorRef[Boolean]) extends Command

  case class Verify4Login(code: String, replyTo: ActorRef[Boolean]) extends Command

  case class GenVerifyCode4Password(replyTo: ActorRef[Boolean]) extends Command

  case class Verify4Password(code: String, replyTo: ActorRef[Boolean]) extends Command

  case object StopWork extends Command

  case class BusyTimeOut(msg: String) extends Command

  private final val InitTime = Some(5.minutes)

  private final case object BehaviorChangeKey

  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: BusyTimeOut = BusyTimeOut("busy time error")
  ) extends Command

  //切换behavior的状态
  private[this] def switchBehavior(
    ctx: ActorContext[Command],
    behaviorName: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: BusyTimeOut = BusyTimeOut("busy time error"))(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command]
  ) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(
      timer.startSingleTimer(BehaviorChangeKey, timeOut, _)
    )
    stashBuffer.unstashAll(ctx, behavior)
  }



  def create(email: String): Behavior[Command] =
    Behaviors.setup[Command]{ ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command]{ implicit timer =>
        timer.startSingleTimer(WorkStopKey, StopWork, 10.minutes)
        work(email)
      }
    }

  def work(email: String, registerCode: String = "", loginCode: String = "", changeCode: String = "")
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command]{ (ctx, msg) =>
      msg match {
        case msg: GetVerifyCode4Register =>
          val verifyCode = genVerifyCode
          log.info(s"code:$verifyCode")
          Future{
            EmailUtil.send("您的注册验证码", verifyCode, List(email))
          }.onComplete{
            case Success(value) =>
              msg.replyTo ! true
              ctx.self ! SwitchBehavior("work", work(email, verifyCode, loginCode, changeCode))
            case Failure(ex) =>
              msg.replyTo ! false
              log.info(s"send verify code fail: ${ex.getMessage}")
          }
          switchBehavior(ctx, "busy", busy(), InitTime, BusyTimeOut("init time out"))

        case msg: GenVerifyCode4Login =>
          val loginCode = genVerifyCode
          log.info(s"code:$loginCode")
          Future{
            EmailUtil.send("您的登录验证码", loginCode, List(email))
          }.onComplete{
            case Success(value) =>
              msg.replyTo ! true
              ctx.self ! SwitchBehavior("work", work(email, registerCode, loginCode, changeCode))
            case Failure(ex) =>
              msg.replyTo ! false
              log.info(s"send verify code fail: ${ex.getMessage}")
          }
          switchBehavior(ctx, "busy", busy(), InitTime, BusyTimeOut("init time out"))

        case msg: GenVerifyCode4Password =>
          val passwordCode = genVerifyCode
          log.info(s"code:$passwordCode")
          Future{
            EmailUtil.send("您的重置密码验证码", passwordCode, List(email))
          }.onComplete{
            case Success(_) =>
              msg.replyTo ! true
              ctx.self ! SwitchBehavior("work", work(email, registerCode, loginCode, passwordCode))
            case Failure(ex) =>
              msg.replyTo ! false
              log.info(s"send verify code fail: ${ex.getMessage}")
          }
          switchBehavior(ctx, "busy", busy(), InitTime, BusyTimeOut("init time out"))

        case msg: Verify4Register =>
          log.info(s"msg.code:${msg.code}")
          log.info(s"registerCode:$registerCode")
          if(msg.code == registerCode){
            msg.replyTo ! true
            work(email, "", loginCode, changeCode)
          }else{
            msg.replyTo ! false
            Behaviors.same
          }

        case msg: Verify4Login =>
          if(msg.code == loginCode){
            msg.replyTo ! true
            work(email, registerCode, "", changeCode)
          }else{
            msg.replyTo ! false
            Behaviors.same
          }

        case msg: Verify4Password =>
          log.info(s"changeCode:$changeCode")
          log.info(s"msg.code:${msg.code}")
          if(msg.code == changeCode){
            msg.replyTo ! true
            work(email, registerCode, loginCode, "")
          }else{
            msg.replyTo ! false
            Behaviors.same
          }
      }
    }

  def busy()(implicit stashBuffer: StashBuffer[Command], timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, behavior, durationOpt, timeOut) =>
          switchBehavior(ctx, name, behavior, durationOpt, timeOut)

        case msg: BusyTimeOut =>
          log.info("send verify code timeout")
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behaviors.same
      }
    }

  def genVerifyCode: String ={
    val charList:List[String] = List("0","1","2","3","4","5","6","7","8","9")
    val SIZE:Int = 5 //五位随机数
    var sb:String = ""
    for(i <- 0 until SIZE){
      val ranNum = scala.util.Random.nextInt(10)
      sb += charList(ranNum)
    }
    sb.toString
  }
}
