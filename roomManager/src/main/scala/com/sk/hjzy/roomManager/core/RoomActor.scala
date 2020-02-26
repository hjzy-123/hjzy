package com.sk.hjzy.roomManager.core

import akka.actor
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.protocol.ptcl.CommonInfo
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{GetLiveInfoRsp, JoinMeetingRsp, NewMeetingRsp}
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{GetUserInfoList, JoinRoom, NewRoom, Stop}
import org.seekloud.byteobject.MiddleBufferInJvm
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.{HostCloseRoom, _}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.Boot.{executor, roomManager, userManager}
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import com.sk.hjzy.roomManager.utils.{DistributorClient, EmailUtil, ProcessorClient, RtpClient}
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

  private final case object Timer4Stop

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

  final case class sendInviteEmail(invitees: List[String], roomInfo: RoomInfo) extends Command

  case class PartRoomInfo(roomId: Long, roomName: String, roomDes: String)

  private final val InitTime = Some(5.minutes)

  private var SpeakApplyMap = Map[Long, String]()

  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      log.debug(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(1024)  //8192
        val subscribers = mutable.HashMap.empty[Long, ActorRef[UserActor.Command]]
        val liveInfoMap = mutable.HashMap.empty[Long, LiveInfo]
        val wholeRoomInfo = WholeRoomInfo(RoomInfo(roomId,"","",-1,"","",""))
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, -1)
      }
    }
  }

  private def idle(
                  roomId : Long,
                  subscribers: mutable.HashMap[Long, ActorRef[UserActor.Command]],
                  wholeRoomInfo: WholeRoomInfo,
                  liveInfoMap: mutable.HashMap[Long, LiveInfo],
                  startTime: Long,
                  userInfoListOpt: Option[List[PartUserInfo]] = None,
                  )
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm,
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewRoom(userId, roomId, roomName: String, roomDes: String, password: String, invitees, replyTo: ActorRef[NewMeetingRsp]) =>
          UserInfoDao.searchById(userId).map{ userTableOpt =>
            if(userTableOpt.nonEmpty){
              val partRoomInfo = RoomInfo(roomId, roomName, roomDes, userTableOpt.get.uid, userTableOpt.get.userName,
                UserInfoDao.getHeadImg(userTableOpt.get.headImg),
                UserInfoDao.getHeadImg(userTableOpt.get.coverImg),Some(password), None)

              for{
                inviteError <- UserInfoDao.searchNameNonexist(invitees)
              }yield {
                if(inviteError.nonEmpty)
                  replyTo ! NewMeetingRsp(Some(partRoomInfo), 100021, s"邀请${inviteError.mkString(",")}邮件发送失败，请检查用户名后重新邀请。")
                else {
                  replyTo ! NewMeetingRsp(Some(partRoomInfo))
                }

                ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo.copy(roomInfo = partRoomInfo), liveInfoMap, startTime))
                ctx.self ! sendInviteEmail(invitees, partRoomInfo)
              }
            }else{
              replyTo ! NewMeetingRsp(None, 100020, "此用户不存在")
              ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo,liveInfoMap, startTime))
            }
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case sendInviteEmail(invitees, roomInfo) =>
          for{
            inviteEmail <- UserInfoDao.searchEmailByNameList(invitees)
          }yield {
            if(inviteEmail.nonEmpty) {
              Future{
                EmailUtil.send("您收到以下会议邀请，请及时参与会议~", s"房间号：${roomInfo.roomId};<br> 密码：${roomInfo.password.get};" +
                  s"<br> 会议名称：${roomInfo.roomName}; <br>会议描述: ${roomInfo.roomDes}", inviteEmail.toList)
              }.onComplete{
                case Success(value) =>
                  log.info(s"send---email-----suceess")
                case Failure(exception) =>
                  log.info(s"send---email-----fail")
              }
            }
          }
          Behaviors.same

        case JoinRoom(roomId: Long, password: String,replyTo: ActorRef[JoinMeetingRsp]) =>
          if(wholeRoomInfo.roomInfo.password.nonEmpty){
            if(wholeRoomInfo.roomInfo.password.get == password)
              replyTo ! JoinMeetingRsp(Some(wholeRoomInfo.roomInfo))
            else
              replyTo ! JoinMeetingRsp(None,100020,s"加入会议室请求失败:密码错误")
          }else{
            log.info("加入会议室失败")
            replyTo ! JoinMeetingRsp(None,100020,s"加入会议室请求失败:会议室不存在")
          }
          Behaviors.same

        case GetUserInfoList(roomId, userId) =>
          if(userInfoListOpt.nonEmpty) {
            dispatchTo(subscribers)(List(userId),UserInfoListRsp(Some(userInfoListOpt.get.filter(_.userId != userId))))
            val oldUserList = subscribers.filter(r => r._1 != userId).keys.toList
            dispatchTo(subscribers)(oldUserList,UserInfoListRsp(Some(List(userInfoListOpt.get.last))))
            dispatchTo(subscribers)(oldUserList,RcvComment(-1l, "", s"${userInfoListOpt.get.filter(_.userId == userId).head.userName}加入房间"))

            val liveIdList = liveInfoMap.map(r => (r._1, r._2.liveId)).toList.filter(_._1 != userId)
            if(wholeRoomInfo.isStart == 1){
              log.info(s"会议已经开始，给新进来的用户直接发送liveId---------${wholeRoomInfo.isStart}-------------")
              if(liveInfoMap.get(userId).nonEmpty){
                ProcessorClient.updateRoomInfo(roomId, List((liveInfoMap(userId).liveId, 1)), liveInfoMap.keys.toList.length, wholeRoomInfo.speaker._2)
                dispatchTo(subscribers)(List(userId),StartMeetingRsp(Some(liveInfoMap(userId)), liveIdList))
                dispatchTo(subscribers)(oldUserList,GetLiveId4Other(userId, liveInfoMap(userId).liveId))
              }else
                dispatchTo(subscribers)(List(userId),StartMeetingRsp(None, liveIdList, 100002,"无liveInfo"))

              if(wholeRoomInfo.speaker._1 != -1){
                log.info(s"有人正在发言，给新来的用户发言者信息---------${wholeRoomInfo.speaker._2}-------------")
                dispatchTo(subscribers)(List(userId), CloseSoundFrame2Client(-1))
                dispatchTo(subscribers)(List(userId), SpeakingUser(wholeRoomInfo.speaker._1, wholeRoomInfo.speaker._2))
              }
            }

          } else
            dispatch(subscribers)(UserInfoListRsp(None,100008, "此房间没有用户"))
          Behaviors.same

          //todo processor update
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
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap,startTime, Some(userInfoListOpt.get :+ partUserInfo)))
                    else
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap,startTime, Some(List(partUserInfo))))
                  case Left(value) =>
                    if(userInfoListOpt.nonEmpty)
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, startTime, Some(userInfoListOpt.get :+ partUserInfo)))
                    else
                      ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, startTime, Some(List(partUserInfo))))
                }
              }else{
                ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap, startTime))
              }
            }
          }else if(join == Common.Subscriber.left){
            log.info(s"${ctx.self.path}新用户离开房间roomId=$roomId,userId=$userId")
            val otherUserList = subscribers.filter(r => r._1 != userId).keys.toList
            dispatchTo(subscribers)(otherUserList,LeftUserRsp(userId))
            dispatchTo(subscribers)(otherUserList,RcvComment(-1l, "", s"${userInfoListOpt.get.filter(_.userId == userId).head.userName}离开房间"))
            subscribers.remove(userId)
            liveInfoMap.remove(userId)
            if(userId == wholeRoomInfo.speaker._1) {
              dispatchTo(subscribers)(otherUserList,StopSpeakingUser(userId, userInfoListOpt.get.filter(_.userId == userId).head.userName))
              val speakerNew = liveInfoMap(wholeRoomInfo.roomInfo.userId).liveId
              ProcessorClient.updateRoomInfo(roomId, List((liveInfoMap(userId).liveId, -1)), liveInfoMap.keys.toList.length, speakerNew)
              ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo.copy(speaker = (wholeRoomInfo.roomInfo.userId, speakerNew)), liveInfoMap,startTime, Some(userInfoListOpt.get.filter(_.userId != userId))))
            }else{
              ProcessorClient.updateRoomInfo(roomId, List((liveInfoMap(userId).liveId, -1)), liveInfoMap.keys.toList.length, wholeRoomInfo.speaker._2)
              ctx.self ! SwitchBehavior("idle", idle(roomId, subscribers,wholeRoomInfo, liveInfoMap,startTime, Some(userInfoListOpt.get.filter(_.userId != userId))))
            }
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case TestRoom(roomInfo) =>
          //仅用户测试使用空房间
//          idle(WholeRoomInfo(roomInfo, mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]]()),subscribers,  System.currentTimeMillis(), 0)
          Behaviors.same

        //todo processor update
        case ActorProtocol.HostLeaveRoom(roomId) =>
          log.info(s"${ctx.self.path} host leave room")
          val hostLiveId = liveInfoMap(wholeRoomInfo.roomInfo.userId).liveId
          subscribers.remove(wholeRoomInfo.roomInfo.userId)
          liveInfoMap.remove(wholeRoomInfo.roomInfo.userId)
          if(userInfoListOpt.get.exists(_.userId != wholeRoomInfo.roomInfo.userId) && userInfoListOpt.nonEmpty){
            val newHost = userInfoListOpt.get.filter(_.userId != wholeRoomInfo.roomInfo.userId).head

            //todo  要把申请表储存下来吗？
            dispatch(subscribers)(ChangeHost2Client(newHost.userId, newHost.userName, SpeakApplyMap))
            dispatchTo(subscribers)(subscribers.filter(r => r._1 != newHost.userId).keys.toList,RcvComment(-1, "", s"主持人${wholeRoomInfo.roomInfo.userName}离开会议室，${newHost.userName}被指派为新的主持人"))
            dispatchTo(subscribers)(List(newHost.userId),RcvComment(-1, "", s"主持人${wholeRoomInfo.roomInfo.userName}离开会议室，您被指派为新的主持人"))
            userManager ! ActorProtocol.ChangeBehaviorToHost(newHost.userId, newHost.userId)
            val newRoomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(userId = newHost.userId, userName = newHost.userName, headImgUrl = newHost.headImgUrl))

            if(wholeRoomInfo.roomInfo.userId == wholeRoomInfo.speaker._1) {
              ProcessorClient.updateRoomInfo(roomId, List((hostLiveId, -1)), liveInfoMap.keys.toList.length, liveInfoMap(newHost.userId).liveId)
              idle(roomId, subscribers,newRoomInfo.copy(speaker = (newHost.userId,liveInfoMap(newHost.userId).liveId)), liveInfoMap, startTime, Some(userInfoListOpt.get.filter(_.userId != wholeRoomInfo.roomInfo.userId)))
            } else {
              ProcessorClient.updateRoomInfo(roomId, List((hostLiveId, -1)), liveInfoMap.keys.toList.length, wholeRoomInfo.speaker._2)
              idle(roomId, subscribers,newRoomInfo, liveInfoMap, startTime, Some(userInfoListOpt.get.filter(_.userId != wholeRoomInfo.roomInfo.userId)))
            }
          } else {
            log.info("主持人离开，房间内无人委派，房间废弃")
            if(wholeRoomInfo.isStart == 1) {
              ProcessorClient.closeRoom(roomId)
              if(userInfoListOpt.get.nonEmpty)
                roomManager ! RoomManager.DelaySeekRecord(wholeRoomInfo, roomId, startTime, userInfoListOpt.get)
            }
            timer.startSingleTimer(Timer4Stop, Stop(roomId), 1500.milli)
            idle(roomId, subscribers,WholeRoomInfo(wholeRoomInfo.roomInfo.copy(userId = -1, userName = "", headImgUrl = "")), liveInfoMap, startTime)
          }

        case Stop(roomId) =>
          log.info(s"${ctx.self}---$roomId stopped ------")
          Behaviors.stopped

        case ActorProtocol.WebSocketMsgWithActor(userId, roomId, wsMsg) =>
          handleWebSocketMsg(wholeRoomInfo, subscribers,liveInfoMap,startTime, userInfoListOpt, dispatch(subscribers), dispatchTo(subscribers))(ctx, userId, roomId, wsMsg)

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
    startTime :Long,
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
        idle(roomId,subscribers,info, liveInfoMap, startTime, userInfoListOpt)

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
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo ,liveInfoMap, startTime, userInfoListOpt))
          case Failure(e) =>
            log.info(s"s${ctx.self.path.name} the search by userId error:$e")
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case ChangeHost(newHost, audSpeakApplyMap)  =>
        log.info(s"${ctx.self.path} 指派新的主持人$newHost")
        val oldHost = wholeRoomInfo.roomInfo.userId
        SpeakApplyMap = audSpeakApplyMap
        UserInfoDao.searchById(newHost).onComplete {
          case Success(value) =>
            value match {
              case Some(v) =>
                val info = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(userId = newHost ,userName = v.userName, headImgUrl = v.headImg, coverImgUrl = v.coverImg))
                log.info("指派新的主持人时，新的房间信息如下————", info)
                userManager ! ActorProtocol.ChangeBehaviorToParticipant(oldHost, newHost)
                userManager ! ActorProtocol.ChangeBehaviorToHost(newHost, newHost)
                dispatchTo(subscribers.filter(r => r._1 != oldHost).keys.toList, ChangeHost2Client(newHost, v.userName, audSpeakApplyMap))
                dispatchTo(List(oldHost), ChangeHostRsp(newHost, v.userName))
                dispatch(RcvComment(-1, "", s"${v.userName}被指派为新的主持人"))
                ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,info, liveInfoMap, startTime, userInfoListOpt))
              case None =>
                dispatchTo(List(oldHost), ChangeHostRsp(newHost, "", 10002 ,"此用户不存在"))
                log.info(s"${ctx.self.path.name} the database doesn't have the user")
                ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo ,liveInfoMap,startTime, userInfoListOpt))
            }
          case Failure(e) =>
            dispatchTo(List(oldHost), ChangeHostRsp(newHost, "", 10002 ,"查询失败"))
            log.info(s"s${ctx.self.path.name} the search by userId error:$e")
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, startTime, userInfoListOpt))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case StartMeetingReq(`userId`,token) =>
        log.info(s"${ctx.self.path} 开始会议，roomId=$roomId")
        val userIdList = subscribers.keys.toList
        // 发言人默认为主持人
        val speaker = liveInfoMap(wholeRoomInfo.roomInfo.userId).liveId
        userIdList.foreach{ id =>
          val liveIdList = liveInfoMap.map(r => (r._1, r._2.liveId)).toList.filter(_._1 != id)
          if(liveInfoMap.get(id).nonEmpty)
            dispatchTo(List(id), StartMeetingRsp(Some(liveInfoMap(id)), liveIdList))
          else
            dispatchTo(List(id), StartMeetingRsp(None, liveIdList, 100002, "无liveInfo"))
        }
        dispatch(RcvComment(-1, "", s"会议开始了~"))
        //fixme 开始录像
        for {
          data <- RtpClient.getLiveInfoFunc()
        } yield {
          data match {
            case Right(rsp) =>
              ProcessorClient.newConnect(roomId, liveInfoMap.values.map(_.liveId).toList, liveInfoMap.values.toList.length,
                speaker, rsp.liveInfo.liveId, rsp.liveInfo.liveCode).map{
                case Right(r) =>
                  val startTimeNew = r.startTime
                  ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo.copy(isStart = 1),liveInfoMap, startTimeNew, userInfoListOpt))
                case Left(e) =>
                  ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, startTime, userInfoListOpt))
              }

            case Left(str) =>
              log.info(s"${ctx.self.path} 请求processor录像失败=$str")
              ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, startTime, userInfoListOpt))
          }
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

//        val newRoom = WholeRoomInfo(wholeRoomInfo.roomInfo, 1)
//        log.info(s"开始会议后新的房间$newRoom")
//        idle(roomId,subscribers,newRoom,liveInfoMap, startTime, userInfoListOpt)

      case GetLiveInfoReq(userId) =>
        RtpClient.getLiveInfoFunc().map {
          case Right(rsp) =>
            liveInfoMap.put(userId, rsp.liveInfo)
            dispatchTo(subscribers.filter(_._1 != userId).keys.toList, GetLiveId4Other(userId, rsp.liveInfo.liveId ))
            dispatchTo(List(userId), WsProtocol.GetLiveInfoRsp(Some(rsp.liveInfo)))
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, startTime, userInfoListOpt))
          case _ =>
            dispatchTo(List(userId), WsProtocol.GetLiveInfoRsp(None, 100002, "无LiveInfo"))
            ctx.self ! SwitchBehavior("idle", idle(roomId,subscribers,wholeRoomInfo,liveInfoMap, startTime, userInfoListOpt))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case PingPackage =>
        Behaviors.same

      case CloseSoundFrame(userId, sound, frame) =>
        log.info(s"${ctx.self.path} 主持人屏蔽$userId")
        dispatchTo(List(userId), CloseSoundFrame2Client(sound ,frame))
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt)

      case CloseOwnSoundFrame(userId, sound, frame) =>
        log.info(s"${ctx.self.path} $userId 关闭或打开自己的声音或图像")
        dispatch(ClientCloseSoundFrame( userId, sound, frame))
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt)

      case ForceOut(userId) =>
        log.info(s"${ctx.self.path} 强制$userId 退出会议")
        dispatchTo(List(userId), ForceOut2Client(userId))
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt)

      case ApplySpeak(userId) =>
        val userName = userInfoListOpt.get.filter(_.userId == userId).head.userName
        log.info(s"${ctx.self.path} $userName 请求发言")
        dispatch(RcvComment(-1,"",s"$userName 请求发言"))
        dispatchTo(List(wholeRoomInfo.roomInfo.userId), ApplySpeak2Host(userId, userName))
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt)

      //todo processor update
      case ApplySpeakAccept(userId, userName, accept) =>
        if(accept) {
          val speakerNew = liveInfoMap(userId).liveId
          ProcessorClient.updateRoomInfo(roomId, List[(String, Int)](), liveInfoMap.keys.toList.length, speakerNew)
          dispatch(RcvComment(-1,"",s"主持人${wholeRoomInfo.roomInfo.userName}同意了 $userName 的发言请求"))
          dispatchTo(List(userId), ApplySpeakRsp())
          dispatchTo(subscribers.filter( r => r._1 != userId && r._1 != wholeRoomInfo.roomInfo.userId).keys.toList, CloseSoundFrame2Client(-1))
          dispatch(SpeakingUser(userId, userName))
          idle(roomId,subscribers,wholeRoomInfo.copy(speaker = (userId, userName)), liveInfoMap, startTime, userInfoListOpt)
        } else {
          dispatch(RcvComment(-1,"",s"主持人${wholeRoomInfo.roomInfo.userName}拒绝了 $userName 的发言请求"))
          dispatchTo(List(userId), ApplySpeakRsp(100008, "主持人拒绝了您的发言请求"))
          idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt)
        }

      //todo processor update
      case AppointSpeak(userId) =>
        val speakerNew = liveInfoMap(userId).liveId
        ProcessorClient.updateRoomInfo(roomId, List[(String, Int)](), liveInfoMap.keys.toList.length, speakerNew)
        var userName = "参会者"
        if(userInfoListOpt.get.exists(p = _.userId == userId))
          userName = userInfoListOpt.get.filter(_.userId == userId).head.userName
        dispatchTo(subscribers.filter(r => r._1 != userId && r._1 != wholeRoomInfo.roomInfo.userId).keys.toList, RcvComment(-1,"",s"主持人指定 $userName 发言"))
        dispatchTo(List(userId), RcvComment(-1,"",s"主持人指定您发言"))
        dispatchTo(subscribers.filter( r => r._1 != userId && r._1 != wholeRoomInfo.roomInfo.userId).keys.toList, CloseSoundFrame2Client(-1))
        dispatch(SpeakingUser(userId, userName))
        idle(roomId,subscribers,wholeRoomInfo.copy(speaker = (userId, userName)), liveInfoMap, startTime, userInfoListOpt)

      case StopSpeak(userId) =>
        val userName = userInfoListOpt.get.filter(_.userId == userId).head.userName
        dispatchTo(subscribers.filter(r => r._1 != userId && r._1 != wholeRoomInfo.roomInfo.userId).keys.toList, RcvComment(-1,"",s" $userName 发言结束"))
        dispatchTo(List(userId), RcvComment(-1,"",s"主持人结束您的发言"))
        dispatchTo(subscribers.filter( r => r._1 != userId && r._1 != wholeRoomInfo.roomInfo.userId).keys.toList, CloseSoundFrame2Client(1))
        dispatch(StopSpeakingUser(userId, userName))
        idle(roomId,subscribers,wholeRoomInfo.copy(speaker = (-1,"")), liveInfoMap, startTime, userInfoListOpt)

      case StopMeetingReq(userId) =>
        ProcessorClient.closeRoom(roomId)
        if(userInfoListOpt.get.nonEmpty)
          roomManager ! RoomManager.DelaySeekRecord(wholeRoomInfo, roomId, startTime, userInfoListOpt.get)
        dispatch(RcvComment(-1,"","会议结束了~"))
        dispatch(StopMeetingRsp())
        timer.startSingleTimer(Timer4Stop, Stop(roomId), 1500.milli)
        idle(roomId,subscribers,wholeRoomInfo.copy(isStart = 0), liveInfoMap, startTime, userInfoListOpt)

      case InviteOthers(invitees) =>
        for{
          inviteError <- UserInfoDao.searchNameNonexist(List(invitees))
        }yield {
          if(inviteError.nonEmpty)
            dispatchTo(List(userId), InviteOthersRsp(1000021,s"邀请${inviteError.mkString(",")}邮件发送失败，请检查用户名后重新邀请。"))
          else
            dispatchTo(List(userId), InviteOthersRsp())
        }
        ctx.self ! sendInviteEmail(List(invitees), wholeRoomInfo.roomInfo)
        idle(roomId,subscribers,wholeRoomInfo, liveInfoMap, startTime, userInfoListOpt)

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
