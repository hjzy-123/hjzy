package com.sk.hjzy.roomManager.core

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.{Wrap, WsMsgClient}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.core.UserActor.ChildDead
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.Boot.{executor, roomManager, scheduler, timeout}
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{ChangeBehaviorToHost, ChangeBehaviorToParticipant}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
 * 由Boot创建
 * 建立webSocket流
 * 管理UserActor
 */
object UserManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  case class TimeOut(msg:String) extends Command

  final case class WebSocketFlowSetup(userId:Long,roomId:Long, hostId:Long, replyTo:ActorRef[Option[Flow[Message,Message,Any]]]) extends Command

  final case class SetupWs(uidOpt:Long, tokenOpt:String ,roomId:Long, hostId:Long, replyTo: ActorRef[Option[Flow[Message, Message, Any]]]) extends Command

  def create():Behavior[Command] = {
    log.debug(s"RoomManager start...")
    Behaviors.setup[Command]{
      ctx =>
        implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command]{
          implicit timer =>
            idle()
        }
    }
  }

  private def idle()
                  (implicit stashBuffer: StashBuffer[Command],timer:TimerScheduler[Command]):Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case SetupWs(uid, token, roomId,hostId,replyTo) =>
          UserInfoDao.verifyUserWithToken(uid, token).onComplete {
            case Success(f) =>
              if (f) {
                log.debug(s"${ctx.self.path} ws start")
                val flowFuture: Future[Option[Flow[Message, Message, Any]]] = ctx.self ? (WebSocketFlowSetup(uid,roomId, hostId, _))
                flowFuture.map(replyTo ! _)
              } else {
                replyTo ! None
              }
            case Failure(e) =>
              log.error(s"getBindWx future error: $e")
              replyTo ! None
          }
          Behaviors.same

        case WebSocketFlowSetup(userId,roomId, hostId,replyTo) =>
            log.info(s"${ctx.self.path} websocket will setup for user:$userId")
            getUserActorOpt(userId,ctx) match{
              case Some(actor) =>
                log.debug(s"${ctx.self.path} setup websocket error:该账户已经登录userId=$userId")
                //TODO 重复登录相关处理
                //                actor ! UserActor.UserLogin(roomId,userId)
                //                replyTo ! Some(setupWebSocketFlow(actor))
                replyTo ! None
              case None =>
                val userActor = getUserActor(userId,hostId,ctx)
                userActor ! UserActor.UserLogin(roomId,userId)
                replyTo ! Some(setupWebSocketFlow(userActor))
            }
          Behaviors.same

        case ChildDead(userId,actor) =>
          log.debug(s"${ctx.self.path} the child = ${ctx.children}")
          Behaviors.same

        case r@ChangeBehaviorToHost(userId, hostId) =>
          getUserActorOpt(userId, ctx) match {
            case Some(actor) =>
              actor ! r
            case None =>
              log.info(s"${userId}-userActor不存在")
          }
          Behaviors.same

        case r@ChangeBehaviorToParticipant(userId, hostId) =>
          getUserActorOpt(userId, ctx) match {
            case Some(actor) =>
              actor ! r
            case None =>
              log.info(s"${userId}-userActor不存在")
          }
          Behaviors.same

        case x =>
          log.warn(s"unknown msg: $x")
          Behaviors.unhandled
      }
    }

  private def getUserActor(userId:Long, hostId:Long, ctx: ActorContext[Command]): ActorRef[UserActor.Command] = {
    val childrenName = s"userActor-$userId"
    ctx.child(childrenName).getOrElse {
      val actor = ctx.spawn(UserActor.create(userId, hostId), childrenName)
      ctx.watchWith(actor, ChildDead(userId,actor))
      actor
    }.unsafeUpcast[UserActor.Command]
  }

  private def getUserActorOpt(userId:Long,ctx:ActorContext[Command]): Option[ActorRef[UserActor.Command]] = {
    val childrenName = s"userActor-$userId"
    ctx.child(childrenName).map(_.unsafeUpcast[UserActor.Command])
  }

  private def setupWebSocketFlow(userActor:ActorRef[UserActor.Command]):Flow[Message,Message,Any]  = {
    import org.seekloud.byteobject.ByteObject._
    import org.seekloud.byteobject.MiddleBufferInJvm

    import scala.language.implicitConversions

    implicit def parseJsonString2WsMsgClient(s: String): Option[WsMsgClient] = {
      import io.circe.generic.auto._
      import io.circe.parser._

      try {
        val wsMsg = decode[WsMsgClient](s).right.get
        Some(wsMsg)
      } catch {
        case e: Exception =>
          log.info(s"parse front msg failed when json parse,s=${s},e=$e")
          None
      }
    }
    Flow[Message]
      .collect {
        case TextMessage.Strict(m) =>
          log.info(s"接收到ws消息，类型TextMessage.Strict，msg-${m}")
          UserActor.WebSocketMsg(m)

        case BinaryMessage.Strict(m) =>
//          log.info(s"接收到ws消息，类型Binary $r")
          val buffer = new MiddleBufferInJvm(m.asByteBuffer)
          bytesDecode[WsMsgClient](buffer) match {
            case Right(req) =>
              UserActor.WebSocketMsg(Some(req))
            case Left(e) =>
              log.info(s"websocket decode error:$e")
              UserActor.WebSocketMsg(None)
          }

        case x =>
          log.info(s"$userActor recv a unsupported msg from websocket:$x")
          UserActor.WebSocketMsg(None)

      }
      .via(UserActor.flow(userActor))
      .map{
        case t: Wrap =>
          BinaryMessage.Strict(ByteString(t.ws))
        case x =>
          log.debug(s"websocket send an unknown msg:$x")
          TextMessage.apply("")

      }
      .withAttributes(ActorAttributes.supervisionStrategy(decider = decider))
  }


  private val decider:Supervision.Decider = {
    e:Throwable =>
      e.printStackTrace()
      Supervision.Resume
  }


}
