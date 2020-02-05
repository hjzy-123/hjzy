package com.sk.hjzy.roomManager.core

import akka.NotUsed
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.seekloud.byteobject.MiddleBufferInJvm
import com.sk.hjzy.protocol.ptcl.CommonInfo
import com.sk.hjzy.protocol.ptcl.CommonInfo._
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import com.sk.hjzy.roomManager.Boot.{executor, roomManager, scheduler}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.utils.RtpClient
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * actor由UserManager创建
  * 处理并向客户端分发webSocket消息
  */

object UserActor {

  import scala.language.implicitConversions
  import org.seekloud.byteobject.ByteObject._

  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)
  private final val BusyTime = Some(5.minutes)

  trait Command

  /**web socket 消息*/
  final case class WebSocketMsg(msg:Option[WsMsgClient]) extends Command
  final case class DispatchMsg(msg:WsMsgRm,closeRoom:Boolean) extends Command
  case object CompleteMsgClient extends Command
  case class FailMsgClient(ex:Throwable) extends Command
  case class UserClientActor(actor:ActorRef[WsMsgRm]) extends Command

  /**http消息*/
  final case class UserLogin(roomId:Long,userId:Long) extends Command with UserManager.Command//新用户请求mpd的时候处理这个消息，更新roomActor中的列表
  case class UserLeft[U](actorRef: ActorRef[U]) extends Command
  final case class ChildDead[U](userId: Long,childRef: ActorRef[U]) extends Command with UserManager.Command
  final case object ChangeBehaviorToInit extends Command
  final case object SendHeartBeat extends  Command

  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends Command

  private case class TimeOut(msg: String) extends Command

  private final case object BehaviorChangeKey

  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  /**
    * userId
    * */
  def create(userId: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      log.info(s"userActor-$userId is starting...")
      ctx.setReceiveTimeout(30.seconds, CompleteMsgClient)
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
        init(userId,None)
      }
    }
  }

  private def init(
                    userId:Long,
                    roomIdOpt:Option[Long]
                  )(
    implicit stashBuffer:StashBuffer[Command],
    sendBuffer: MiddleBufferInJvm,
    timer: TimerScheduler[Command]
  ):Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case UserClientActor(clientActor) =>
            ctx.watchWith(clientActor, UserLeft(clientActor))
            timer.startPeriodicTimer("HeartBeatKey_" + userId, SendHeartBeat, 10.seconds)
            switchBehavior(ctx, "audience", audience(userId,clientActor,roomIdOpt.get))


          case UserLogin(roomId,`userId`) =>
            //先发一个用户登陆，再切换到其他的状态
            roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.join,roomId,userId,Some(ctx.self))
            init(userId,Some(roomId))

          case TimeOut(m) =>
            log.debug(s"${ctx.self.path} is time out when busy,msg=${m}")
            Behaviors.stopped

          case unknown =>
            if(userId == Common.TestConfig.TEST_USER_ID){
              log.debug(s"${ctx.self.path} 测试房间的房主actor，不处理其他类型的消息msg=$unknown")
            }else{
              log.debug(s"${ctx.self.path} recv an unknown msg:${msg} in init state...")
              stashBuffer.stash(unknown)
            }

            Behavior.same
        }
    }
  }

  private def busy()
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]
    ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path.name} is time out when busy, msg=$m")
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }


  private def sink(userActor: ActorRef[UserActor.Command]): Sink[Command, NotUsed] = ActorSink.actorRef[Command](
    ref = userActor,
    onCompleteMessage = CompleteMsgClient,
    onFailureMessage = { e =>
      e.printStackTrace()
      FailMsgClient(e)
    }
  )

  def flow(userActor: ActorRef[UserActor.Command]):Flow[WebSocketMsg,WsMsgManager,Any] = {
    val in = Flow[WebSocketMsg].to(sink(userActor))
    val out = ActorSource.actorRef[WsMsgManager](
      completionMatcher = {
        case CompleteMsgRm =>
          println("flow got CompleteMsgRm msg")
//          userActor ! HostCloseRoom(None)
      },
      failureMatcher = {
        case FailMsgRm(e) =>
          e.printStackTrace()
          e
      },
      bufferSize = 256,
      overflowStrategy = OverflowStrategy.dropHead
    ).mapMaterializedValue(outActor => userActor ! UserClientActor(outActor))
    Flow.fromSinkAndSource(in,out)
  }
}
