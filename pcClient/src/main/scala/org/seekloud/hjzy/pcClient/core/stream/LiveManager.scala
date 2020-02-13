package org.seekloud.hjzy.pcClient.core.stream

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.AskPattern._
import javafx.scene.canvas.GraphicsContext
import org.bytedeco.ffmpeg.global.avcodec
import org.seekloud.hjzy.capture.sdk.DeviceUtil.{getAllDevices, getDeviceOption}
import org.seekloud.hjzy.capture.sdk.{DeviceUtil, MediaCapture}
import org.seekloud.hjzy.pcClient.common.AppSettings
import org.seekloud.hjzy.pcClient.core.collector.CaptureActor
import org.seekloud.hjzy.pcClient.core.rtp._
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.core.stream.StreamPuller.{PackageLossInfo, PullCommand}
import org.seekloud.hjzy.pcClient.scene.MeetingScene
import org.seekloud.hjzy.pcClient.utils.{GetAllPixel, NetUtil, RtpUtil}
import com.sk.hjzy.rtpClient.{PullStreamClient, PushStreamClient}
import org.seekloud.hjzy.pcClient.utils.RtpUtil.{clientHost, clientHostQueue}
import org.seekloud.hjzy.player.sdk.MediaPlayer
import org.slf4j.LoggerFactory
import org.seekloud.hjzy.pcClient.Boot.{executor, scheduler, timeout}

import concurrent.duration._
import language.postfixOps
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

/**
  * User: Arrow
  * Date: 2019/7/19
  * Time: 12:25
  *
  * 推拉流鉴权及控制
  *
  */
object LiveManager {

  private val log = LoggerFactory.getLogger(this.getClass)
  private var validHost = clientHost

  val dispatcher: DispatcherSelector = DispatcherSelector.fromConfig("akka.actor.my-blocking-dispatcher")

  case class JoinInfo(roomId: Long, audienceId: Long, gc: GraphicsContext)

  case class WatchInfo(roomId: Long, gc: GraphicsContext)

  case class VideoInfo(roomId: Long, pusherId: Long, gc: GraphicsContext)

  sealed trait LiveCommand

  final case object GetPackageLoss extends LiveCommand

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends LiveCommand

  final case class DevicesOn(gc: GraphicsContext, isJoin: Boolean = false, callBackFunc: Option[() => Unit] = None) extends LiveCommand

  final case object DeviceOff extends LiveCommand

  final case class SwitchMediaMode(isJoin: Boolean, reset: () => Unit) extends LiveCommand

  final case class ChangeMediaOption(bit: Option[Int], re: Option[String], frameRate: Option[Int],
    needImage: Boolean = true, needSound: Boolean = true, reset: () => Unit) extends LiveCommand with CaptureActor.CaptureCommand

  final case class ChangeCaptureMode(mediaSource: Int, cameraPosition: Int) extends LiveCommand

  final case class RecordOption(recordOrNot: Boolean, path: Option[String] = None, reset: () => Unit)  extends LiveCommand with CaptureActor.CaptureCommand

  final case class PushStream(liveId: String, liveCode: String) extends LiveCommand

  final case object InitRtpFailed extends LiveCommand

  final case object StopPush extends LiveCommand

  final case class Ask4State(reply: ActorRef[Boolean]) extends LiveCommand

  final case class PullStream(liveId: String, videoInfo: VideoInfo, meetingScene: Option[MeetingScene] = None) extends LiveCommand

  final case object StopPull extends LiveCommand

  final case object PusherStopped extends LiveCommand

  final case object PullerStopped extends LiveCommand

  private object PUSH_RETRY_TIMER_KEY

  private object PULL_RETRY_TIMER_KEY


  def create(parent: ActorRef[RmManager.RmCommand], mediaPlayer: MediaPlayer): Behavior[LiveCommand] =
    Behaviors.setup[LiveCommand] { ctx =>
      log.info(s"LiveManager is starting...")
      implicit val stashBuffer: StashBuffer[LiveCommand] = StashBuffer[LiveCommand](Int.MaxValue)
      Behaviors.withTimers[LiveCommand] { implicit timer =>
        idle(parent, mediaPlayer, isStart = false, isRegular = false)
      }
    }


  private def idle(
    parent: ActorRef[RmManager.RmCommand],
    mediaPlayer: MediaPlayer,
    captureActor: Option[ActorRef[CaptureActor.CaptureCommand]] = None,
    streamPusher: Option[ActorRef[StreamPusher.PushCommand]] = None,
    streamPuller: Option[(String, ActorRef[StreamPuller.PullCommand])] = None,
    mediaCapture: Option[MediaCapture] = None,
    isStart: Boolean,
    isRegular: Boolean
  )(
    implicit stashBuffer: StashBuffer[LiveCommand],
    timer: TimerScheduler[LiveCommand]
  ): Behavior[LiveCommand] =
    Behaviors.receive[LiveCommand] { (ctx, msg) =>
      msg match {
        case msg: DevicesOn =>
          val captureActor = getCaptureActor(ctx, msg.gc, msg.isJoin, msg.callBackFunc)
          val mediaCapture = MediaCapture(captureActor, debug = AppSettings.captureDebug, needTimestamp = AppSettings.needTimestamp)
          val availableDevices = GetAllPixel.getAllDevicePixel()
          var pixel = (640,360)
          if(availableDevices.nonEmpty && !availableDevices.contains("640x360")){
            pixel = DeviceUtil.parseImgResolution(availableDevices.max)
          }
          log.info("availableDevices pixels: "+availableDevices)
          mediaCapture.setAudioCodec(avcodec.AV_CODEC_ID_MP2)
          mediaCapture.setVideoCodec(avcodec.AV_CODEC_ID_MPEG2VIDEO)
          mediaCapture.setImageWidth(pixel._1)
          mediaCapture.setImageHeight(pixel._2)
          captureActor ! CaptureActor.GetMediaCapture(mediaCapture)
          mediaCapture.start()
          idle(parent, mediaPlayer, Some(captureActor), streamPusher, streamPuller, Some(mediaCapture), isStart = isStart, isRegular = isRegular)

        case DeviceOff =>
          captureActor.foreach(_ ! CaptureActor.StopCapture)
          idle(parent, mediaPlayer, None, streamPusher, streamPuller, mediaCapture, isStart = isStart, isRegular = isRegular)



        case msg: SwitchMediaMode =>
          captureActor.foreach(_ ! CaptureActor.SwitchMode(msg.isJoin, msg.reset))
          Behaviors.same

        case msg: ChangeMediaOption =>
          captureActor.foreach(_ ! msg)
          Behaviors.same

        case msg: ChangeCaptureMode =>
          msg.mediaSource match {
            case 0 =>
              mediaCapture.foreach(_.showCamera())
            case 1 =>
              mediaCapture.foreach(_.showDesktop())
            case 2 =>
              mediaCapture.foreach { me =>
                me.changeCameraPosition(msg.cameraPosition)
                me.showBoth()
              }
          }
          Behaviors.same


        case msg: RecordOption =>
          captureActor.foreach(_ ! msg)
          Behaviors.same

        case msg: PushStream =>
          log.debug(s"prepare push stream.")
          assert(captureActor.nonEmpty)
          if (streamPusher.isEmpty) {
            val pushChannel = new PushChannel
            val pusher = getStreamPusher(ctx, msg.liveId, msg.liveCode, captureActor.get)
            RtpUtil.initIpPool()
            validHost = clientHostQueue.dequeue()
            val rtpClient = new PushStreamClient(AppSettings.host, NetUtil.getFreePort, pushChannel.serverPushAddr, pusher,AppSettings.rtpServerDst)
            mediaCapture.foreach(_.setTimeGetter(rtpClient.getServerTimestamp))
            pusher ! StreamPusher.InitRtpClient(rtpClient)
            idle(parent, mediaPlayer, captureActor, Some(pusher), streamPuller, mediaCapture, isStart = isStart, isRegular = isRegular)
          } else {
            log.info(s"waiting for old pusher stop.")
            ctx.self ! StopPush
            timer.startSingleTimer(PUSH_RETRY_TIMER_KEY, msg, 100.millis)
            Behaviors.same
          }

        case InitRtpFailed =>
          ctx.self ! StopPush
          Behaviors.same

        case StopPush =>
          log.info(s"LiveManager stop pusher!")
          streamPusher.foreach {
            pusher =>
              log.info(s"stopping pusher...")
              pusher ! StreamPusher.StopPush
          }
          Behaviors.same

        case msg: PullStream =>
          if (streamPuller.isEmpty) {
            val pullChannel = new PullChannel
            val puller = getStreamPuller(ctx, msg.liveId, mediaPlayer, msg.videoInfo, msg.meetingScene)
            val rtpClient = new PullStreamClient(AppSettings.host, NetUtil.getFreePort, pullChannel.serverPullAddr, puller, AppSettings.rtpServerDst)
            puller ! StreamPuller.InitRtpClient(rtpClient)
            idle(parent, mediaPlayer, captureActor, streamPusher, Some((msg.liveId, puller)), mediaCapture, isStart = true, isRegular = isRegular)
          } else {
            log.info(s"waiting for old puller-${streamPuller.get._1} stop.")
            ctx.self ! StopPull
            timer.startSingleTimer(PULL_RETRY_TIMER_KEY, msg, 100.millis)
            Behaviors.same
          }

        case GetPackageLoss =>
          streamPuller.foreach { s =>
            s._2 ! StreamPuller.GetLossAndBand
          }
          Behaviors.same

        case StopPull =>
          log.info(s"LiveManager stop puller")
          streamPuller.foreach {
            puller =>
              log.info(s"stopping puller-${puller._1}")
              puller._2 ! StreamPuller.StopPull
          }
          Behaviors.same

        case PusherStopped =>
          log.info(s"LiveManager got pusher stopped.")
          idle(parent, mediaPlayer, captureActor, None, streamPuller, mediaCapture, isStart = isStart, isRegular = isRegular)

        case PullerStopped =>
          log.info(s"LiveManager got puller stopped.")
          if(isRegular) parent ! RmManager.PullerStopped
          idle(parent, mediaPlayer, captureActor, streamPusher, None, mediaCapture, isStart = false, isRegular = false)

        case Ask4State(reply) =>
          reply ! isStart
          idle(parent, mediaPlayer, captureActor, streamPusher, streamPuller, mediaCapture, isStart = isStart, isRegular = true)

        case ChildDead(child, childRef) =>
          log.debug(s"LiveManager unWatch child-$child")
          ctx.unwatch(childRef)
          Behaviors.same

        case x =>
          log.warn(s"unknown msg in idle: $x")
          Behaviors.unhandled
      }
    }

  private def getCaptureActor(
    ctx: ActorContext[LiveCommand],
    gc: GraphicsContext,
    isJoin: Boolean,
    callBackFunc: Option[() => Unit],
    frameRate: Int = 30
  ) = {
    val childName = s"captureActor-${System.currentTimeMillis()}"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(CaptureActor.create(frameRate, gc, isJoin, callBackFunc), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[CaptureActor.CaptureCommand]
  }

  private def getStreamPusher(
    ctx: ActorContext[LiveCommand],
    liveId: String,
    liveCode: String,
    //    mediaActor: ActorRef[MediaActor.MediaCommand]
    captureActor: ActorRef[CaptureActor.CaptureCommand]
  ) = {
    val childName = s"streamPusher-$liveId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(StreamPusher.create(liveId, liveCode, ctx.self, captureActor), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[StreamPusher.PushCommand]
  }

  private def getStreamPuller(
    ctx: ActorContext[LiveCommand],
    liveId: String,
    mediaPlayer: MediaPlayer,
    videoInfo: VideoInfo,
    //    joinInfo: Option[JoinInfo],
    //    watchInfo: Option[WatchInfo],
    meetingScene: Option[MeetingScene],
    //    audienceScene : Option[AudienceScene],
    //    hostScene: Option[HostScene]
  ) = {
    val childName = s"streamPuller-$liveId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(StreamPuller.create(liveId, ctx.self, mediaPlayer, videoInfo, meetingScene), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[StreamPuller.PullCommand]
  }


}