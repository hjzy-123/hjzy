package com.sk.hjzy.roomManager.core

import akka.actor
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.protocol.ptcl.CommonInfo
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{GetLiveInfoRsp, JoinMeetingRsp, NewMeetingRsp}
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{GetUserInfoList, JoinRoom, NewRoom}
import org.seekloud.byteobject.MiddleBufferInJvm
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.{HostCloseRoom, _}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.common.Common.Role
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.Boot.{executor, userManager}
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import com.sk.hjzy.roomManager.utils.{DistributorClient, ProcessorClient, RtpClient}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import org.seekloud.byteobject.ByteObject._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}


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
        val liveInfoMap = mutable.HashMap.empty[Long, LiveInfo]
        val wholeRoomInfo = WholeRoomInfo(RoomInfo(roomId,"","",-1,"","",""))
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap)
      }
    }
  }

  private def idle(
                  roomId : Long,
                  subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]],
                  wholeRoomInfo: WholeRoomInfo,
                  liveInfoMap: mutable.HashMap[Long, LiveInfo],
                  userInfoListOpt: Option[List[PartUserInfo]] = None,
                  )
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm,
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewRoom(userId, roomId, roomName: String, roomDes: String, password: String, replyTo: ActorRef[NewMeetingRsp]) =>
          UserInfoDao.searchById(userId).map{ userTableOpt =>
            if(userTableOpt.nonEmpty){
              val partRoomInfo = RoomInfo(roomId, roomName, roomDes, userTableOpt.get.uid, userTableOpt.get.userName,
                UserInfoDao.getHeadImg(userTableOpt.get.headImg),
                UserInfoDao.getHeadImg(userTableOpt.get.coverImg),Some(password), None)
              replyTo ! NewMeetingRsp(Some(partRoomInfo))
              ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo.copy(roomInfo = partRoomInfo), liveInfoMap))
            }else{
              replyTo ! NewMeetingRsp(None, 100020, "此用户不存在")
              ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo,liveInfoMap))
            }
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case JoinRoom(roomId: Long, password: String,replyTo: ActorRef[JoinMeetingRsp]) =>
          if(wholeRoomInfo.roomInfo.password.nonEmpty){
            if(wholeRoomInfo.roomInfo.password.get == password)
              replyTo ! JoinMeetingRsp(Some(wholeRoomInfo.roomInfo))
            else
              replyTo ! JoinMeetingRsp(None,100020,s"加入会议室请求失败:密码错误")
          }else{
            replyTo ! JoinMeetingRsp(None,100020,s"加入会议室请求失败:会议室不存在")
          }
          Behaviors.same

        case GetUserInfoList(roomId, userId) =>
          if(userInfoListOpt.nonEmpty) {
            dispatchTo(subscribers)(List((userId)),UserInfoListRsp(Some(userInfoListOpt.get.filter(_.userId != userId))))
            val oldUserList = subscribers.filter(r => r._1 != userId).keys.toList
            dispatchTo(subscribers)(oldUserList,UserInfoListRsp(Some(List(userInfoListOpt.get.last))))
            dispatchTo(subscribers)(oldUserList,RcvComment(-1l, "", s"${userInfoListOpt.get.filter(_.userId == userId).head.userName}加入房间"))

            val liveIdList = liveInfoMap.map(r => (r._1, r._2.liveId)).toList.filter(_._1 != userId)
            if(wholeRoomInfo.isStart == 1){
              if(liveInfoMap.get(userId).nonEmpty){
                dispatchTo(subscribers)(List((userId)),StartMeetingRsp(Some(liveInfoMap(userId)), liveIdList))
                dispatchTo(subscribers)(oldUserList,GetLiveId4Other(userId, liveInfoMap(userId).liveId))
              }else
                dispatchTo(subscribers)(List((userId)),StartMeetingRsp(None, liveIdList, 100002,"无liveInfo"))
            }

          } else
            dispatch(subscribers)(UserInfoListRsp(None,100008, "此房间没有用户"))
          Behaviors.same

        case ActorProtocol.UpdateSubscriber(join, roomId, userId,userActorOpt) =>
          if (join == Common.Subscriber.join) {
            log.info(s"${ctx.self.path}新用户加入房间roomId=$roomId,userId=$userId")
            subscribers.put(userId, userActorOpt.get)
            for{
              userTableOpt <- UserInfoDao.searchById(userId)
              liveInfo <- RtpClient.getLiveInfoFunc()
            } yield {
              if(userTableOpt.nonEmpty){
                val partUserInfo = PartUserInfo(userTableOpt.get.uid, userTableOpt.get.userName,UserInfoDao.getHeadImg(userTableOpt.get.headImg))
                liveInfo match {
                  case Right(rsp) =>
                    log.info(s"获取新的liveInfo$liveInfo")
                    liveInfoMap.put(userId, rsp.liveInfo)
                    if(userInfoListOpt.nonEmpty)
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, Some(userInfoListOpt.get :+ partUserInfo)))
                    else
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, Some(List(partUserInfo))))
                  case Left(value) =>
                    if(userInfoListOpt.nonEmpty)
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, Some(userInfoListOpt.get :+ partUserInfo)))
                    else
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, Some(List(partUserInfo))))
                }
              }else{
                ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap))
              }
            }
          }else if(join == Common.Subscriber.left){
            log.info(s"${ctx.self.path}新用户离开房间roomId=$roomId,userId=$userId")
            val otherUserList = subscribers.filter(r => r._1 != userId).keys.toList
            dispatchTo(subscribers)(otherUserList,LeftUserRsp(userId))
            dispatchTo(subscribers)(otherUserList,RcvComment(-1l, "", s"${userInfoListOpt.get.filter(_.userId == userId).head.userName}离开房间"))
            subscribers.remove(userId)
            liveInfoMap.remove(userId)
            ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap,Some(userInfoListOpt.get.filter(_.userId != userId))))
          }

          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case TestRoom(roomInfo) =>
          //仅用户测试使用空房间
//          idle(WholeRoomInfo(roomInfo, mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]]()),subscribers,  System.currentTimeMillis(), 0)
          Behaviors.same

        case ActorProtocol.HostLeaveRoom(roomId) =>
          log.info(s"${ctx.self.path} host leave room")
          subscribers.remove(wholeRoomInfo.roomInfo.userId)
          liveInfoMap.remove(wholeRoomInfo.roomInfo.userId)
          if(userInfoListOpt.get.exists(_.userId != wholeRoomInfo.roomInfo.userId) && userInfoListOpt.nonEmpty){
            val newHost = userInfoListOpt.get.filter(_.userId != wholeRoomInfo.roomInfo.userId).head
            dispatch(subscribers)(ChangeHost2Client(newHost.userId, newHost.userName))
            dispatchTo(subscribers)(subscribers.filter(r => r._1 != newHost.userId).keys.toList,RcvComment(-1, "", s"主持人${wholeRoomInfo.roomInfo.userName}离开会议室，${newHost.userName}被指派为新的主持人"))
            dispatchTo(subscribers)(List(newHost.userId),RcvComment(-1, "", s"主持人${wholeRoomInfo.roomInfo.userName}离开会议室，您被指派为新的主持人"))
            userManager ! ActorProtocol.ChangeBehaviorToHost(newHost.userId, newHost.userId)
            val newRoomInfo = WholeRoomInfo(wholeRoomInfo.roomInfo.copy(userId = newHost.userId, userName = newHost.userName, headImgUrl = newHost.headImgUrl))
            idle(roomId, subscribers,newRoomInfo, liveInfoMap, Some(userInfoListOpt.get.filter(_.userId != wholeRoomInfo.roomInfo.userId)))
          } else {
            log.info("主持人离开，房间内无人委派，房间废弃")
            idle(roomId, subscribers,WholeRoomInfo(wholeRoomInfo.roomInfo.copy(userId = -1, userName = "", headImgUrl = "")), liveInfoMap)
          }
        //todo  主持人离开房间时没有人时， 相当于结束会议，要删除房间信息，及对应的Actor


        case ActorProtocol.WebSocketMsgWithActor(userId, roomId, wsMsg) =>
          handleWebSocketMsg(WholeRoomInfo(wholeRoomInfo.roomInfo), subscribers,liveInfoMap,userInfoListOpt, dispatch(subscribers), dispatchTo(subscribers))(ctx, userId, roomId, wsMsg)

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$x in idle state...")
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
    **/
    //todo webSocket消息
  private def handleWebSocketMsg(
    wholeRoomInfo: WholeRoomInfo,
    subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]], //包括主播在内的所有用户
    liveInfoMap: mutable.HashMap[Long, LiveInfo],
    userInfoListOpt: Option[List[PartUserInfo]] = None,
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

      case ModifyRoomInfo(roomName, roomDes) =>
        val roomInfo = if (roomName.nonEmpty && roomDes.nonEmpty) {
          wholeRoomInfo.roomInfo.copy(roomName = roomName.get, roomDes = roomDes.get)
        } else if (roomName.nonEmpty) {
          wholeRoomInfo.roomInfo.copy(roomName = roomName.get)
        } else if (roomDes.nonEmpty) {
          wholeRoomInfo.roomInfo.copy(roomDes = roomDes.get)
        } else {
          wholeRoomInfo.roomInfo
        }
        val info = WholeRoomInfo(roomInfo)
        log.info(s"${ctx.self.path} modify the room info$wholeRoomInfo")
        dispatch(UpdateRoomInfo2Client(roomInfo.roomName, roomInfo.roomDes))
        dispatchTo(List(wholeRoomInfo.roomInfo.userId), ModifyRoomRsp())
        idle(roomId,subscribers,info, liveInfoMap, userInfoListOpt)


      case Comment(`userId`, `roomId`, comment, color, extension) =>
        log.info(s"收到留言$comment")
        UserInfoDao.searchById(userId).onComplete {
          case Success(value) =>
            value match {
              case Some(v) =>
                dispatch(RcvComment(userId, v.userName, comment, color, extension))
              case None =>
                log.info(s"${ctx.self.path.name} the database doesn't have the user")
            }
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo ,liveInfoMap,userInfoListOpt))
          case Failure(e) =>
            log.info(s"s${ctx.self.path.name} the search by userId error:$e")
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, userInfoListOpt))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case ChangeHost(newHost)  =>
        log.info(s"${ctx.self.path} 指派新的主持人$newHost")
        val oldHost = wholeRoomInfo.roomInfo.userId
        UserInfoDao.searchById(newHost).onComplete {
          case Success(value) =>
            value match {
              case Some(v) =>
                val roomInfo = wholeRoomInfo.roomInfo.copy(userId = newHost ,userName = v.userName, headImgUrl = v.headImg, coverImgUrl = v.coverImg)
                val info = WholeRoomInfo(roomInfo)
                userManager ! ActorProtocol.ChangeBehaviorToParticipant(oldHost, newHost)
                userManager ! ActorProtocol.ChangeBehaviorToHost(newHost, newHost)
                dispatchTo(subscribers.filter(r => r._1 != oldHost).keys.toList, ChangeHost2Client(newHost, v.userName))
                dispatchTo(List(oldHost), ChangeHostRsp(newHost, v.userName))
                dispatch(RcvComment(-1, "", s"${v.userName}被指派为新的主持人"))
                ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,info, liveInfoMap, userInfoListOpt))
              case None =>
                dispatchTo(List(oldHost), ChangeHostRsp(newHost, "", 10002 ,"此用户不存在"))
                log.info(s"${ctx.self.path.name} the database doesn't have the user")
                ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo ,liveInfoMap,userInfoListOpt))
            }
          case Failure(e) =>
            dispatchTo(List(oldHost), ChangeHostRsp(newHost, "", 10002 ,"查询失败"))
            log.info(s"s${ctx.self.path.name} the search by userId error:$e")
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, userInfoListOpt))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case StartMeetingReq(`userId`,token) =>
        log.info(s"${ctx.self.path} 开始会议，roomId=$roomId")
        val userIdList = subscribers.keys.toList
        var liveInfo4mix = LiveInfo("","")
        val liveIdList = liveInfoMap.map(r => (r._1, r._2.liveId)).toList.filter(_._1 != userId)
        userIdList.foreach{ id =>
          if(liveInfoMap.get(id).nonEmpty)
            dispatchTo(List(id), StartMeetingRsp(Some(liveInfoMap(id)), liveIdList))
          else
            dispatchTo(List(id), StartMeetingRsp(None, liveIdList, 100002, "无liveInfo"))
        }
        dispatch(RcvComment(-1, "", s"会议开始了~"))

//        RtpClient.getLiveInfoFunc().map {
//          case Right(GetLiveInfoRsp(liveInfo4Mix, 0, _)) =>
//            liveInfo4mix = liveInfo4Mix
//          case _ =>
//        }
//        ProcessorClient.newConnect(roomId, liveInfoMap.values.toList, liveInfo4mix.liveId, liveInfo4mix.liveCode)
        idle(roomId,subscribers,wholeRoomInfo.copy(isStart = 1), liveInfoMap, userInfoListOpt)

      case GetLiveInfoReq(userId) =>
        RtpClient.getLiveInfoFunc().map {
          case Right(rsp) =>
            liveInfoMap.put(userId, rsp.liveInfo)
            dispatchTo(subscribers.filter(_._1 != userId).keys.toList, GetLiveId4Other(userId, rsp.liveInfo.liveId ))
            dispatchTo(List(userId), WsProtocol.GetLiveInfoRsp(Some(rsp.liveInfo)))
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, userInfoListOpt))
          case _ =>
            dispatchTo(List(userId), WsProtocol.GetLiveInfoRsp(None, 100002, "无LiveInfo"))
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, userInfoListOpt))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case PingPackage =>
        Behaviors.same

      case x =>
        log.info(s"${ctx.self.path} recv an unknown msg:$x")
        Behaviors.same
    }
  }

  private def dispatch(subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]])(msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.info(s"${subscribers}分发消息：$msg")
    subscribers.values.foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[WsProtocol.HostCloseRoom]))
  }

  /**
    * subscribers:所有的订阅者
    * targetUserIdList：要发送的目标用户
    * msg：发送的消息
    **/
  private def dispatchTo(subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]])(targetUserIdList: List[Long], msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.info(s"${subscribers}定向分发消息：$msg")
    targetUserIdList.foreach { k =>
      subscribers.get(k).foreach(r => r ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[WsProtocol.HostCloseRoom]))
    }
  }


}
