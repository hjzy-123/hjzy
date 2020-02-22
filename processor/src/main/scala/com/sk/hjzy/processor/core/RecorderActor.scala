package com.sk.hjzy.processor.core

import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.OutputStream
import java.nio.ShortBuffer

import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.bytedeco.ffmpeg.global.{avcodec, avutil}
import org.bytedeco.javacv.{FFmpegFrameFilter, FFmpegFrameRecorder, Frame, Java2DFrameConverter}
import org.slf4j.LoggerFactory
import com.sk.hjzy.processor.common.AppSettings.{addTs, bitRate, debugPath, isDebug}
import com.sk.hjzy.processor.Boot.roomManager

import scala.collection.mutable
import scala.concurrent.duration._


/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午2:30
  *
  * actor由RoomActor创建
  * 编码线程 stream数据传入pipe
  * 合并连线线程
  */
object RecorderActor {

  var audioChannels = 2 //todo 待议
  val sampleFormat = 1 //todo 待议
  var frameRate = 30

  val CanvasSize: (Int, Int) = (512, 288)  // todo   这个size由什么决定

  val number = 8

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class UpdateRoomInfo(roomId: Long,  liveIdList: List[(String, Int)],num: Int, speaker: String) extends Command

  case object Init extends Command

  case object RestartRecord extends Command

  case object StopRecorder extends Command

  case object CloseRecorder extends Command

  case class NewFrame(liveId: String, frame: Frame) extends Command

  case class UpdateRecorder(channel: Int, sampleRate: Int, frameRate: Double, width: Int, height: Int, liveId: String) extends Command

  case object TimerKey4Close

  sealed trait VideoCommand

  case class TimeOut(msg: String) extends Command

  case class Image4Host(liveIdList: List[String], frame: Frame) extends VideoCommand

  case class Image4Client( liveId : String, frame: Frame) extends VideoCommand

  case class SetNum(num: Int) extends VideoCommand

  case class SetSpeaker(speaker: String) extends VideoCommand

  case class NewRecord4Ts(recorder4ts: FFmpegFrameRecorder) extends VideoCommand

  case object Close extends VideoCommand

  case class Ts4Host(var time: Long = 0)

  case class Ts4Client(var time: Long = 0)

  case class Image(var frame: Frame = null)

  case class Ts4LastImage(var time: Long = -1)

  case class Ts4LastSample(var time: Long = 0)

  private val emptyAudio = ShortBuffer.allocate(1024 * 2)
  private val emptyAudio4one = ShortBuffer.allocate(1152)

  def create(roomId: Long, liveIdList: List[String], num: Int, speaker: String, output: OutputStream): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"recorderActor start----")
          avutil.av_log_set_level(-8)
          val recorder4ts = new FFmpegFrameRecorder(output, CanvasSize._1, CanvasSize._2, audioChannels)
          recorder4ts.setFrameRate(frameRate)
          recorder4ts.setVideoBitrate(bitRate)
          recorder4ts.setVideoCodec(avcodec.AV_CODEC_ID_MPEG2VIDEO)
          recorder4ts.setAudioCodec(avcodec.AV_CODEC_ID_MP2)
          recorder4ts.setMaxBFrames(0)
          recorder4ts.setFormat("mpegts")
          try {
            recorder4ts.startUnsafe()
          } catch {
            case e: Exception =>
              log.error(s" recorder meet error when start:$e")
          }
          roomManager ! RoomManager.RecorderRef(roomId, ctx.self)
          ctx.self ! Init
          single(roomId, liveIdList, num, speaker, recorder4ts, null, null, null, null, output, 30000, CanvasSize)
      }
    }
  }

  def single(roomId: Long,liveIdList: List[String], num: Int, speaker: String,
  recorder4ts: FFmpegFrameRecorder,
  ffFilter: FFmpegFrameFilter,
  drawer: ActorRef[VideoCommand],
  ts4Host: Ts4Host,
  ts4Client: Ts4Client,
  out: OutputStream,
  tsDiffer: Int = 30000, canvasSize: (Int, Int))(implicit timer: TimerScheduler[Command],
  stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Init =>
          if (ffFilter != null) {
            ffFilter.close()
          }
          val ffFilterN = new FFmpegFrameFilter("[0:a][1:a] amix=inputs=2:duration=longest:dropout_transition=3:weights=1 1[a]", audioChannels)
          ffFilterN.setAudioChannels(audioChannels)
          ffFilterN.setSampleFormat(sampleFormat)

          //todo    setAudioInputs 是什么意思
          ffFilterN.setAudioInputs(2)
          ffFilterN.start()
          single(roomId,  liveIdList, num, speaker, recorder4ts, ffFilterN, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

        case UpdateRecorder(channel, sampleRate, f, width, height, liveId) =>
          if(liveId == liveIdList.head) {
            log.info(s"$roomId updateRecorder channel:$channel, sampleRate:$sampleRate, frameRate:$f, width:$width, height:$height")
            recorder4ts.setFrameRate(f)
            recorder4ts.setAudioChannels(channel)
            recorder4ts.setSampleRate(sampleRate)
            ffFilter.setAudioChannels(channel)
            ffFilter.setSampleRate(sampleRate)
            recorder4ts.setImageWidth(width)
            recorder4ts.setImageHeight(height)
            single(roomId, liveIdList, num, speaker, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer,  canvasSize)
          }else{
            Behaviors.same
          }

        case NewFrame(liveId, frame) =>
          if(liveId == liveIdList.head){
            recorder4ts.record(frame)
            Behaviors.same
          }else{
            log.info("进入work阶段")
            val canvas = new BufferedImage(CanvasSize._1, CanvasSize._2, BufferedImage.TYPE_3BYTE_BGR)

            val clientFrameMap: mutable.Map[String, Image] = mutable.Map[String, Image]()
            val Java2DFrameConverterMap: mutable.Map[String, Java2DFrameConverter] = mutable.Map[String, Java2DFrameConverter]()

            liveIdList.foreach{ id =>
              clientFrameMap.put(id, Image())
              Java2DFrameConverterMap.put(id, new Java2DFrameConverter())
            }

            val drawer = ctx.spawn(draw(canvas, canvas.getGraphics, Ts4LastImage(), clientFrameMap, recorder4ts, Java2DFrameConverterMap,new Java2DFrameConverter,
              num, speaker, "defaultImg.jpg", roomId, canvasSize), s"drawer_$roomId")
            ctx.self ! NewFrame(liveId, frame)
            work(roomId, liveIdList, num, speaker,recorder4ts,ffFilter, drawer,ts4Host,ts4Client,out,tsDiffer,canvasSize)
          }

        case CloseRecorder =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          try {
            ffFilter.close()
            drawer ! Close
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped

        case StopRecorder =>
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 1.seconds)
          Behaviors.same
      }
    }
  }

  def work(roomId: Long, liveIdList: List[String], num: Int, speaker: String,
           recorder4ts: FFmpegFrameRecorder,
           ffFilter: FFmpegFrameFilter,
           drawer: ActorRef[VideoCommand],
           ts4Host: Ts4Host,
           ts4Client: Ts4Client,
           out: OutputStream,
           tsDiffer: Int = 30000, canvasSize: (Int, Int))
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$roomId recorder to work behavior")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewFrame(liveId, frame) =>
          if (frame.image != null) {
            if (liveId == liveIdList.head) {
              drawer ! Image4Host(liveIdList,frame)
            } else if (liveIdList.tail.contains(liveId)) {
              drawer ! Image4Client(liveId,frame)
            } else {
              log.info(s"wrong, liveId, work got wrong img")
            }
          }
          if (frame.samples != null) {
            try {
              ffFilter.pushSamples(liveIdList.indexOf(liveId), frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)

              val f = ffFilter.pullSamples().clone()
              if (f != null) {
                recorder4ts.recordSamples(f.sampleRate, f.audioChannels, f.samples: _*)
              }
            } catch {
              case ex: Exception =>
                log.debug(s"$liveId record sample error system: $ex")
            }
          }
          Behaviors.same

        case msg: UpdateRoomInfo =>
          log.info(s"$roomId got msg: $msg in work.")
          if (msg.num != num) {
            drawer ! SetNum(msg.num)
          }
          if (msg.speaker != speaker) {
            drawer ! SetSpeaker(msg.speaker)
          }
          ctx.self ! RestartRecord

          var newliveIdList = liveIdList
          msg.liveIdList.foreach{ id =>
            if(id._2 == 1)
              newliveIdList = liveIdList :+ id._1
            else
              newliveIdList = liveIdList.filter( _ != id._1)
          }

          work(roomId,  newliveIdList, msg.num, msg.speaker, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

        case m@RestartRecord =>
          log.info(s"couple state get $m")
          Behaviors.same

        case CloseRecorder =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          try {
            ffFilter.close()
            drawer ! Close
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped

        case StopRecorder =>
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 1.seconds)
          Behaviors.same

        case x =>
          Behaviors.same
      }
    }
  }

  def draw(canvas: BufferedImage, graph: Graphics, lastTime: Ts4LastImage, clientFrame: mutable.Map[String, Image] ,
           recorder4ts: FFmpegFrameRecorder, convert1:mutable.Map[String, Java2DFrameConverter] ,convert:Java2DFrameConverter,
           num: Int, speaker:String ,bgImg: String, roomId: Long, canvasSize: (Int, Int)): Behavior[VideoCommand] = {
    Behaviors.setup[VideoCommand] { ctx =>
      Behaviors.receiveMessage[VideoCommand] {
        case t: Image4Host =>
          val time = t.frame.timestamp
          log.info("host frame")
          //fixme 优化布局

          if(num > 0 & num < 5){
            graph.drawImage(convert1(t.liveIdList.head).convert(t.frame), canvasSize._1/2 * 0, 0, canvasSize._1/2, canvasSize._2/2, null)
            t.liveIdList.tail.foreach{ liveId =>
              val index = t.liveIdList.indexOf(liveId)
              val img: BufferedImage = convert1(liveId).convert(clientFrame(liveId).frame)
              if(index < 2)
                graph.drawImage(img, canvasSize._1/2 * index, 0, canvasSize._1/2, canvasSize._2/2, null)
              else
                graph.drawImage(img, canvasSize._1/2 * (index-2), canvasSize._2/2, canvasSize._1/2, canvasSize._2/2, null)
            }

            val speakerIndex = t.liveIdList.indexOf(speaker)
            if(speakerIndex < 2)
              graph.drawString("发言人", canvasSize._1/2 * speakerIndex + 24, 24)
            else
              graph.drawString("发言人", canvasSize._1/2 * (speakerIndex - 2), canvasSize._2 / 2 + 24)
          }else if(num > 4 & num < 10){
            graph.drawImage(convert1(t.liveIdList.head).convert(t.frame), canvasSize._1/3 * 0, 0, canvasSize._1/3, canvasSize._2/3, null)
            t.liveIdList.tail.foreach{ liveId =>
              val index = t.liveIdList.indexOf(liveId)
              val img: BufferedImage = convert1(liveId).convert(clientFrame(liveId).frame)
              if(index < 3)
                graph.drawImage(img, canvasSize._1/3 * index, 0, canvasSize._1/3, canvasSize._2/3, null)
              else if(index < 6 & index >2)
                graph.drawImage(img, canvasSize._1/3 * (index-3), canvasSize._2/3 , canvasSize._1/3, canvasSize._2/3, null)
              else
                graph.drawImage(img, canvasSize._1/3 * (index-6), canvasSize._2/3 * 2, canvasSize._1/3, canvasSize._2/3, null)
            }

            val speakerIndex = t.liveIdList.indexOf(speaker)
            if(speakerIndex < 3)
              graph.drawString("发言人", canvasSize._1/3 * speakerIndex + 24, 24)
            else if(speakerIndex > 2 & speakerIndex < 6)
              graph.drawString("发言人", canvasSize._1/3 * (speakerIndex - 3), canvasSize._2/3 + 24)
            else
              graph.drawString("发言人", canvasSize._1/3 * (speakerIndex - 6), canvasSize._2/3 * 2 + 24)
          }

          //fixme 此处为何不直接recordImage
          val frame = convert.convert(canvas)
          recorder4ts.record(frame.clone())
          Behaviors.same

        case t: Image4Client =>
          clientFrame(t.liveId).frame = t.frame
          Behaviors.same

        case m@NewRecord4Ts(recorder4ts) =>
          log.info(s"got msg: $m")
          draw(canvas, graph, lastTime, clientFrame, recorder4ts, convert1 ,convert, num, speaker, bgImg, roomId, canvasSize)

        case Close =>
          log.info(s"drawer stopped")
          recorder4ts.releaseUnsafe()
          Behaviors.stopped

        case  t: SetNum =>
          log.info(s"got msg: $t")
          draw(canvas, graph, lastTime, clientFrame, recorder4ts, convert1 ,convert, t.num, speaker, bgImg, roomId, canvasSize)

        case  t: SetSpeaker =>
          log.info(s"got msg: $t")
          draw(canvas, graph, lastTime, clientFrame, recorder4ts, convert1 ,convert, num, t.speaker, bgImg, roomId, canvasSize)
      }
    }
  }

}
