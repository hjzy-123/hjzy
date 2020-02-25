package com.sk.hjzy.processor.utils

/**
 * Author: Jason
 * Date: 2019/11/22
 * Time: 10:29
 */

import java.net.InetSocketAddress
import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import com.sk.hjzy.processor.Boot.executor
import com.sk.hjzy.processor.common.AppSettings._
import com.sk.hjzy.processor.utils.ProcessorClient.processorBaseUrl
import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.{NewConnect, NewConnectRsp}
import com.sk.hjzy.rtpClient.PushStreamClient
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import scala.concurrent.Future
import scala.language.postfixOps

object TestPushClient2 extends HttpUtil {

  implicit val system: ActorSystem = ActorSystem("push", config)

  /**拉流数量*/
  private val pullStreamNum = 1

  /**拉流 liveId*/
  val liveId = "liveIdTest-1580"

  /** super5 内网 */
  val pushStreamDst = new InetSocketAddress("10.1.29.247", 42043)
  val pullStreamDst = new InetSocketAddress("10.1.29.247", 42044)
  val httpDst = "http://10.1.29.247:42040"

  val srcList = List("D:\\videos\\爱宠大机密.ts", "D:\\videos\\超能陆战队1.ts")
  val portList = List(1234, 2345,  3456)

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

  def main(args: Array[String]): Unit = {

    println("testPushClient start...")

    single(505, srcList(0),portList(2))

    Thread.sleep(1200000)

  }



}