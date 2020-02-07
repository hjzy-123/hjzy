package com.sk.hjzy.roomManager.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{JoinMeetingRsp, NewMeetingRsp}
import com.sk.hjzy.roomManager.common.Common
import com.sk.hjzy.roomManager.models.dao.{RecordDao, UserInfoDao}
import com.sk.hjzy.roomManager.core.RoomActor._
import com.sk.hjzy.roomManager.protocol.ActorProtocol
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{GetUserInfoList, JoinRoom, NewRoom}
import org.slf4j.LoggerFactory

/**
 * 由Boot创建
 * 管理房间列表, （创建会议，加入会议鉴权）
 * 通知roomActor webSocket消息
 */
object RoomManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

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

        case r@NewRoom(userId:Long, roomId, roomName: String, roomDes: String, password: String,replyTo: ActorRef[NewMeetingRsp]) =>
          val roomActor = getRoomActor(roomId, ctx)
          roomActor ! r
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

        case r@ActorProtocol.StartMeeting(userId,roomId,actor) =>
          getRoomActor(roomId,ctx) ! r
          Behaviors.same

        case r@ActorProtocol.HostCloseRoom(roomId)=>
          //如果断开websocket的用户的id能够和已经开的房间里面的信息匹配上，就说明是主播
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
