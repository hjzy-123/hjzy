package com.sk.hjzy.roomManager.core

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.protocol.ptcl.CommonInfo.{LiveInfo, RoomInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.CommonProtocol._
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol
import com.sk.hjzy.roomManager.Boot.{executor, scheduler, timeout}
import com.sk.hjzy.roomManager.common.AppSettings._
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.models.dao.{RecordDao, UserInfoDao}
import com.sk.hjzy.roomManager.core.RoomActor._
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import com.sk.hjzy.roomManager.utils.{DistributorClient, ProcessorClient}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * 由Boot创建
 * 处理并向客户端分发webSocket消息
 */
object RoomManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  case class ExistRoom(roomId:Long,replyTo:ActorRef[Boolean]) extends Command

  case class DelaySeekRecord(wholeRoomInfo:WholeRoomInfo, totalView:Int, roomId:Long, startTime:Long, liveId: String) extends Command
  case class OnSeekRecord(wholeRoomInfo:WholeRoomInfo, totalView:Int, roomId:Long, startTime:Long, liveId: String) extends Command

  case class GetRtmpLiveInfo(roomId:Long, replyTo:ActorRef[GetLiveInfoRsp4RM]) extends Command with RoomActor.Command

  private final case object DelaySeekRecordKey

  private final case object FinishPullKey

  def create():Behavior[Command] = {
    Behaviors.setup[Command]{ctx =>
      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
      log.info(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command]{implicit timer =>
        var roomInfo = RoomInfo(Common.TestConfig.TEST_ROOM_ID,"test_room","测试房间",Common.TestConfig.TEST_USER_ID,
          "byf1",UserInfoDao.getHeadImg(""),
          UserInfoDao.getCoverImg(""),0,0,
          Some(Common.getMpdPath(Common.TestConfig.TEST_ROOM_ID))
        )
        log.debug(s"${ctx.self.path} ---===== ${roomInfo.rtmp}")
        getRoomActor(Common.TestConfig.TEST_ROOM_ID,ctx) ! TestRoom(roomInfo)
        idle()
      }
    }
  }

  private def idle() //roomId -> (roomInfo, liveInfo)
                  (implicit stashBuffer: StashBuffer[Command],timer:TimerScheduler[Command]):Behavior[Command] = {

    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {
        case r@ActorProtocol.AddUserActor4Test(userId,roomId,userActor) =>
          getRoomActorOpt(roomId,ctx) match {
            case Some(actor) =>actor ! r
            case None =>
          }
          Behaviors.same

        case r@ActorProtocol.WebSocketMsgWithActor(userId,roomId,req) =>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) => actor ! r
            case None => log.debug(s"${ctx.self.path}请求错误，该房间还不存在，房间id=$roomId，用户id=$userId")
          }
          Behaviors.same

        case r@ActorProtocol.StartLiveAgain(roomId) =>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) => actor ! r
            case None => log.debug(s"${ctx.self.path}重新直播请求错误，该房间已经关闭，房间id=$roomId")
          }
          Behaviors.same

        case r@ActorProtocol.StartRoom4Anchor(userId,roomId,actor) =>
          getRoomActor(roomId,ctx) ! r
          Behaviors.same

        case r@GetRtmpLiveInfo(roomId, replyTo)=>
          getRoomActorOpt(roomId,ctx) match{
            case Some(actor) =>actor ! r
            case None =>
              log.debug(s"${ctx.self.path}房间未建立")
              replyTo ! GetLiveInfoRsp4RM(None,100041,s"获取live info 请求失败:房间不存在")
          }
          Behaviors.same

        //延时请求获取录像（计时器）
        case DelaySeekRecord(wholeRoomInfo, totalView, roomId, startTime, liveId) =>
          log.info("---- wait seconds to seek record ----")
          timer.startSingleTimer(DelaySeekRecordKey + roomId.toString + startTime, OnSeekRecord(wholeRoomInfo, totalView, roomId, startTime, liveId), 5.seconds)
          Behaviors.same

        //延时请求获取录像
        case OnSeekRecord(wholeRoomInfo, totalView, roomId, startTime, liveId) =>
          timer.cancel(DelaySeekRecordKey + roomId.toString + startTime)
          DistributorClient.seekRecord(roomId,startTime).onComplete{
            case Success(v) =>
              v match{
                case Right(rsp) =>
                  log.debug(s"${ctx.self.path}获取录像id${roomId}时长为duration=${rsp.duration}")
                  RecordDao.addRecord(wholeRoomInfo.roomInfo.roomId,
                    wholeRoomInfo.roomInfo.roomName,wholeRoomInfo.roomInfo.roomDes,startTime,
                    UserInfoDao.getVideoImg(wholeRoomInfo.roomInfo.coverImgUrl),0,wholeRoomInfo.roomInfo.like,rsp.duration)
                  //timer.startSingleTimer(FinishPullKey + roomId.toString + startTime, FinishPull(roomId, startTime, liveId), 5.seconds)
                case Left(err) =>
                  log.debug(s"${ctx.self.path} 查询录像文件信息失败,error:$err")
              }

            case Failure(error) =>
              log.debug(s"${ctx.self.path} 查询录像文件失败,error:$error")
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
