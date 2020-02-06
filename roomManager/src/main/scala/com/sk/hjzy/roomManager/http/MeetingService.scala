package com.sk.hjzy.roomManager.http

import akka.actor.typed.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.sk.hjzy.roomManager.Boot.{emailManager4Web, executor, log, roomManager, scheduler, timeout}
import com.sk.hjzy.roomManager.utils.{CirceSupport, FileUtil, SecureUtil}
import io.circe._
import io.circe.generic.auto._
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{ErrorRsp, JoinMeeting, JoinMeetingRsp, NewMeeting, NewMeetingRsp, SuccessRsp}

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.actor.typed.scaladsl.AskPattern._
import com.sk.hjzy.roomManager.protocol.ActorProtocol.{JoinRoom, NewRoom}

import scala.concurrent.Future
trait MeetingService extends CirceSupport with ServiceUtils with SessionBase{

  private val newMeeting = (path("newMeeting") & post){
    entity(as[Either[Error, NewMeeting]]){
      case Right(req) =>
        val newRoomRsp : Future[NewMeetingRsp]  = roomManager ? (NewRoom(req.userId, req.roomId, req.roomName, req.roomDes, req.password, _))
        dealFutureResult{
          newRoomRsp.map{ r =>
            complete(r)
          }
        }
      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }

  private val joinMeeting = (path("joinMeeting") & post){
    entity(as[Either[Error, JoinMeeting]]){
      case Right(req) =>
        val joinRoom: Future[JoinMeetingRsp] = roomManager ? (JoinRoom(req.roomId, req.password,_))
        dealFutureResult{
          joinRoom.map{ rst =>
            complete(rst)
          }
        }
      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }

  val meetingRoute: Route = pathPrefix("Meeting"){
    newMeeting ~ joinMeeting
  }

}
