package com.sk.hjzy.roomManager.core

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.seekloud.byteobject.MiddleBufferInJvm
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol._
import com.sk.hjzy.roomManager.Boot.{executor, roomManager}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{ChangeBehaviorToHost, ChangeBehaviorToParticipant}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps


/**
  * actor由UserManager创建
  * 切换主持人和参会者状态
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
  def create(userId: Long, hostId:Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      log.info(s"userActor-$userId is starting...")
      ctx.setReceiveTimeout(30.seconds, CompleteMsgClient)
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
        init(userId, hostId)
      }
    }
  }

  private def init(
                    userId:Long,
                    hostId: Long,
                    roomIdOpt:Option[Long] = None,
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
            roomManager ! ActorProtocol.GetUserInfoList(roomIdOpt.get, userId)
            if(userId == hostId)
              switchBehavior(ctx, "host", host(userId,clientActor,roomIdOpt.get, hostId))
            else
              switchBehavior(ctx, "participant", participant(userId,clientActor,roomIdOpt.get, hostId))

          case UserLogin(roomId,`userId`) =>
            //先发一个用户登陆，再切换到其他的状态
            roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.join,roomId,userId,Some(ctx.self))
            init(userId,hostId,Some(roomId))

          case TimeOut(m) =>
            log.info(s"${ctx.self.path} is time out when busy,msg=$m")
            Behaviors.stopped

          case unknown =>
            if(userId == Common.TestConfig.TEST_USER_ID){
              log.info(s"${ctx.self.path} 测试房间的房主actor，不处理其他类型的消息msg=$unknown")
            }else{
              log.info(s"${ctx.self.path} recv an unknown msg:$msg in init state...")
              stashBuffer.stash(unknown)
            }
            Behavior.same
        }
    }
  }

  //主持人，房间id
  private def host(
                      userId: Long,
                      clientActor:ActorRef[WsMsgRm],
                      roomId:Long,
                      hostId:Long
                    )
                    (
                      implicit stashBuffer: StashBuffer[Command],
                      timer:TimerScheduler[Command],
                      sendBuffer:MiddleBufferInJvm
                    ):Behavior[Command] =
    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {
        case SendHeartBeat =>
          log.info(s"${ctx.self.path} 发送心跳给userId=$userId,roomId=$roomId")
          ctx.scheduleOnce(10.seconds, clientActor, Wrap(HeatBeat(System.currentTimeMillis()).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
          Behaviors.same

        case DispatchMsg(message,closeRoom) =>
          clientActor ! message
          Behaviors.same

        case WebSocketMsg(reqOpt) =>
          if(reqOpt.contains(PingPackage)){
            if(timer.isTimerActive("HeartBeatKey_" + userId)) timer.cancel("HeartBeatKey_" + userId)
            ctx.self ! SendHeartBeat
            Behaviors.same
          }
          else{
            reqOpt match{
              case Some(req) =>
                UserInfoDao.searchById(userId).map{
                  case Some(v) =>
                    if(v.`sealed`){
                      log.info(s"${ctx.self.path} 该用户已经被封号，无法发送ws消息")
                      clientActor !Wrap(WsProtocol.AccountSealed.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                      ctx.self ! CompleteMsgClient
                      ctx.self ! SwitchBehavior("host",host(userId,clientActor,roomId, hostId))
                    }else{
                      req match {
                        case StartMeetingReq(`userId`,token,clientType) =>
                          roomManager ! ActorProtocol.StartMeeting(userId,roomId,ctx.self)
                          ctx.self ! SwitchBehavior("host",host(userId,clientActor,roomId,hostId))

                        case x =>
                          roomManager ! ActorProtocol.WebSocketMsgWithActor(userId,roomId,x)
                          ctx.self ! SwitchBehavior("host",host(userId,clientActor,roomId, hostId))

                      }
                    }
                  case None =>
                    log.debug(s"${ctx.self.path} 该用户不存在，无法开始会议")
                    clientActor !Wrap(WsProtocol.NoUser.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                    ctx.self ! CompleteMsgClient
                    ctx.self ! SwitchBehavior("host",host(userId,clientActor,roomId, hostId))
                }
                switchBehavior(ctx,"busy",busy(),BusyTime,TimeOut("busy"))
              case None =>
                log.debug(s"${ctx.self.path} there is no web socket msg in anchor state")
                Behaviors.same
            }
          }

        case CompleteMsgClient =>
          //主持人结束会议
          log.info(s"${ctx.self.path.name} 主持人结束会议，roomId=$roomId,userId=$userId")
          roomManager ! ActorProtocol.HostLeaveRoom(roomId)
          Behaviors.stopped

        case FailMsgClient(ex) =>
          log.info(s"${ctx.self.path} websocket消息错误，断开ws=${userId} error=$ex")
          roomManager ! ActorProtocol.HostLeaveRoom(roomId)
          Behaviors.stopped

        case ChangeBehaviorToInit =>
          log.debug(s"${ctx.self.path} 切换到init状态")
          init(userId,hostId )

        case ChangeBehaviorToParticipant(userId,newHostId) =>
          log.info(s"${ctx.self.path} 切换到participant状态")
          timer.cancelAll()
          timer.startPeriodicTimer("HeartBeatKey_" + userId, SendHeartBeat, 10.seconds)
          switchBehavior(ctx, "participant", participant(userId ,clientActor,roomId ,newHostId))


        case unknown =>
          log.debug(s"${ctx.self.path} recv an unknown msg:${msg} in anchor state...")
          stashBuffer.stash(unknown)
          Behavior.same
      }
    }

  //参会者
  private def participant(
                           userId: Long,
                           clientActor:ActorRef[WsMsgRm],
                           roomId:Long, //观众所在的房间id,
                           hostId:Long
                      )
                      (
                        implicit stashBuffer: StashBuffer[Command],
                        timer:TimerScheduler[Command],
                        sendBuffer:MiddleBufferInJvm
                      ):Behavior[Command] =
    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {
        case SendHeartBeat =>
          log.info(s"${ctx.self.path} 发送心跳给userId=$userId,roomId=$roomId")
          ctx.scheduleOnce(10.seconds, clientActor, Wrap(HeatBeat(System.currentTimeMillis()).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
          Behaviors.same

        case DispatchMsg(message,closeRoom) =>
          clientActor ! message
          if(closeRoom){
            Behaviors.stopped
          }else{
            Behaviors.same
          }

        case CompleteMsgClient =>
          //主播需要关闭房间，通知所有观众
          //观众需要清楚房间中对应的用户信息映射
          log.info(s"${ctx.self.path.name} complete msg")
          timer.cancelAll()
          roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.left,roomId,userId,Some(ctx.self))
          Behaviors.stopped

        case FailMsgClient(ex) =>
          log.info(s"${ctx.self.path} websocket消息错误，断开ws=${userId} error=$ex")
          roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.left,roomId,userId,Some(ctx.self))
          Behaviors.stopped

        case WebSocketMsg(reqOpt) =>
          if(reqOpt.contains(PingPackage)){
            if(timer.isTimerActive("HeartBeatKey_" + userId)) timer.cancel("HeartBeatKey_" + userId)
            ctx.self ! SendHeartBeat
            Behaviors.same
          }
          else{
            reqOpt match{
              case Some(req) =>
                  UserInfoDao.searchById(userId).map{
                    case Some(v) =>
                      if(v.`sealed`){
                        log.info(s"${ctx.self.path} 该用户已经被封号，无法发送ws消息")
                        clientActor !Wrap(WsProtocol.AccountSealed.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                        ctx.self ! SwitchBehavior("participant",participant(userId,clientActor,roomId, hostId))
                      }else{
                        req match{

                          case x =>
                            log.info(s"收到ws消息$req")
                            roomManager ! ActorProtocol.WebSocketMsgWithActor(userId,roomId,req)
                            ctx.self ! SwitchBehavior("participant",participant(userId,clientActor,roomId, hostId))
                        }
                      }
                    case None =>
                      log.debug(s"${ctx.self.path} 该用户不存在，无法参与会议")
                      clientActor !Wrap(WsProtocol.NoUser.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                      ctx.self ! CompleteMsgClient
                      ctx.self ! SwitchBehavior("participant",participant(userId,clientActor,roomId, hostId))
                  }
                  switchBehavior(ctx,"busy",busy(),BusyTime,TimeOut("busy"))

              case None =>
                log.info(s"${ctx.self.path} there is no web socket msg in anchor state")
                Behaviors.same
            }
          }

        case ChangeBehaviorToInit =>
          log.debug(s"${ctx.self.path} 切换到init状态")
          init(userId,hostId)

        case ChangeBehaviorToHost(userId,newHostId) =>
          log.info(s"${ctx.self.path} 切换到host状态")
          timer.cancelAll()
          timer.startPeriodicTimer("HeartBeatKey_" + userId, SendHeartBeat, 10.seconds)
          switchBehavior(ctx, "host", host(userId ,clientActor,roomId ,newHostId))

        case unknown =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$msg in audience state...")
          stashBuffer.stash(unknown)
          Behavior.same
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

    log.info(s"进入${userActor} 的flow函数")
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
