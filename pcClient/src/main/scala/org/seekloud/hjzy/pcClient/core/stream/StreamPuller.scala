package org.seekloud.hjzy.pcClient.core.stream

import java.io.FileOutputStream
import java.net.{DatagramPacket, DatagramSocket, InetAddress}
import java.nio.ByteBuffer
import java.nio.channels.{Channels, Pipe}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.Ids
import org.seekloud.hjzy.pcClient.core.stream.LiveManager.VideoInfo
import org.seekloud.hjzy.pcClient.utils.NetUtil
//import org.seekloud.hjzy.pcClient.common.Constants.AudienceStatus
//import org.seekloud.hjzy.pcClient.common.Ids
import org.seekloud.hjzy.pcClient.component.WarningDialog
import org.seekloud.hjzy.pcClient.core.stream.LiveManager.{JoinInfo, WatchInfo}
import org.seekloud.hjzy.player.sdk.MediaPlayer
import com.sk.hjzy.rtpClient.Protocol._
import com.sk.hjzy.rtpClient.{Protocol, PullStreamClient}
import org.seekloud.hjzy.pcClient.core.player.VideoPlayer
import org.seekloud.hjzy.pcClient.scene.MeetingScene
import org.slf4j.LoggerFactory

import concurrent.duration._

/**
  * User: TangYaruo
  * Date: 2019/8/20
  * Time: 13:41
  */
object StreamPuller {

  private val log = LoggerFactory.getLogger(this.getClass)

  //  val outStream = new FileOutputStream(s"video-${System.currentTimeMillis()}.mp4")

  type PullCommand = Protocol.Command

  case class PackageLossInfo(lossScale60: Double, lossScale10: Double, lossScale2: Double)

  case class BandWidthInfo(bandWidth60s: Double, bandWidth10s: Double, bandWidth2s: Double)

  final case class InitRtpClient(pullClient: PullStreamClient) extends PullCommand

  final case object PullStartTimeOut extends PullCommand

  final case object GetLossAndBand extends PullCommand

  final case object PullStream extends PullCommand

  final case object PullTimeOut extends PullCommand

  final case object StopPull extends PullCommand

  final case object StopSelf extends PullCommand

  private case class TimeOut(msg: String) extends PullCommand

  private final case object BehaviorChangeKey

  private val addr = InetAddress.getByName("127.0.0.1")

  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[PullCommand],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends PullCommand

  private[this] def switchBehavior(ctx: ActorContext[PullCommand],
    behaviorName: String, behavior: Behavior[PullCommand], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[PullCommand],
      timer: TimerScheduler[PullCommand]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }


  def create(
    liveId: String,
    parent: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    videoInfo: VideoInfo,
    //    joinInfo: Option[JoinInfo],
    //    watchInfo: Option[WatchInfo],
    meetingScene: Option[MeetingScene],
    //    audienceScene: Option[AudienceScene],
    //    hostScene: Option[HostScene]
  ): Behavior[PullCommand] =
    Behaviors.setup[PullCommand] { ctx =>
      log.info(s"StreamPuller-$liveId is starting.")
      implicit val stashBuffer: StashBuffer[PullCommand] = StashBuffer[PullCommand](Int.MaxValue)
      Behaviors.withTimers[PullCommand] { implicit timer =>
        val port = NetUtil.getFreePort
        val socket = new DatagramSocket()
        init(liveId, parent, mediaPlayer, videoInfo, meetingScene, None, socket, port)
      }

    }

  private def init(
    liveId: String,
    parent: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    videoInfo: VideoInfo,
    //    joinInfo: Option[JoinInfo],
    //    watchInfo: Option[WatchInfo],
    meetingScene: Option[MeetingScene],
    //    audienceScene: Option[AudienceScene],
    //    hostScene: Option[HostScene],
    pullClient: Option[PullStreamClient],
    socket: DatagramSocket,
    port: Int
  )(
    implicit timer: TimerScheduler[PullCommand],
    stashBuffer: StashBuffer[PullCommand]
  ): Behavior[PullCommand] =
    Behaviors.receive[PullCommand] { (ctx, msg) =>
      msg match {
        case msg: InitRtpClient =>
          log.info(s"StreamPuller-$liveId init rtpClient.")
          msg.pullClient.pullStreamStart()
          timer.startSingleTimer(PullStartTimeOut, PullStartTimeOut, 5.seconds)
          //todo
          //          meetingScene.foreach(_.startPackageLoss())
          init(liveId, parent, mediaPlayer, videoInfo, meetingScene, Some(msg.pullClient), socket, port)

        case PullStreamReady =>
          log.info(s"StreamPuller-$liveId ready for pull.")
          timer.cancel(PullStartTimeOut)
          ctx.self ! PullStream
          Behaviors.same

        case PullStartTimeOut =>
          pullClient.foreach(_.getClientId())
          timer.startSingleTimer(PullStartTimeOut, PullStartTimeOut, 5.seconds)
          Behaviors.same

        case PullStream =>
          log.info(s"StreamPuller-$liveId PullStream.")
          if(liveId != "-1"){
            pullClient.foreach(_.pullStreamData(List(liveId)))
          }else{
            log.info(s"StreamPuller-liveId=$liveId,error id=$liveId")
            pullClient.foreach(_.pullStreamData(List()))
          }
          timer.startSingleTimer(PullTimeOut, PullTimeOut, 30.seconds)
          Behaviors.same

        case msg: PullStreamReqSuccess =>
          log.info(s"StreamPuller-$liveId PullStream-${msg.liveIds} success.")
          timer.cancel(PullTimeOut)
          //          val mediaPipe = Pipe.open() // server -> sink -> source -> client
          //          val sink = mediaPipe.sink()
          //          val source = mediaPipe.source()
          //          sink.configureBlocking(false)
          //
          //          val inputStream = Channels.newInputStream(source)
          val inputStream = s"udp://127.0.0.1:$port"
          //todo
          //          meetingScene.foreach(_.autoReset())
          val playId = Ids.getPlayId(videoInfo.roomId, videoInfo.pusherId)
          mediaPlayer.setTimeGetter(playId, pullClient.get.getServerTimestamp)
          val videoPlayer = ctx.spawn(VideoPlayer.create(playId, meetingScene, None, None), s"videoPlayer-$playId")
          mediaPlayer.start(playId, videoPlayer, Left(inputStream), Some(videoInfo.gc), None)

          //          if (joinInfo.nonEmpty) {
          //            audienceScene.foreach(_.autoReset())
          //            hostScene.foreach(_.resetBack())
          //            val playId = Ids.getPlayId(AudienceStatus.CONNECT, roomId = Some(joinInfo.get.roomId), audienceId = Some(joinInfo.get.audienceId))
          //            mediaPlayer.setTimeGetter(playId, pullClient.get.getServerTimestamp)
          //            val videoPlayer = ctx.spawn(VideoPlayer.create(playId, meetingScene, None, None), s"videoPlayer$playId")
          //            mediaPlayer.start(playId, videoPlayer, Right(inputStream), Some(joinInfo.get.gc), None)
          //          }
          //
          //          if (watchInfo.nonEmpty) {
          //            audienceScene.foreach(_.autoReset())
          //            val playId = Ids.getPlayId(AudienceStatus.LIVE, roomId = Some(watchInfo.get.roomId))
          //            mediaPlayer.setTimeGetter(playId, pullClient.get.getServerTimestamp)
          //            val videoPlayer = ctx.spawn(VideoPlayer.create(playId, meetingScene, None, None), s"videoPlayer$playId")
          //            mediaPlayer.start(playId, videoPlayer, Right(inputStream), Some(watchInfo.get.gc), None)
          //
          //          }
          stashBuffer.unstashAll(ctx, pulling(liveId, parent, pullClient.get, mediaPlayer, meetingScene, socket, port))

        case GetLossAndBand =>
          pullClient.foreach{ p =>
            val info = {
              p.getPackageLoss().map(i => i._1 -> PackageLossInfo(i._2.lossScale60, i._2.lossScale10, i._2.lossScale2))
            }

            val bandInfo = p.getBandWidth().map(i => i._1 -> BandWidthInfo(i._2.bandWidth60s, i._2.bandWidth10s, i._2.bandWidth2s))
            //todo
            //            meetingScene.foreach(_.drawPackageLoss(info, bandInfo))
          }
          Behaviors.same

        case PullStreamPacketLoss =>
          log.info(s"StreamPuller-$liveId PullStreamPacketLoss.")
          timer.startSingleTimer(PullStream, PullStream, 30.seconds)
          Behaviors.same

        case msg: NoStream =>
          log.info(s"No stream ids: ${msg.liveIds}")
          if (msg.liveIds.contains(liveId)) {
            log.info(s"Stream-$liveId unavailable now, try later.")
            timer.startSingleTimer(PullStream, PullStream, 30.seconds)
          }
          Behaviors.same

        case PullTimeOut =>
          log.info(s"StreamPuller-$liveId pull timeout, try again.")
          ctx.self ! PullStream
          Behaviors.same

        case StopPull =>
          log.info(s"StreamPuller-$liveId stopped in init.")
          parent ! LiveManager.PullerStopped
          Behaviors.stopped

        case x =>
          log.warn(s"unhandled msg in init: $x")
          stashBuffer.stash(x)
          Behaviors.same
      }
    }
  private object ENSURE_STOP_PULL

  private def pulling(
    liveId: String,
    parent: ActorRef[LiveManager.LiveCommand],
    pullClient: PullStreamClient,
    mediaPlayer: MediaPlayer,
    //    joinInfo: Option[JoinInfo],
    meetingScene: Option[MeetingScene],
    //    audienceScene: Option[AudienceScene],
    //    hostScene: Option[HostScene]
    socket: DatagramSocket,
    port: Int
  )(
    implicit timer: TimerScheduler[PullCommand],
    stashBuffer: StashBuffer[PullCommand]
  ): Behavior[PullCommand] =
    Behaviors.receive[PullCommand] { (ctx, msg) =>
      msg match {
        case msg: PullStreamData =>
          if (msg.data.nonEmpty) {
            try {
              //              log.debug(s"StreamPuller-$liveId pull-${msg.data.length}.")
              //              outStream.write(msg.data)
              val s = msg.data
              val datagramPacket = new DatagramPacket(s, s.length, addr, port)
              if(!socket.isClosed || !socket.isBound)socket.send(datagramPacket)
              //              mediaSink.write(ByteBuffer.wrap(msg.data))
              //              log.debug(s"StreamPuller-$liveId  write success.")
              ctx.self ! SwitchBehavior("pulling", pulling(liveId, parent, pullClient, mediaPlayer, meetingScene, socket, port))
            } catch {
              case ex: Exception =>
                log.warn(s"sink write pulled data error: $ex. Stop StreamPuller-$liveId")
                ctx.self ! StopPull
            }
          } else {
            log.debug(s"StreamPuller-$liveId pull null.")
            ctx.self ! SwitchBehavior("pulling", pulling(liveId, parent, pullClient, mediaPlayer,  meetingScene, socket, port))
          }
          busy(liveId, parent, pullClient)

        case GetLossAndBand =>
          val info = pullClient.getPackageLoss().map(i => i._1 -> PackageLossInfo(i._2.lossScale60, i._2.lossScale10, i._2.lossScale2))
          val bandInfo = pullClient.getBandWidth().map(i => i._1 -> BandWidthInfo(i._2.bandWidth60s, i._2.bandWidth10s, i._2.bandWidth2s))

          //todo
          //          meetingScene.foreach(_.drawPackageLoss(info, bandInfo))
          Behaviors.same

        case StopPull =>
          log.info(s"StreamPuller-$liveId is stopping.")
          timer.startPeriodicTimer(ENSURE_STOP_PULL,StopPull,2000.milliseconds)
          try pullClient.close()
          catch {
            case  e: Exception =>
              log.info(s"StreamPuller-$liveId close error: $e")
          }
          Behaviors.same

        case CloseSuccess =>
          log.info(s"StreamPuller-$liveId stopped.")
          //          outStream.flush()
          //          outStream.close()
          parent ! LiveManager.PullerStopped
          timer.cancel(ENSURE_STOP_PULL)
          Behaviors.stopped

        case msg: StreamStop =>
          log.info(s"Pull stream-${msg.liveId} thread has been closed.")
          parent ! LiveManager.PullerStopped
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("播放中的流已被关闭!")
            //todo
            //            hostScene.foreach(_.listener.shutJoin())
            //            audienceScene.foreach(a => a.listener.quitJoin(a.getRoomInfo.roomId))
          }
          Behaviors.stopped

        case PullStream =>
          Behaviors.same

        case x =>
          log.warn(s"unknown msg in pulling: $x")
          Behaviors.unhandled
      }
    }


  private def busy(
    liveId: String,
    parent: ActorRef[LiveManager.LiveCommand],
    pullClient: PullStreamClient
  )
    (
      implicit stashBuffer: StashBuffer[PullCommand],
      timer: TimerScheduler[PullCommand]
    ): Behavior[PullCommand] =
    Behaviors.receive[PullCommand] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
          Behaviors.stopped

        case StopPull =>
          log.info(s"StreamPuller-$liveId is stopping.")
          try pullClient.close()
          catch {
            case  e: Exception =>
              log.info(s"StreamPuller-$liveId close error: $e")
          }
          Behaviors.same

        case CloseSuccess =>
          log.info(s"StreamPuller-$liveId stopped.")
          parent ! LiveManager.PullerStopped
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }

}
