package com.sk.hjzy.processor.utils

import java.io.File

import com.sk.hjzy.processor.Boot.executor
import com.sk.hjzy.processor.utils.SecureUtil.genPostEnvelope
import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.{CloseRoom, CloseRoomRsp, NewConnect, NewConnectRsp, UpdateRoomInfo, UpdateRsp}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Future


object ProcessorClient extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.parser.decode
  import io.circe.syntax._

  private val log = LoggerFactory.getLogger(this.getClass)

  val processorBaseUrl = "http://127.0.0.1:30388/hjzy/processor"

  def newConnect(roomId: Long, liveIdList: List[String], num: Int, speaker: String, pushLiveId:String, pushLiveCode:String):Future[Either[String,NewConnectRsp]] = {
    val url = processorBaseUrl + "/newConnect"
    val jsonString = NewConnect(roomId, liveIdList, num, speaker, pushLiveId, pushLiveCode).asJson.noSpaces
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

  def updateRoomInfo(roomId: Long, liveIdList: List[(String,Int)], num: Int, speaker: String):Future[Either[String,UpdateRsp]] = {
    val url = processorBaseUrl + "/updateRoomInfo"
    val jsonString = UpdateRoomInfo(roomId, liveIdList, num, speaker).asJson.noSpaces
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

  def main(args: Array[String]): Unit = {

    //    deleteFile()
    //    updateRoomInfo(8888,List("liveIdTest-1111"),1,0).map{
    //      a=>
    //        println(a)
    //    }


    //
    //    closeRoom(8888).map{
    //      a =>
    //        println(a)
    //    }
    //    updateRoomInfo(8888,List("1000","2000"),2,0).map{
    //      a=>
    //        println(a)
    //    }

    newConnect(1111, List("1000", "1000", "1000"), 3, "1000", "", "").map{
      r =>
        println(r)
    }

    Thread.sleep(30000)
  }


}
