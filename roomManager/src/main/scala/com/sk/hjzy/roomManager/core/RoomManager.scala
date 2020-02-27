package com.sk.hjzy.roomManager.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{JoinMeetingRsp, NewMeetingRsp}
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.PartUserInfo
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.models.dao.{RecordDao, UserInfoDao}
import com.sk.hjzy.roomManager.core.RoomActor._
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{GetUserInfoList, JoinRoom, NewRoom, Stop}
import com.sk.hjzy.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import com.sk.hjzy.roomManager.utils.ProcessorClient
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}
import scala.concurrent.duration.{FiniteDuration, _}
import com.sk.hjzy.roomManager.Boot.executor

/**
 * 由Boot创建
 * 管理房间列表, （创建会议，加入会议鉴权）
 * 通知roomActor webSocket消息
 */
object RoomManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  case class DelaySeekRecord(wholeRoomInfo:WholeRoomInfo,  roomId:Long, startTime:Long , userInfoList: List[PartUserInfo]) extends Command

  case class OnSeekRecord(wholeRoomInfo:WholeRoomInfo,  roomId:Long, startTime:Long , userInfoList: List[PartUserInfo]) extends Command

  private final case object DelaySeekRecordKey

  private final case object Timer4Stop

  def create():Behavior[Command] = {
    Behaviors.setup[Command]{ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      log.info(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command]{implicit timer =>
        val roomInfo = RoomInfo(Common.TestConfig.TEST_ROOM_ID,"test_room","测试房间",Common.TestConfig.TEST_USER_ID, "tldq",
          UserInfoDao.getHeadImg(""), UserInfoDao.getCoverImg(""))
        log.debug(s"${ctx.self.path} ---===== ${roomInfo.rtmp}")
        getRoomActor(Common.TestConfig.TEST_ROOM_ID,ctx) ! TestRoom(roomInfo)

        idle()
      }
    }
  }

  private def idle()
                  (implicit stashBuffer: StashBuffer[Command],timer:TimerScheduler[Command]):Behavior[Command] = {

    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {

        case r@NewRoom(userId:Long, roomId, roomName: String, roomDes: String, password: String, invitees, replyTo: ActorRef[NewMeetingRsp]) =>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) => replyTo ! NewMeetingRsp(None, 100020, "此房间已存在，无法再次创建")
            case None =>
              val roomActor = getRoomActor(roomId, ctx)
              roomActor ! r
          }
          Behaviors.same

        case r@JoinRoom(roomId: Long, password: String,replyTo: ActorRef[JoinMeetingRsp]) =>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) =>actor ! r
            case None =>
              log.debug(s"${ctx.self.path}房间未建立")
              replyTo ! JoinMeetingRsp(None,100020,s"加入会议室请求失败:会议室不存在")
          }
          Behaviors.same

        case r@GetUserInfoList(roomId, userId) =>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) =>actor ! r
            case None =>
              log.debug(s"${ctx.self.path}房间未建立")
          }
          Behaviors.same

        case r@ActorProtocol.UpdateSubscriber(join,roomId,userId,userActor) =>
          getRoomActorOpt(roomId,ctx)match{
            case Some(actor) =>
              actor ! r
            case None =>log.debug(s"${ctx.self.path}更新用户信息失败，房间不存在，有可能该用户是主持人等待房间开启，房间id=$roomId,用户id=$userId")
          }
          Behaviors.same

//        case r@ActorProtocol.StartMeeting(userId,roomId,actor) =>
//          getRoomActor(roomId,ctx) ! r
//          Behaviors.same

//        case r@ActorProtocol.HostCloseRoom(roomId)=>
//          //主持人结束会议
//          getRoomActorOpt(roomId, ctx) match{
//            case Some(roomActor) => roomActor ! r
//            case None =>log.debug(s"${ctx.self.path}关闭房间失败，房间不存在，id=$roomId")
//          }
//          Behaviors.same

        case DelaySeekRecord(wholeRoomInfo, roomId, startTime, userInfoList) =>
          log.info("---- wait seconds to seek record ----")
          timer.startSingleTimer(DelaySeekRecordKey + roomId.toString + startTime, OnSeekRecord(wholeRoomInfo, roomId, startTime, userInfoList), 5.seconds)
          Behaviors.same

        //延时请求获取录像
        case OnSeekRecord(wholeRoomInfo, roomId, startTime, userInfoList) =>
          timer.cancel(DelaySeekRecordKey + roomId.toString + startTime)
          ProcessorClient.seekRecord(roomId,startTime).onComplete{
            case Success(v) =>
              v match{
                case Right(rsp) =>
                  log.info(s"${ctx.self.path}获取录像id${roomId}时长为duration=${rsp.duration}")

                  //todo  1  可能没变化，已经添加进去， 改到传参之前
                  var userNameList = ""
                  userInfoList.foreach{ u =>
                    if(userInfoList.last != u)
                      userNameList = userNameList + u.userName +  "@"
                    else
                      userNameList = userNameList + u.userName
                  }
                  log.info("可观看录像的用户名称", userNameList)
                  RecordDao.addRecord(wholeRoomInfo.roomInfo.roomId,
                    wholeRoomInfo.roomInfo.roomName,wholeRoomInfo.roomInfo.roomDes,startTime,
                    UserInfoDao.getVideoImg(wholeRoomInfo.roomInfo.coverImgUrl),0, 0, rsp.duration, userNameList)

                case Left(err) =>
                  log.info(s"${ctx.self.path} 查询录像文件信息失败,error:$err")
              }

            case Failure(error) =>
              log.info(s"${ctx.self.path} 查询录像文件失败,error:$error")
          }
          timer.startSingleTimer(Timer4Stop, Stop(roomId), 1500.milli)
          Behaviors.same

        case r@Stop(roomId) =>
          getRoomActorOpt(roomId, ctx) match{
            case Some(roomActor) => roomActor ! r
            case None =>log.debug(s"${ctx.self.path}关闭房间失败，房间不存在，id=$roomId")
          }
          Behaviors.same

        case r@ActorProtocol.HostLeaveRoom(roomId)=>
          //主持人webSocket断开，离开房间
          getRoomActorOpt(roomId, ctx) match{
            case Some(roomActor) => roomActor ! r
            case None =>log.debug(s"${ctx.self.path}关闭房间失败，房间不存在，id=$roomId")
          }
          Behaviors.same

        case r@ActorProtocol.WebSocketMsgWithActor(userId,roomId,req) =>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) => actor ! r
            case None => log.debug(s"${ctx.self.path}请求错误，该房间还不存在，房间id=$roomId，用户id=$userId")
          }
          Behaviors.same

        case ChildDead(name,childRef) =>
          log.debug(s"${ctx.self.path} the child = ${ctx.children}")
          Behaviors.same

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg")
          Behaviors.same
      }
    }
  }

  private def getRoomActor(roomId:Long, ctx: ActorContext[Command]) = {
    val childrenName = s"roomActor-${roomId}"
    ctx.child(childrenName).getOrElse {
      val actor = ctx.spawn(RoomActor.create(roomId), childrenName)
      ctx.watchWith(actor,ChildDead(childrenName,actor))
      actor
    }.unsafeUpcast[RoomActor.Command]
  }

  private def getRoomActorOpt(roomId:Long, ctx: ActorContext[Command]) = {
    val childrenName = s"roomActor-${roomId}"
//    log.debug(s"${ctx.self.path} the child = ${ctx.children},get the roomActor opt = ${ctx.child(childrenName).map(_.unsafeUpcast[RoomActor.Command])}")
    ctx.child(childrenName).map(_.unsafeUpcast[RoomActor.Command])

  }


}
