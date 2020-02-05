package com.sk.hjzy.roomManager.http

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.sk.hjzy.roomManager.Boot.{emailManager4Web, executor, log, roomManager, scheduler, timeout}
import akka.actor.typed.scaladsl.AskPattern._
import com.sk.hjzy.roomManager.utils.{CirceSupport, FileUtil, SecureUtil}
import io.circe._
import io.circe.generic.auto._
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{ErrorRsp, JoinMeeting, JoinMeetingRsp, NewMeeting, NewMeetingRsp, SuccessRsp}
import com.sk.hjzy.roomManager.core.RoomManager

import scala.concurrent.Future

trait MeetingService extends CirceSupport with ServiceUtils with SessionBase{

  private val newMeeting = (path("newMeeting") & post){
    entity(as[Either[Error, NewMeeting]]){
      case Right(req) =>
        roomManager ! RoomManager.NewRoom(req.roomId, req.roomName, req.roomDes, req.password)
        complete(SuccessRsp)
      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }

  private val joinMeeting = (path("joinMeeting") & post){
    entity(as[Either[Error, JoinMeeting]]){
      case Right(req) =>
        val verify: Future[Boolean] = roomManager ? (RoomManager.JoinRoom(req.roomId, req.password,_))
        dealFutureResult{
          verify.map{ rst =>
            if(rst){
              complete(SuccessRsp)
            }else{
              complete(ErrorRsp(100002, "房间号或密码错误"))
            }
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
