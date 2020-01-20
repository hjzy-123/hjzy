package com.sk.hjzy.roomManager.core.webClient

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import org.slf4j.LoggerFactory

/**
  * Author: wqf
  * Date: 2020/1/18
  * Time: 23:47
  */
object EmailManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class ChildDead(childName: String) extends Command

  case class GetVerifyCode4Register(email : String, replyTo: ActorRef[Boolean]) extends Command

  case class Verify4Register(email: String, code: String, replyTo: ActorRef[Boolean]) extends Command

  case class GenVerifyCode4Login(email: String, replyTo: ActorRef[Boolean]) extends Command

  case class Verify4Login(email: String, code: String, replyTo: ActorRef[Boolean]) extends Command

  def create(): Behavior[Command] =
    Behaviors.setup[Command]{ ctx  =>
      log.info("email manager actor is starting...")
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command]{ implicit timer =>
        idle()
      }
    }

  def idle(): Behavior[Command] =
    Behaviors.receive[Command]{ (ctx, msg) =>
      msg match {
        case msg: GetVerifyCode4Register =>
          getEmailWorker(ctx, msg.email) ! EmailWorker.GetVerifyCode4Register(msg.replyTo)
          Behaviors.same

        case msg: Verify4Register =>
          if(ctx.child(s"emailWorker-${msg.email}").isEmpty){
            msg.replyTo ! false
            log.info("false")
          }else{
            log.info("true")
            val actor = ctx.child(s"emailWorker-${msg.email}").get.unsafeUpcast[EmailWorker.Command]
            actor ! EmailWorker.Verify4Register(msg.code, msg.replyTo)
          }
          Behaviors.same

        case msg: GenVerifyCode4Login =>
          getEmailWorker(ctx, msg.email) ! EmailWorker.GenVerifyCode4Login(msg.replyTo)
          Behaviors.same

        case msg: Verify4Login =>
          if(ctx.child(s"emailWorker-${msg.email}").isEmpty){
            msg.replyTo ! false
            log.info("false")
          }else{
            log.info("true")
            val actor = ctx.child(s"emailWorker-${msg.email}").get.unsafeUpcast[EmailWorker.Command]
            actor ! EmailWorker.Verify4Login(msg.code, msg.replyTo)
          }
          Behaviors.same
      }
    }

  def getEmailWorker(ctx: ActorContext[Command], email: String): ActorRef[EmailWorker.Command] = {
    val childName = s"emailWorker-$email"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(EmailWorker.create(email), childName)
      ctx.watchWith(actor, ChildDead(childName))
      actor
    }.unsafeUpcast[EmailWorker.Command]
  }
}
