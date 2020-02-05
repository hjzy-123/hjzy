package com.sk.hjzy.roomManager.core

import akka.actor
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo}
import com.sk.hjzy.roomManager.protocol.ActorProtocol.NewRoom
import org.seekloud.byteobject.MiddleBufferInJvm
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol.{HostCloseRoom, _}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.common.Common.Role
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import com.sk.hjzy.roomManager.utils.{DistributorClient, RtpClient}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import org.seekloud.byteobject.ByteObject._

import scala.language.implicitConversions


/**
 * actor由RoomManager创建
 * 接收、处理并分发webSocket消息
 */
object RoomActor {


  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command with RoomManager.Command

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

  final case class TestRoom(roomInfo: RoomInfo) extends Command

  final case class GetRoomInfo(replyTo: ActorRef[RoomInfo]) extends Command //考虑后续房间的建立不依赖ws
  final case class UpdateRTMP(rtmp: String) extends Command

  case class PartRoomInfo(roomId: Long, roomName: String, roomDes: String)

  private final val InitTime = Some(5.minutes)

  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      log.debug(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(1024)  //8192
        //todo subscribers的参数是什么？
        val subscribers = mutable.HashMap.empty[Long, ActorRef[UserActor.Command]]
        init(roomId, subscribers)
      }
    }
  }

  private def init(
    roomId: Long,
    subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]],
    partRoomInfoOpt: Option[PartRoomInfo] = None
  )
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewRoom(roomId, roomName: String, roomDes: String, password: String) =>
          val partRoomInfo = PartRoomInfo(roomId, roomName, roomDes)
          switchBehavior(ctx, "init", init(roomId,subscribers, Some(partRoomInfo)))

        case ActorProtocol.UpdateSubscriber(join, roomId, userId,userActorOpt) =>
          if (join == Common.Subscriber.join) {
              log.debug(s"${ctx.self.path}新用户加入房间roomId=$roomId,userId=$userId")
              subscribers.put(userId, userActorOpt.get)
            }else if(join == Common.Subscriber.left)
            subscribers.remove(userId)

          Behaviors.same


        case TestRoom(roomInfo) =>
          //仅用户测试使用空房间
          idle(WholeRoomInfo(roomInfo, mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]]()),subscribers,  System.currentTimeMillis(), 0)

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$x in init state...")
          Behaviors.same
      }
    }
  }

  private def idle(
    wholeRoomInfo: WholeRoomInfo, //可以考虑是否将主路的liveinfo加在这里，单独存一份连线者的liveinfo列表
    subscribe: mutable.HashMap[Long, ActorRef[UserActor.Command]], //需要区分订阅的用户的身份，注册用户还是临时用户(uid,是否是临时用户true:是)
    startTime: Long,
    totalView: Int,
  )
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case ActorProtocol.WebSocketMsgWithActor(userId, roomId, wsMsg) =>
          handleWebSocketMsg(wholeRoomInfo, subscribe, startTime, totalView,  dispatch(subscribe), dispatchTo(subscribe))(ctx, userId, roomId, wsMsg)

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg $x")
          Behaviors.same
      }
    }
  }

  private def busy()
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }

  //websocket处理消息的函数
  /**
    * userActor --> roomManager --> roomActor --> userActor
    * roomActor
    * subscribers:map(userId,userActor)
    *
    *
    *
    **/
    //todo webSocket消息
  private def handleWebSocketMsg(
    wholeRoomInfo: WholeRoomInfo,
    subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]], //包括主播在内的所有用户
    startTime: Long,
    totalView: Int,
    dispatch: WsMsgRm => Unit,
    dispatchTo: (List[Long], WsMsgRm) => Unit
  )
    (ctx: ActorContext[Command], userId: Long, roomId: Long, msg: WsMsgClient)
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    msg match {

      case PingPackage =>
        Behaviors.same

      case x =>
        log.debug(s"${ctx.self.path} recv an unknown msg:$x")
        Behaviors.same
    }
  }

  private def dispatch(subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]])(msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.debug(s"${subscribers}分发消息：$msg")
    subscribers.values.foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
  }

  /**
    * subscribers:所有的订阅者
    * targetUserIdList：要发送的目标用户
    * msg：发送的消息
    **/
  private def dispatchTo(subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]])(targetUserIdList: List[Long], msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.debug(s"${subscribers}定向分发消息：$msg")
    targetUserIdList.foreach { k =>
      subscribers.get(k).foreach(r => r ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
    }
  }


}
