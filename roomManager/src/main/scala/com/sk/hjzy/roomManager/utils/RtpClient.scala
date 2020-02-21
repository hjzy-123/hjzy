package com.sk.hjzy.roomManager.utils

import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{GetLiveInfoRsp, NewMeeting, NewMeetingRsp}
import SecureUtil.genPostEnvelope
import org.slf4j.LoggerFactory
import com.sk.hjzy.roomManager.Boot.{executor, scheduler, system, timeout}
import com.sk.hjzy.roomManager.common.AppSettings

import scala.concurrent.Future
/**
  * created by byf on 2019.8.28
  * */
object RtpClient extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser.decode

  private val log = LoggerFactory.getLogger(this.getClass)
  val rtpBaseUrl = s"http://${AppSettings.rtpIp}:${AppSettings.rtpPort}/theia/rtpServer/api"

  def getLiveInfoFunc():Future[Either[String,GetLiveInfoRsp]] = {
    log.debug("get live info")
    val url = rtpBaseUrl + "/getLiveInfo"
    val req = genPostEnvelope("roomManager",System.nanoTime().toString,"{}","484ec7db9e39bc4b5e3d").asJson.noSpaces
    postJsonRequestSend("getLiveInfo",url,List(),req,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[GetLiveInfoRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"getLiveInfo decode error : $e")
            Left(s"getLiveInfo decode error : $e")
        }
      case Left(error) =>
        log.error(s"getLiveInfo postJsonRequestSend error : $error")
        Left(s"getLiveInfo postJsonRequestSend error : $error")
    }
  }

  def testLiveInfoFunc():Future[Either[String,GetLiveInfoRsp]] = {
    val url = "http://localhost:30382/theia/roomManager/rtp" + "/getLiveInfo"
    val req = genPostEnvelope("processor",System.nanoTime().toString,"{}","0379a0aaff63c1ce").asJson.noSpaces
    postJsonRequestSend("getLiveInfo",url,List(),req,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[GetLiveInfoRsp](v) match{
          case Right(data) =>
            log.debug("success")
            Right(data)
          case Left(e) =>
            log.error(s"getLiveInfo decode error : $e")
            Left(s"getLiveInfo decode error : $e")
        }
      case Left(error) =>
        log.error(s"getLiveInfo postJsonRequestSend error : $error")
        Left(s"getLiveInfo postJsonRequestSend error : $error")
    }
  }

  def newMeeting( userId: Long, roomId: Long, roomName: String, roomDes: String, password: String, invitees: List[String]):Future[Either[String,NewMeetingRsp]] = {
    val url = "http://localhost:30380/hjzy/roomManager/Meeting" + "/newMeeting"
    val jsonString = NewMeeting(userId,roomId, roomName, roomDes, password, invitees).asJson.noSpaces
    postJsonRequestSend("newMeeting",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[NewMeetingRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"newConnect decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"newConnect postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def main(args: Array[String]):Unit ={
//    getLiveInfoFunc().map {
//      case Left(value) =>
//      case Right(value) =>
//        println(value)
//    }

    newMeeting(10, 4 , "测试", "test", "123", List("gy", "tldq")).map{
      case Left(value) =>
        println("error",value)
      case Right(value) =>
        println(" __---------",value)
    }
  }

}
