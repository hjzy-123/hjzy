package com.sk.hjzy.roomManager.core

import akka.actor
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.byteobject.MiddleBufferInJvm
import com.sk.hjzy.protocol.ptcl.CommonInfo._
import com.sk.hjzy.protocol.ptcl.client2Manager.http.CommonProtocol.{GetLiveInfoRsp, GetLiveInfoRsp4RM}
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol.{HostCloseRoom, _}
import com.sk.hjzy.roomManager.Boot.{executor, roomManager}
import com.sk.hjzy.roomManager.common.AppSettings.{distributorIp, distributorPort}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.common.Common.{Like, Role}
import com.sk.hjzy.roomManager.core.RoomManager.GetRtmpLiveInfo
import com.sk.hjzy.roomManager.models.dao.{RecordDao, StatisticDao, UserInfoDao}
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.ActorProtocol.BanOnAnchor
import com.sk.hjzy.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import com.sk.hjzy.roomManager.utils.{DistributorClient, ProcessorClient, RtpClient}
import org.slf4j.LoggerFactory
import com.sk.hjzy.roomManager.utils.SecureUtil.nonceStr
import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}
import com.sk.hjzy.roomManager.core.UserActor
import org.seekloud.byteobject.ByteObject._

object RoomActor {

  import scala.language.implicitConversions

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

  private final val InitTime = Some(5.minutes)

  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
      log.debug(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(1024)  //8192
        //todo subscribers的参数是什么？
        val subscribers = mutable.HashMap.empty[(Long, Boolean), ActorRef[UserActor.Command]]
        init(roomId, subscribers)
      }
    }
  }

  private def init(
    roomId: Long,
    subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]],
    roomInfoOpt: Option[RoomInfo] = None
  )
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case ActorProtocol.StartRoom4Anchor(userId, `roomId`, actor) =>
          log.debug(s"${ctx.self.path} 用户id=$userId 开启了的新的直播间id=$roomId")
          subscribers.put((userId, false), actor)
          for {
            data <- RtpClient.getLiveInfoFunc()
            userTableOpt <- UserInfoDao.searchById(userId)
          } yield {
            data match {
              case Right(rsp) =>
                if (userTableOpt.nonEmpty) {

                  val roomInfo = RoomInfo(roomId, s"${userTableOpt.get.userName}的直播间", "", userTableOpt.get.uid, userTableOpt.get.userName,
                    UserInfoDao.getHeadImg(userTableOpt.get.headImg),
                    UserInfoDao.getHeadImg(userTableOpt.get.coverImg), 0, 0, None,
                    Some(rsp.liveInfo.liveId)
                  )

                  //todo 改为ProcessorClient(开始会议后通知Processor开始录像)
                  DistributorClient.startPull(roomId, rsp.liveInfo.liveId).map {
                    case Right(r) =>
                      log.info(s"distributor startPull succeed, get live address: ${r.liveAdd}")
                      dispatchTo(subscribers)(List((userId, false)), StartLiveRsp(Some(rsp.liveInfo)))
                      roomInfo.mpd = Some(r.liveAdd)
                      val startTime = r.startTime
                        ctx.self ! SwitchBehavior("idle", idle(WholeRoomInfo(roomInfo), mutable.HashMap(Role.host -> mutable.HashMap(userId -> rsp.liveInfo)), subscribers, mutable.Set[Long](), startTime, 0))

                    case Left(e) =>
                      log.error(s"distributor startPull error: $e")
                      dispatchTo(subscribers)(List((userId, false)), StartLiveRefused4LiveInfoError)
                      ctx.self ! SwitchBehavior("init", init(roomId, subscribers))
                  }

                } else {
                  log.debug(s"${ctx.self.path} 开始直播被拒绝，数据库中没有该用户的数据，userId=$userId")
                  dispatchTo(subscribers)(List((userId, false)), StartLiveRefused)
                  ctx.self ! SwitchBehavior("init", init(roomId, subscribers))
                }
              case Left(error) =>
                log.debug(s"${ctx.self.path} 开始直播被拒绝，请求rtp server解析失败，error:$error")
                dispatchTo(subscribers)(List((userId, false)), StartLiveRefused)
                ctx.self ! SwitchBehavior("init", init(roomId, subscribers))
            }
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case GetRoomInfo(replyTo) =>
          if (roomInfoOpt.nonEmpty) {
            replyTo ! roomInfoOpt.get
          }else {
            log.debug("房间信息未更新")
            replyTo ! RoomInfo(-1, "", "", -1l, "", "", "", -1, -1)
          }
          Behaviors.same

        case TestRoom(roomInfo) =>
          //仅用户测试使用空房间
          idle(WholeRoomInfo(roomInfo), mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]](), subscribers, mutable.Set[Long](), System.currentTimeMillis(), 0)

        case ActorProtocol.AddUserActor4Test(userId, roomId, userActor) =>
          subscribers.put((userId, false), userActor)
          Behaviors.same

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$x in init state...")
          Behaviors.same
      }
    }
  }

  private def idle(
    wholeRoomInfo: WholeRoomInfo, //可以考虑是否将主路的liveinfo加在这里，单独存一份连线者的liveinfo列表
    liveInfoMap: mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]],
    subscribe: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]], //需要区分订阅的用户的身份，注册用户还是临时用户(uid,是否是临时用户true:是)
    liker: mutable.Set[Long],
    startTime: Long,
    totalView: Int,
    isJoinOpen: Boolean = false,
  )
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case ActorProtocol.AddUserActor4Test(userId, roomId, userActor) =>
          subscribe.put((userId, false), userActor)
          Behaviors.same

        case ActorProtocol.WebSocketMsgWithActor(userId, roomId, wsMsg) =>
          handleWebSocketMsg(wholeRoomInfo, subscribe, liveInfoMap, liker, startTime, totalView, isJoinOpen, dispatch(subscribe), dispatchTo(subscribe))(ctx, userId, roomId, wsMsg)

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
    subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]], //包括主播在内的所有用户
    liveInfoMap: mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]], //"audience"/"anchor"->Map(userId->LiveInfo)
    liker: mutable.Set[Long],
    startTime: Long,
    totalView: Int,
    isJoinOpen: Boolean = false,
    dispatch: WsMsgRm => Unit,
    dispatchTo: (List[(Long, Boolean)], WsMsgRm) => Unit
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

  private def dispatch(subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]])(msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.debug(s"${subscribers}分发消息：$msg")
    subscribers.values.foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
  }

  /**
    * subscribers:所有的订阅者
    * targetUserIdList：要发送的目标用户
    * msg：发送的消息
    **/
  private def dispatchTo(subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]])(targetUserIdList: List[(Long, Boolean)], msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.debug(s"${subscribers}定向分发消息：$msg")
    targetUserIdList.foreach { k =>
      subscribers.get(k).foreach(r => r ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
    }
  }


}
