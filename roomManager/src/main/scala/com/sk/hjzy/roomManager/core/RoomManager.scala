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
 * 管理房间列表, （创建会议，加入会议鉴权）
 */
object RoomManager {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  case class NewRoom(roomId: Long, roomName: String, roomDes: String, password: String) extends Command

  case class JoinRoom(roomId: Long, password: String,replyTo: ActorRef[Boolean]) extends Command

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
        idle(mutable.Map[Long,String]())
      }
    }
  }

  private def idle(roomPassMap: mutable.Map[Long, String])
                  (implicit stashBuffer: StashBuffer[Command],timer:TimerScheduler[Command]):Behavior[Command] = {

    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {

        case NewRoom(roomId, roomName: String, roomDes: String, password: String) =>
          //todo          val roomActor = getRoomActor(roomId, ctx)
          roomPassMap.put(roomId, password)
          Behaviors.same

        case JoinRoom(roomId: Long, password: String,replyTo: ActorRef[Boolean]) =>
          if(roomPassMap.get(roomId).nonEmpty){
            if(roomPassMap(roomId) == password)
              replyTo ! true
            else
              replyTo ! false
          }else{
            replyTo ! false
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
