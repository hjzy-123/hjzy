package com.sk.hjzy.roomManager.utils

import akka.event.jul.Logger
import SecureUtil.genPostEnvelope
import org.slf4j.LoggerFactory
import com.sk.hjzy.roomManager.Boot.{executor, scheduler, system, timeout}
import com.sk.hjzy.protocol.ptcl.processer2Manager.Processor
import com.sk.hjzy.protocol.ptcl.processer2Manager.Processor.{CloseRoom, CloseRoomRsp, NewConnect, NewConnectRsp, UpdateRsp}
import com.sk.hjzy.roomManager.common.AppSettings
import com.sk.hjzy.roomManager.common.AppSettings.distributorDomain
import com.sk.hjzy.roomManager.http.ServiceUtils.CommonRsp

import scala.concurrent.Future
/**
  * created by byf on 2019.7.17 13:09
  * */
object ProcessorClient extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser.decode

  private val log = LoggerFactory.getLogger(this.getClass)

  val processorBaseUrl = s"http://${AppSettings.processorIp}:${AppSettings.processorPort}/theia/processor"
  val distributorBaseUrl = s"https://$distributorDomain/theia/distributor"

  def newConnect(roomId:Long, liveId4host: String, liveId4client: String, liveId4push: String, liveCode4push: String, layout: Int):Future[Either[String,NewConnectRsp]] = {
    val url = processorBaseUrl + "/newConnect"
    val jsonString = NewConnect(roomId, liveId4host, liveId4client, liveId4push, liveCode4push, layout).asJson.noSpaces
    postJsonRequestSend("newConnect",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[NewConnectRsp](v) match{
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

  def updateRoomInfo(roomId:Long,layout:Int):Future[Either[String,UpdateRsp]] = {
    val url = processorBaseUrl + "/updateRoomInfo"
    val jsonString = Processor.UpdateRoomInfo(roomId, layout).asJson.noSpaces
    postJsonRequestSend("updateRoomInfo",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[UpdateRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"updateRoomInfo decode error : $e")
            Left(s"updateRoomInfo decode error : $e")
        }
      case Left(error) =>
        log.error(s"updateRoomInfo postJsonRequestSend error : $error")
        Left(s"updateRoomInfo postJsonRequestSend error : $error")
    }
  }


  def closeRoom(roomId:Long):Future[Either[String,CloseRoomRsp]] = {
    val url = processorBaseUrl + "/closeRoom"
    val jsonString = CloseRoom(roomId).asJson.noSpaces
    postJsonRequestSend("closeRoom",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[CloseRoomRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"closeRoom decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"closeRoom postJsonRequestSend error : $error")
        Left("Error")
    }

  }


}
