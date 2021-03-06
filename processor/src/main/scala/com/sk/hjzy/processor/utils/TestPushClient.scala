package com.sk.hjzy.processor.utils

/**
 * Author: Jason
 * Date: 2019/11/22
 * Time: 10:29
 */

import java.net.InetSocketAddress
import java.util.concurrent.{ExecutorService, Executors}

import com.sk.hjzy.processor.Boot.executor
import akka.actor.typed.scaladsl.adapter._
import com.sk.hjzy.processor.utils.ProcessorClient.{log, postJsonRequestSend, processorBaseUrl}
import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.{CloseRoom, CloseRoomRsp, NewConnect, NewConnectRsp, UpdateRoomInfo, UpdateRsp}
import com.sk.hjzy.rtpClient.PushStreamClient
import com.sk.hjzy.processor.common.AppSettings._

import scala.concurrent.Future
import scala.language.postfixOps
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, DispatcherSelector}
import akka.actor.ActorSystem
import org.bytedeco.javacpp.Loader
import org.slf4j.LoggerFactory
object TestPushClient extends HttpUtil {

  implicit val system: ActorSystem = ActorSystem("push", config)

  /**拉流数量*/
  private val pullStreamNum = 1

  /**拉流 liveId*/
  val liveId = "liveIdTest-1580"

  /** super5 内网 */
  val pushStreamDst = new InetSocketAddress("47.92.170.2", 42043)
  val pullStreamDst = new InetSocketAddress("47.92.170.2", 42044)
  val httpDst = "http://47.92.170.2:42040"

  val srcList = List("D:\\videos\\爱宠大机密.ts", "D:\\videos\\超能陆战队1.ts")
  val portList = List(1234, 2345, 3456)

  def single(ssrc:Int, src:String, port: Int):Unit = {
    val threadPool:ExecutorService=Executors.newFixedThreadPool(2)
    try {
//      var ssrc = 110
//      for(i <- 0 until 0+num){
//        ssrc += 1

//        threadPool.execute(new ThreadTest(ssrc))
//        println(srcList(i))
        val pushActor = system.spawn(TestPushActor.create(s"liveIdTest-$ssrc", ssrc, src), s"PushStreamActor-$ssrc")
        val pushClient = new PushStreamClient("0.0.0.0",port, pushStreamDst, pushActor, httpDst)
        pushActor ! TestPushActor.Ready(pushClient)
//      }
    }finally {
      threadPool.shutdown()
    }
  }

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

  def hls2Mp4():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg,"-f","mpegts", "-i", s"${debugPath}222.mp4","-b:v","1M","-c:v","libx264",
      s"${debugPath}233.mp4")
    val processor = pb.inheritIO().start()
    Thread.sleep(100000)

  }

  def main(args: Array[String]): Unit = {

    println("testPushClient start...")

    single(506, srcList(0),portList(2))
//    single(423, srcList(1),portList(1))

    Thread.sleep(2000)
    RtpClient.getLiveInfoFunc().map {
      case Right(rsp) =>
        println("获得push的live", rsp)
        newConnect(907, List( "liveIdTest-506"), 1, "liveIdTest-506", rsp.liveInfo.liveId, rsp.liveInfo.liveCode).map{
          r =>
            println("-----------------------------------------------------------------------------------", r)
            Thread.sleep(90000)

//            updateRoomInfo(755, List(("liveIdTest-505", 1)), 3, "liveIdTest-504").map{ r =>
//              println(r)
//            }

            closeRoom(907).map{ r=>
              println("-----------------", r)
            }
        }
      case Left(value) =>
        println("error", value)
    }


    Thread.sleep(1200000)

  }



}