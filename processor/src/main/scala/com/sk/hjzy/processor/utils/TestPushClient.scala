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
import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.{NewConnect, NewConnectRsp}
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
import org.slf4j.LoggerFactory
object TestPushClient extends HttpUtil {

  implicit val system: ActorSystem = ActorSystem("push", config)

  /**拉流数量*/
  private val pullStreamNum = 1

  /**拉流 liveId*/
  val liveId = "liveIdTest-1580"

  val distributerBaseUrl = "http://10.1.29.248:30389/theia/distributor"

  /** super5 内网 */
  val pushStreamDst = new InetSocketAddress("10.1.29.246", 61040)
  val pullStreamDst = new InetSocketAddress("10.1.29.246", 61041)
  val httpDst = "http://10.1.29.248:30390"

  def single(num:Int):Unit = {
    val threadPool:ExecutorService=Executors.newFixedThreadPool(2)
    try {
      var ssrc = 220
      for(i <- 1199 until 1199+num){
        ssrc += 1
        //todo newConnect
//        newLive(i.toLong,s"liveIdTest-$ssrc",System.currentTimeMillis())
        //        newLive(i.toLong,s"$ssrc",System.currentTimeMillis())
        Thread.sleep(2000)
//        threadPool.execute(new ThreadTest(ssrc))
        val pushActor = system.spawn(TestPushActor.create(s"liveIdTest-$ssrc", ssrc), "PushStreamActor")
        val pushClient = new PushStreamClient("0.0.0.0", 1234, pushStreamDst, pushActor, httpDst)
        pushActor ! TestPushActor.Ready(pushClient)
      }
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

    single(1)

    /** 本地测试 */
    //        val pushStreamDst = new InetSocketAddress("127.0.0.1", 61040)
    //        val pullStreamDst = new InetSocketAddress("127.0.0.1", 61041)
    //        val httpDst = "http://127.0.0.1:30390"

    /** super1 内网 */
    //        val pushStreamDst = new InetSocketAddress("10.1.29.244", 61040)
    //        val pullStreamDst = new InetSocketAddress("10.1.29.244", 61041)
    //        val httpDst = "http://10.1.29.244:30390"

    /** super1 公网 */
    //    val pushStreamDst = new InetSocketAddress("media.seekloud.org", 61040)
    //    val pullStreamDst = new InetSocketAddress("media.seekloud.org", 61041)
    //    val httpDst = "https://media.seekloud.org:50443"

    /** super3 内网 */
    //        val pushStreamDst = new InetSocketAddress("10.1.29.246", 61040)
    //        val pullStreamDst = new InetSocketAddress("10.1.29.246", 61041)
    //        val httpDst = "http://10.1.29.246:30390"

    /** super3 公网 */
    //        val pushStreamDst = new InetSocketAddress("media.seekloud.com", 61040)
    //        val pullStreamDst = new InetSocketAddress("media.seekloud.com", 61041)
    //        val httpDst = "https://media.seekloud.com:50443"





    Thread.sleep(2000)



  }



}