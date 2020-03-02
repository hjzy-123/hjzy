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

  val CanvasSize: (Int, Int) = (640, 480)  // todo   这个size由什么决定

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

  case class UpdateDrawer(liveIdList: List[(String, Int)], num: Int, speaker: String) extends VideoCommand

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
          val ts4ClientMap: mutable.Map[String, Ts4Client] = mutable.Map[String, Ts4Client]()
          liveIdList.foreach{ id =>
            ts4ClientMap.put(id, Ts4Client())
          }
          ctx.self ! Init
          work(roomId, liveIdList, num, speaker, recorder4ts, null, null, Ts4Host(), ts4ClientMap, output, 30000, CanvasSize)
      }
    }
  }

  def work(roomId: Long,liveIdList: List[String], num: Int, speaker: String,
  recorder4ts: FFmpegFrameRecorder,
  ffFilter: FFmpegFrameFilter,
  drawer: ActorRef[VideoCommand],
  ts4Host: Ts4Host,
  ts4Client: mutable.Map[String, Ts4Client],
  out: OutputStream,
  tsDiffer: Int = 20000, canvasSize: (Int, Int))(implicit timer: TimerScheduler[Command],
  stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Init =>
          if(num == 1)
            work(roomId, liveIdList, num, speaker, recorder4ts, null, null, ts4Host, ts4Client, out, tsDiffer,  canvasSize)
          else{
            if (ffFilter != null) {
              println("------------------------------------------------------------------------------------")
              ffFilter.close()
            }

            var input= ""
            for(i <- 0 until num)
              input = input + s"[$i:a]"
            println(s"???????????????????????$input+++++++++++${num}+++++++++++++++++++")

            val ffFilterN = new FFmpegFrameFilter(s"$input amix=inputs=${num}:duration=longest:dropout_transition=3 0[a]", audioChannels)
            ffFilterN.setAudioChannels(audioChannels)
            ffFilterN.setSampleFormat(sampleFormat)
            println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++")
            ffFilterN.setAudioInputs(num)
            println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
            ffFilterN.start()

            if(drawer == null){
              val canvas = new BufferedImage(CanvasSize._1, CanvasSize._2, BufferedImage.TYPE_3BYTE_BGR)
              val clientFrameMap: mutable.Map[String, Image] = mutable.Map[String, Image]()
              val Java2DFrameConverterMap: mutable.Map[String, Java2DFrameConverter] = mutable.Map[String, Java2DFrameConverter]()

              liveIdList.foreach{ id =>
                clientFrameMap.put(id, Image())
                Java2DFrameConverterMap.put(id, new Java2DFrameConverter())
              }
              val drawerNew = ctx.spawn(draw(liveIdList, ffFilterN,canvas, canvas.getGraphics, Ts4LastImage(), clientFrameMap, recorder4ts, Java2DFrameConverterMap,new Java2DFrameConverter,
                num, speaker, "defaultImg.jpg", roomId, canvasSize), s"drawer_$roomId")
              work(roomId,  liveIdList, num, speaker, recorder4ts, ffFilterN, drawerNew, ts4Host, ts4Client, out, tsDiffer, canvasSize)
            }else
              work(roomId,  liveIdList, num, speaker, recorder4ts, ffFilterN, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)
          }

        case UpdateRecorder(channel, sampleRate, f, width, height, liveId) =>
          if(liveId == liveIdList.head) {
            log.info(s"$roomId updateRecorder channel:$channel, sampleRate:$sampleRate, frameRate:$f, width:$width, height:$height")
            recorder4ts.setFrameRate(f)
            recorder4ts.setAudioChannels(channel)
            recorder4ts.setSampleRate(sampleRate)
            if(ffFilter != null){
              ffFilter.setAudioChannels(channel)
              ffFilter.setSampleRate(sampleRate)
            }
            recorder4ts.setImageWidth(width)
            recorder4ts.setImageHeight(height)
            work(roomId, liveIdList, num, speaker, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer,  canvasSize)
          }else{
            Behaviors.same
          }

        case NewFrame(liveId, frame) =>
          if(num == 1){
            recorder4ts.record(frame)
          }else{
            if (frame.image != null) {
              if (liveId == liveIdList.head) {
                drawer ! Image4Host(liveIdList,frame)
              } else if (liveIdList.tail.contains(liveId)) {
                drawer ! Image4Client(liveId,frame)
              } else {
                log.info(s"wrong, liveId, work got wrong img")
              }
            }
//            if (frame.samples != null) {
//              try {
//                ffFilter.pushSamples(liveIdList.indexOf(liveId), frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)
////                if(liveId==liveIdList.head){
////                  println(s"++++++++++++++++++++++++++++++++++++++++++++++$liveId",(frame.timestamp-ts4Host.time> tsDiffer))
////                  if((frame.timestamp-ts4Host.time> tsDiffer) && ts4Host.time != -1) {
////                    println(s"$liveId ooooooooooooooooooooooooooooooooooooooooo-- ${((frame.timestamp - ts4Host.time) / 22000l).toInt}")
////                    (2 to ((frame.timestamp - ts4Host.time) / 23000l).toInt).foreach { i =>
////                      log.info(s"$liveId loss sample-- $i")
////                      if (frame.audioChannels == 2) {
////                        ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio)
////                      } else {
////                        ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio4one)
////                      }
////                    }
////                  }
////                  ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)
////                  if(frame.timestamp > ts4Host.time) {
////                    println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
////                    ts4Host.time = frame.timestamp
////                  }
////                }else{
////                  println(s"++++++++++++++++++++++++++++++++++++++++++++++$liveId",(frame.timestamp-ts4Client(liveId).time> tsDiffer))
////                  if((frame.timestamp-ts4Client(liveId).time> tsDiffer) && ts4Client(liveId).time != 0) {
////                    println(s"$liveId kkkkkkkkkkkkkkkkkkkkkkkkkkkkk-- ${((frame.timestamp - ts4Client(liveId).time) / 22000l).toInt}")
////                    (2 to ((frame.timestamp - ts4Client(liveId).time) / 22000l).toInt).foreach { i =>
////                      log.info(s"$liveId loss sample-- $i")
////
////                      if (frame.audioChannels == 2) {
////                        ffFilter.pushSamples(liveIdList.indexOf(liveId), frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio)
////                      } else {
////                        ffFilter.pushSamples(liveIdList.indexOf(liveId), frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio4one)
////                      }
////                    }
////                  }
////                  ffFilter.pushSamples(liveIdList.indexOf(liveId), frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)
////                  if(frame.timestamp > ts4Client(liveId).time) {
////                    println("bbbbbbbbbbbbbbbbbbbbbbbbb")
////                    ts4Client(liveId).time = frame.timestamp
////                  }
////                }
//
//                val f = ffFilter.pullSamples().clone()
//                if (f != null) {
//                  println(s"!!!!!!!!!!!!!!!!!   have sound2222222222  $liveId    $f")
//                  if((f.timestamp - frame.timestamp) < 30000){
//                    println("qqqqqqqqqqqqqqqqqqqqqqqqqq",(f.timestamp - frame.timestamp))
//                    recorder4ts.recordSamples(f.sampleRate, f.audioChannels, f.samples: _*)
//                  }
//                }
//              } catch {
//                case ex: Exception =>
//                  log.debug(s"$liveId record sample error system: $ex")
//              }
//            }
          }
          Behaviors.same

        case msg: UpdateRoomInfo =>
          log.info(s"${ctx.self} receive a msg $msg")
          var newliveIdList = liveIdList
          msg.liveIdList.foreach{ id =>
            if(id._2 == 1)
              newliveIdList = liveIdList :+ id._1
            else
              newliveIdList = liveIdList.filter( _ != id._1)
          }
          if(drawer != null)
            drawer ! UpdateDrawer(msg.liveIdList, msg.num, msg.speaker)
          ctx.self ! Init
          work(roomId,  newliveIdList, msg.num, msg.speaker, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

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

  def draw(liveList: List[String], ffFilter: FFmpegFrameFilter,canvas: BufferedImage, graph: Graphics, lastTime: Ts4LastImage, clientFrame: mutable.Map[String, Image] ,
           recorder4ts: FFmpegFrameRecorder, convert1:mutable.Map[String, Java2DFrameConverter] ,convert:Java2DFrameConverter,
           num: Int, speaker:String ,bgImg: String, roomId: Long, canvasSize: (Int, Int)): Behavior[VideoCommand] = {
    Behaviors.setup[VideoCommand] { ctx =>
      Behaviors.receiveMessage[VideoCommand] {
        case t: Image4Host =>
          if(t.frame.samples != null) {
            try{
              println("push sample", t.liveIdList.head, "index", 0)
              ffFilter.pushSamples(0, t.frame.audioChannels, t.frame.sampleRate, ffFilter.getSampleFormat, t.frame.samples: _*)
            }catch {
              case ex: Exception =>
                log.debug(s"${t.liveIdList.head} record sample error system: $ex")
            }
          }
          val time = t.frame.timestamp
          log.info("host frame")
          var row = 0
          var flag = false
          if(num >0 & num <= 2) {
            row = 2
            flag = true
          } else if(num > 2 & num <= 4) {
            row = 2
          } else if(num > 4 & num <=9) {
            row = 3
          }

          t.liveIdList.foreach{ liveId =>
            val index = t.liveIdList.indexOf(liveId)
            var img: BufferedImage = convert1(liveId).convert(clientFrame(liveId).frame)
            if(index == 0)
              img = convert1(liveId).convert(t.frame)
            if(index < row) {
              if(flag)
                graph.drawImage(img, canvasSize._1/row * index , canvasSize._2 / 4, canvasSize._1/row, canvasSize._2/row, null)
              else
                graph.drawImage(img, canvasSize._1/row * index, 0, canvasSize._1/row, canvasSize._2/row, null)
            } else if(index < row *2 & index >= row)
              graph.drawImage(img, canvasSize._1/row * (index-row), canvasSize._2/row , canvasSize._1/row, canvasSize._2/row, null)
            else
              graph.drawImage(img, canvasSize._1/row * (index-row * 2), canvasSize._2/row * 2, canvasSize._1/row, canvasSize._2/row, null)
          }

          val speakerIndex = t.liveIdList.indexOf(speaker)
          if(speakerIndex < row) {
            if(flag)
              graph.drawString("发言人", canvasSize._1/row * speakerIndex + 24, 24 + canvasSize._2/ 4)
            else
              graph.drawString("发言人", canvasSize._1/row * speakerIndex + 24, 24)
          } else if(speakerIndex >= row & speakerIndex < row * 2)
            graph.drawString("发言人", canvasSize._1/row * (speakerIndex - row), canvasSize._2/row + 24)
          else
            graph.drawString("发言人", canvasSize._1/row * (speakerIndex - row * 2) , canvasSize._2/row * 2 + 24)

          //fixme 此处为何不直接recordImage
          val frame = convert.convert(canvas)
          recorder4ts.record(frame.clone())

          if(t.frame.samples !=null){
            println("pulllllllll sample")
            val f = ffFilter.pullSamples().clone()
            if (f != null) {
              println(s"!!!!!!!!!!!!!!!!!   have sound2222222222  ${t.liveIdList.head}    $f")
              if((f.timestamp - frame.timestamp) < 30000){
                println("qqqqqqqqqqqqqqqqqqqqqqqqqq",(f.timestamp - frame.timestamp))
                recorder4ts.recordSamples(f.sampleRate, f.audioChannels, f.samples: _*)
              }
            }
          }
          Behaviors.same

        case t: Image4Client =>
          clientFrame(t.liveId).frame = t.frame
          if(t.frame.samples != null) {
            try{
              println("push sample", t.liveId, "index", liveList.indexOf(t.liveId))
              ffFilter.pushSamples(liveList.indexOf(t.liveId), t.frame.audioChannels, t.frame.sampleRate, ffFilter.getSampleFormat, t.frame.samples: _*)
            }catch {
              case ex: Exception =>
                log.debug(s"${t.liveId} record sample error system: $ex")
            }
          }
          Behaviors.same

        case m@NewRecord4Ts(recorder4ts) =>
          log.info(s"got msg: $m")
          draw(liveList, ffFilter,canvas, graph, lastTime, clientFrame, recorder4ts, convert1 ,convert, num, speaker, bgImg, roomId, canvasSize)

        case m@UpdateDrawer(liveIdList, num, speaker) =>
          log.info(s"got msg: $m")
          liveIdList.foreach{ l =>
            if(l._2 == 1) {
              clientFrame.put(l._1, Image())
              convert1.put(l._1, new Java2DFrameConverter())
            } else {
              clientFrame.remove(l._1)
              convert1.remove(l._1)
            }
          }
          graph.clearRect(0, 0, canvasSize._1, canvasSize._2)
          draw(liveList, ffFilter,canvas, graph, lastTime, clientFrame, recorder4ts, convert1 ,convert, num, speaker, bgImg, roomId, canvasSize)

        case Close =>
          log.info(s"drawer stopped")
          recorder4ts.releaseUnsafe()
          Behaviors.stopped
      }
    }
  }

}
