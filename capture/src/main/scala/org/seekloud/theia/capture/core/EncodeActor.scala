package org.seekloud.hjzy.capture.core

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.{Buffer, ShortBuffer}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.{LinkedBlockingDeque, ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.bytedeco.javacv.{FFmpegFrameRecorder, Java2DFrameConverter}
import org.seekloud.hjzy.capture.sdk.MediaCapture.executor
import org.seekloud.hjzy.capture.protocol.Messages
import org.seekloud.hjzy.capture.protocol.Messages.{EncodeException, EncoderType}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}
import org.bytedeco.javacv.Frame
import org.seekloud.hjzy.capture.core.CaptureManager.MediaSettings
/**
  * User: TangYaruo
  * Date: 2019/8/28
  * Time: 17:54
  * Description: 用来存储视频对视频的编码
  */
object EncodeActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  var debug: Boolean = true
  private var needTimeMark: Boolean = false

  private var savedFrame: Option[Frame] = None

  def debug(msg: String): Unit = {
    if (debug) log.debug(msg)
  }

  sealed trait Command

  final case object StartEncodeLoop extends Command

  final case object EncodeLoop extends Command

  final case class EncodeSamples(sampleRate: Int, channel: Int, samples: ShortBuffer) extends Command

  final case object StopEncode extends Command

  final case object PauseEncode extends Command

  final case object StartEncode extends Command

  def create(
    replyTo: ActorRef[Messages.ReplyToCommand],
    encodeType: EncoderType.Value,
    encoder: FFmpegFrameRecorder,
    imageCache: LinkedBlockingDeque[Messages.LatestFrame],
    mediaSettings: MediaSettings,
    isDebug: Boolean,
    needTimestamp: Boolean
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"EncodeActor-$encodeType is starting...")
      debug = isDebug
      needTimeMark = needTimestamp
      Future {
        log.info(s"Encoder-$encodeType is starting...")
        encoder.startUnsafe()
        log.info(s"Encoder-$encodeType started.")
      }.onComplete {
        case Success(_) =>
          ctx.self ! StartEncodeLoop
        case Failure(e) =>
          encodeType match {
            case EncoderType.STREAM =>
              log.info(s"streamEncoder start failed: $e")
              replyTo ! Messages.StreamCannotBeEncoded
            case EncoderType.FILE =>
              log.info(s"fileEncoder start failed: $e")
              replyTo ! Messages.CannotSaveToFile
              ctx.self ! StopEncode
            case EncoderType.BILIBILI =>
              log.info(s"fileEncoder start failed: $e")
              replyTo ! Messages.CannotRecordToBiliBili
          }

      }
      working(replyTo, encodeType, encoder, imageCache, new Java2DFrameConverter(), mediaSettings)
    }


  private def working(
    replyTo: ActorRef[Messages.ReplyToCommand],
    encodeType: EncoderType.Value,
    encoder: FFmpegFrameRecorder,
    imageCache: LinkedBlockingDeque[Messages.LatestFrame],
    imageConverter: Java2DFrameConverter,
    mediaSettings: MediaSettings,
    encodeLoop: Option[ScheduledFuture[_]] = None,
    encodeExecutor: Option[ScheduledThreadPoolExecutor] = None,
    frameNumber: Int = 0,
    pauseEncode: Boolean = false
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StartEncodeLoop =>
          debug(s"frameRate: ${encoder.getFrameRate}, interval: ${1000 / encoder.getFrameRate}")

          val encodeLoopExecutor = new ScheduledThreadPoolExecutor(1)
          val loop = encodeLoopExecutor.scheduleAtFixedRate(
            () => {
              ctx.self ! EncodeLoop
            },
            0,
            ((1000.0 / encoder.getFrameRate) * 1000).toLong,
            TimeUnit.MICROSECONDS
          )

          working(replyTo, encodeType, encoder, imageCache, imageConverter, mediaSettings, Some(loop), Some(encodeLoopExecutor), frameNumber, pauseEncode)

        case PauseEncode =>
          working(replyTo, encodeType, encoder, imageCache, imageConverter, mediaSettings, encodeLoop, encodeExecutor, frameNumber, true)

        case StartEncode =>
          working(replyTo, encodeType, encoder, imageCache, imageConverter, mediaSettings, encodeLoop, encodeExecutor, frameNumber, false)

        case EncodeLoop =>
          if(!pauseEncode){
            if (mediaSettings.needImage) {
              try {
                val latestImage = imageCache.peek()
                if (latestImage != null) {
                  if(savedFrame.isDefined){
                    savedFrame = Some(latestImage.frame)
                  }
                  encoder.setTimestamp((frameNumber * (1000.0 / encoder.getFrameRate) * 1000).toLong)
                  if (!needTimeMark) {
                    encoder.record(latestImage.frame)
                  } else {
                    val iw = latestImage.frame.imageWidth
                    val ih = latestImage.frame.imageHeight
                    val bImg = imageConverter.convert(latestImage.frame)

                   // log.info(Java2DFrameConverter.getBufferedImageType(latestImage.frame) + "")
                    val ts = if (CaptureManager.timeGetter != null) CaptureManager.timeGetter() else System.currentTimeMillis()
                    val date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:S").format(ts)
                    bImg.getGraphics.drawString(date, iw / 10, ih / 10)
                    encoder.record(imageConverter.convert(bImg))
//                    log.info("record frame")
                  }
                }
              } catch {
                case ex: Exception =>
                  log.error(s"[$encodeType] encode image frame error: $ex")
                  if(ex.getMessage.startsWith("av_interleaved_write_frame() error")){
                    replyTo ! EncodeException(ex)
                    ctx.self ! StopEncode
                  }
              }
            }
          }else {
            try {
//              savedFrame.get.image.
              ////              blackFrame.image = new Array[Buffer]()

              val bImg = new BufferedImage(mediaSettings.imageWidth, mediaSettings.imageHeight, BufferedImage.TYPE_3BYTE_BGR)
//              val bImg = imageConverter.convert(blackFrame)
//              log.info("b")
//              log.info("!!!!" + (bImg == null))
              val gra = bImg.getGraphics
              gra.setColor(Color.BLACK)
              gra.fillRect(0, 0, mediaSettings.imageWidth, mediaSettings.imageHeight)
              encoder.record(imageConverter.convert(bImg))
            } catch {
              case ex: Exception =>
                log.error(s"[$encodeType] encode image frame error: $ex")
                if (ex.getMessage.startsWith("av_interleaved_write_frame() error")) {
                  replyTo ! EncodeException(ex)
                  ctx.self ! StopEncode
                }
            }
          }
          working(replyTo, encodeType, encoder, imageCache, imageConverter, mediaSettings, encodeLoop, encodeExecutor, frameNumber + 1, pauseEncode)

        case msg: EncodeSamples =>
          if (encodeLoop.nonEmpty) {
            try {
              encoder.recordSamples(msg.sampleRate, msg.channel, msg.samples)
            } catch {
              case ex: Exception =>
                log.warn(s"Encoder-$encodeType encode samples error: $ex")
            }
          }
          Behaviors.same

        case StopEncode =>
          log.info(s"encoding stopping...")
          encodeLoop.foreach(_.cancel(false))
          encodeExecutor.foreach(_.shutdown())
          try {
            encoder.releaseUnsafe()
            log.info(s"release encode resources.")
          } catch {
            case ex: Exception =>
              log.warn(s"release encode error: $ex")
              ex.printStackTrace()
          }
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in working: $x")
          Behaviors.unhandled
      }
    }

}
