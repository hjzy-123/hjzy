package com.sk.hjzy.processor.core

import java.io.{File, FileOutputStream, OutputStream}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.sk.hjzy.processor.Boot
import com.sk.hjzy.processor.Boot.streamPullActor
import com.sk.hjzy.processor.common.AppSettings.{debugPath, isDebug}
import com.sk.hjzy.processor.core.StreamPullActor.NewLive
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午3:04
  *
  * actor由RoomActor创建，初始化GrabberActor时
  * 建立pullActor->grabber 管道
  * 存储map liveId->ActorRef
  */
object StreamPullPipe {

  sealed trait Command

  case class NewBuffer(data: Array[Byte]) extends Command

  case object ClosePipe extends Command

  case object Timer4Stop

  case object Stop extends Command

  val log = LoggerFactory.getLogger(this.getClass)

  def create(roomId: Long, liveId: String, out: OutputStream): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          streamPullActor ! NewLive(liveId, roomId, ctx.self)
          val output = if (false) {
            val file = new File(s"$debugPath$roomId/${liveId}_in.mp4")
            Some(new FileOutputStream(file))
          } else None
          work(roomId, liveId, out, output)
      }
    }
  }

  def work(roomId: Long, liveId: String, out: OutputStream, fileOut:Option[FileOutputStream])(implicit timer: TimerScheduler[Command],
                                                                                   stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewBuffer(data) =>
          if (Boot.showStreamLog) {
            log.info(s"NewBuffer $liveId ${data.length}")
          }
//          fileOut.foreach(_.write(data))
          out.write(data)
          Behaviors.same

        case ClosePipe =>
          timer.startSingleTimer(Timer4Stop, Stop, 50.milli)
          Behaviors.same

        case Stop =>
          log.info(s"$liveId pullPipe stopped ----")
//          fileOut.foreach(_.close())
          out.close()
          Behaviors.stopped

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }
}