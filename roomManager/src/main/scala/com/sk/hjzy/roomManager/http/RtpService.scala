package com.sk.hjzy.roomManager.http

import com.sk.hjzy.roomManager.Boot._

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.sk.hjzy.roomManager.Boot.{executor, scheduler}
import com.sk.hjzy.protocol.ptcl.CommonRsp
import com.sk.hjzy.protocol.ptcl.client2Manager.http.CommonProtocol.{GetLiveInfoRsp, GetLiveInfoRsp4RM}
import com.sk.hjzy.roomManager.http.SessionBase._
import com.sk.hjzy.protocol.ptcl.server2Manager.CommonProtocol.{Verify, VerifyError, VerifyRsp}
import com.sk.hjzy.roomManager.core.RoomManager
import com.sk.hjzy.roomManager.utils.RtpClient

import scala.concurrent.Future

trait RtpService extends ServiceUtils{
  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._


  case class GetLiveInfoReq()
  private val getLiveInfo = (path("getLiveInfo") & post){
    dealPostReq[GetLiveInfoReq]{req =>
      RtpClient.getLiveInfoFunc().map{
        case Right(rsp) =>
          log.debug(s"获取liveInfo  ..${rsp}")
          complete(GetLiveInfoRsp4RM(Some(rsp.liveInfo)))
        case Left(error) =>
          complete(CommonRsp(1000023, s"获取live info失败：${error}"))

      }.recover{
        case e:Exception =>
          complete(CommonRsp(1000024, s"获取live info失败：${e}"))
      }
    }
  }

  val rtpRoutes: Route = pathPrefix("rtp") {
    getLiveInfo
  }
}

