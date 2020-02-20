package com.sk.hjzy.processor.utils

import java.io.{File, FileInputStream}
import java.nio.ByteBuffer
import java.nio.channels.Pipe
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.sk.hjzy.rtpClient.Protocol.{AuthRsp, Header, PushStreamError}
import com.sk.hjzy.rtpClient.{Protocol, PushStreamClient}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * Author: Jason
 * Date: 2019/11/22
 * Time: 10:31
 */
object TestPushActor {

  //  private var lastSeq =  0

  //  var list = List.empty[Int]

  val tsStreamType = 33

  val seq = new AtomicInteger(0)

  private val log = LoggerFactory.getLogger(this.getClass)

  case class Ready(client: PushStreamClient) extends Protocol.Command

  case class PushStream(liveId: String, ssrc: Int) extends Protocol.Command

  case object WritePipe extends Protocol.Command

  case class PushAnotherStream(ssrc: Int) extends Protocol.Command

  case class PullStream(liveId: List[String]) extends Protocol.Command

  case class Auth(liveId: String, liveCode: String) extends Protocol.Command

  def create(liveId:String, ssrc:Int, src:String): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Protocol.Command] = StashBuffer[Protocol.Command](Int.MaxValue)
      Behaviors.withTimers[Protocol.Command] { implicit timer =>
        wait4Ready(liveId, ssrc, src)
      }
    }
  }

  def wait4Ready(liveId:String, ssrc:Int, src:String)
    (implicit timer: TimerScheduler[Protocol.Command],
      stashBuffer: StashBuffer[Protocol.Command]): Behavior[Protocol.Command] = {
    Behaviors.receive[Protocol.Command] { (ctx, msg) =>
      msg match {
        case Ready(client) =>
          log.info("recv Ready")
          stashBuffer.unstashAll(ctx, work(liveId:String, ssrc:Int, client, src))

        case x =>
          stashBuffer.stash(x)
          Behavior.same
      }
    }
  }

  def work(liveId:String, ssrc:Int, client: PushStreamClient, src:String)
    (implicit timer: TimerScheduler[Protocol.Command],
      stashBuffer: StashBuffer[Protocol.Command]): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { ctx =>
      //读取视频文件
//      val src = "D:\\videos\\爱宠大机密.ts"
      //      val src = "C:/Users/Administrator/Videos/2019-11-06 10-56-35.ts"
      val fis = new FileInputStream(new File(src))
      var countRead = 0
      var totalReadSize = 0
      var len = -1
      val videoPipe = Pipe.open()
      val sink = videoPipe.sink()
      val source = videoPipe.source()
      val dataBuff = ByteBuffer.allocate(7 * 188)
      client.authStart()
      val loopExecutor = new ScheduledThreadPoolExecutor(1)
      val bufRead = ByteBuffer.allocate(7 * 188)

      val loop = loopExecutor.scheduleAtFixedRate(
        () => {
          ctx.self ! WritePipe
        },
        0,
        ((1000.0 / 25 / 4) * 1000).toLong,
        TimeUnit.MICROSECONDS
      )
      timer.startPeriodicTimer(1, PushStream("", 1888), 10.millis)

      Behaviors.receiveMessage[Protocol.Command] {

        case Auth(liveId, liveCode) =>
          client.auth(liveId, liveCode)
          Behaviors.same

        case AuthRsp(liveId, ifSuccess) =>
          log.info("recv AuthRsp:" +  liveId + " auth " + ifSuccess)
          Behaviors.same

        case WritePipe =>
                    println("writing...")
          val buf_tempRead = new Array[Byte](188 * 2)
          bufRead.clear()
          len = fis.read(buf_tempRead,0,188 * 2)
          for(i <- buf_tempRead.indices){
            bufRead.put(buf_tempRead(i))
          }
          bufRead.flip()
          sink.write(bufRead)
          Behaviors.same



        case PushStream(_, _) =>
          val realSeq = seq.getAndIncrement()
          dataBuff.clear()
          val bytesRead = source.read(dataBuff)
          dataBuff.flip()
          if (bytesRead != -1) {
            log.info(s"bytesRead: $bytesRead")
            log.info(s"data length: ${dataBuff.remaining()}")
            val s = dataBuff.array().take(dataBuff.remaining())
            client.sendData(Header(tsStreamType, 0, realSeq, ssrc, System.currentTimeMillis()), s, client.pushStreamDst1, client.pushChannel, calcDelay = true)
            log.info("send")
            timer.startPeriodicTimer(1, PushStream(liveId, ssrc), (1000.0 / 25 / 4).toLong.millis)
          }
          else {
            log.info("shutdown")
            loop.cancel(false)
            loopExecutor.shutdown()
          }

          //          client.sendData(Header(tsStreamType, 0, realSeq, 1581, System.currentTimeMillis()), data, client.pushStreamDst1, client.pushChannel, calcDelay = true)
          Behaviors.same

        case PushAnotherStream(ssrc) =>
          //            timer.startPeriodicTimer(2, P ushStream("", ssrc), 2.5.millis)
          Behaviors.same

        case PushStreamError(liveId, errCode, msg) =>
          log.info("recv PushStreamError" + msg)
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }
}
