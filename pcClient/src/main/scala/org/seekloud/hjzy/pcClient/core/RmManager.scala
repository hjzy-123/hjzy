package org.seekloud.hjzy.pcClient.core

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.util.{ByteString, ByteStringBuilder}
import com.sk.hjzy.protocol.ptcl.CommonInfo.AudienceInfo
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{RoomInfo, UserInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.Constants.MeetingStatus
import org.seekloud.hjzy.pcClient.common.{AppSettings, Ids, Routes, StageContext}
import org.seekloud.hjzy.pcClient.component.WarningDialog
import org.seekloud.hjzy.pcClient.controller.{HomeController, MeetingController}
import org.seekloud.hjzy.pcClient.core.stream.LiveManager
import org.seekloud.hjzy.pcClient.scene.{HomeScene, MeetingScene}
import org.seekloud.hjzy.player.sdk.MediaPlayer
import org.slf4j.LoggerFactory
import org.seekloud.hjzy.pcClient.Boot.{executor, materializer, scheduler, system, timeout}
import org.seekloud.byteobject.ByteObject._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import concurrent.duration._


/**
  * Author: zwq
  * Date: 2020/1/16
  * Time: 12:43
  * 与roomManager的交互管理
  */
object RmManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  var userInfo: Option[UserInfo] = None
  var roomInfo: Option[RoomInfo] = None
  var meetingRoomInfo: Option[RoomInfo] = None

  /*消息定义*/
  sealed trait RmCommand

  final case class GetHomeItems(homeScene: HomeScene, homeController: HomeController) extends RmCommand

  final case class LogInSuccess(userInfo: UserInfo, roomInfo: RoomInfo, getTokenTime: Option[Long] = None) extends RmCommand

  final case object Logout extends RmCommand

  final case class CreateMeetingSuccess(roomInfo: Option[RoomInfo]) extends RmCommand

  final case class JoinMeetingSuccess(roomInfo: Option[RoomInfo]) extends  RmCommand

  final case class GetSender(sender: ActorRef[WsMsgFront]) extends RmCommand

  final case object StopSelf extends RmCommand

  final case object LeaveRoom extends RmCommand

  final case class SendComment(userId: Long, roomId: Long, comment: String) extends RmCommand

  final case object HeartBeat extends RmCommand

  final case object PingTimeOut extends RmCommand

  final case object PullerStopped extends RmCommand

  /*主持人*/
  final case object HostWsEstablish extends RmCommand

  final case class ModifyRoom(meetingName: Option[String], meetingDes: Option[String]) extends RmCommand

  final case class ModifyRoomFailed(previousName: String, previousDes: String) extends RmCommand

  final case class TurnToAudience(newHostId: Long) extends RmCommand

  final case class KickSbOut(userId: Long) extends RmCommand

  /*普通观众*/
  final case object AudienceWsEstablish extends RmCommand

  final case object HostClosedRoom extends RmCommand

  final case class RoomModified(newName: String, newDes: String) extends RmCommand

  final case object TurnToHost extends RmCommand






  private[this] def switchBehavior(
    ctx: ActorContext[RmCommand], behaviorName: String, behavior: Behavior[RmCommand])
    (implicit stashBuffer: StashBuffer[RmCommand]) = {

    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    stashBuffer.unstashAll(ctx, behavior)
  }

  def create(stageCtx: StageContext): Behavior[RmCommand] = Behaviors.setup[RmCommand] { ctx =>
    log.info(s"RmManager is starting...")
    implicit val stashBuffer: StashBuffer[RmCommand] = StashBuffer[RmCommand](Int.MaxValue)
    Behaviors.withTimers[RmCommand] { implicit timer =>
      val mediaPlayer = new MediaPlayer()
      mediaPlayer.init(isDebug = AppSettings.playerDebug, needTimestamp = AppSettings.needTimestamp)
      val liveManager = ctx.spawn(LiveManager.create(ctx.self, mediaPlayer), "liveManager")
      idle(stageCtx, liveManager, mediaPlayer)
    }
  }

  private def idle(
    stageCtx: StageContext,
    liveManager: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    homeController: Option[HomeController] = None,
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] = Behaviors.receive[RmCommand] { (ctx, msg) =>
    msg match {
      case msg: GetHomeItems =>
        idle(stageCtx, liveManager, mediaPlayer, Some(msg.homeController))

      case StopSelf =>
        log.info(s"rmManager stopped in idle.")
        Behaviors.stopped

      case LogInSuccess(userInfo, roomInfo, getTokenTime) =>
        log.info(s"login success.")
        this.userInfo = Some(userInfo)
        this.roomInfo = Some(roomInfo)
        homeController.get.showScene()
        Behaviors.same

      case CreateMeetingSuccess(roomInfo) =>
        log.debug(s"create meeting success.")
        this.meetingRoomInfo = roomInfo
        val meetingScene = new MeetingScene(stageCtx.getStage)
        val meetingController = new MeetingController(stageCtx, meetingScene, ctx.self)

        def callBack(): Unit = Boot.addToPlatform(meetingScene.changeToggleAction())
        liveManager ! LiveManager.DevicesOn(meetingScene.selfImageGc, callBackFunc = Some(callBack))

        ctx.self ! HostWsEstablish
        Boot.addToPlatform{
          if (homeController != null) homeController.get.removeLoading()
          meetingController.showScene(true)
        }
        switchBehavior(ctx, "hostBehavior", hostBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer))

      case JoinMeetingSuccess(roomInfo) =>
        log.debug(s"join meeting success.")
        this.meetingRoomInfo = roomInfo
        val meetingScene = new MeetingScene(stageCtx.getStage)
        val meetingController = new MeetingController(stageCtx, meetingScene, ctx.self)

        def callBack(): Unit = Boot.addToPlatform(meetingScene.changeToggleAction())
        liveManager ! LiveManager.DevicesOn(meetingScene.selfImageGc, callBackFunc = Some(callBack))

        ctx.self ! AudienceWsEstablish
        Boot.addToPlatform{
          if (homeController != null) homeController.get.removeLoading()
          meetingController.showScene(false)
        }
        switchBehavior(ctx, "audienceBehavior", audienceBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer))


//      case GoToRoomHall =>
//        val roomScene = new RoomScene()
//        val roomController = new RoomController(stageCtx, roomScene, ctx.self)
//        Boot.addToPlatform {
//          if (homeController != null) {
//            homeController.get.removeLoading()
//          }
//          roomController.showScene()
//        }
//        idle(stageCtx, liveManager, mediaPlayer, homeController, Some(roomController))


      case Logout =>
        log.info(s"logout success.")
        this.roomInfo = None
        this.userInfo = None
        homeController.get.showScene()
        Behaviors.same

      case x =>
        log.warn(s"unknown msg in idle: $x")
        stashBuffer.stash(x)
        Behaviors.same
    }
  }


  private def hostBehavior(
    stageCtx: StageContext,
    homeController: Option[HomeController] = None,
    meetingScene: MeetingScene,
    meetingController: MeetingController,
    liveManager: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    sender: Option[ActorRef[WsMsgFront]] = None,
    meetingStatus: Int = MeetingStatus.UNLIVE, //0-会议未开始，1-会议进行中
    joinAudienceList: Option[List[AudienceInfo]] = None   //房间内直播中的除自己以外的人
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] = Behaviors.receive[RmCommand]{ (ctx, msg) =>
    msg match{
      case HostWsEstablish =>
        //与roomManager建立ws
        assert(userInfo.nonEmpty && roomInfo.nonEmpty)

        def successFunc(): Unit = {
          //            hostScene.allowConnect()
          //            Boot.addToPlatform {
          //              hostController.showScene()
          //            }
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("webSocket连接成功！")
          }
        }

        def failureFunc(): Unit = {
          //            liveManager ! LiveManager.DeviceOff
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("webSocket连接失败！")
          }
        }

        val url = Routes.linkRoomManager(userInfo.get.userId, userInfo.get.token, roomInfo.map(_.roomId).get, meetingRoomInfo.get.userId)
        buildWebSocket(ctx, url, meetingController, successFunc(), failureFunc())
        Behaviors.same

      case msg: GetSender =>
        hostBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer, Some(msg.sender))


      case HeartBeat =>
        sender.foreach(_ ! PingPackage)
        timer.cancel(PingTimeOut)
        timer.startSingleTimer(PingTimeOut, PingTimeOut, 30.seconds)
        Behaviors.same


      case PingTimeOut =>
        log.info(s"lose webSocket connection with roomManager!")
        //ws断了
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("webSocket连接断开！")
        }
        Behaviors.same

      case ModifyRoom(meetingName, meetingDes) =>
        log.info(s"rcv ModifyRoom from meetingScene: name == $meetingName, des == $meetingDes")
        if(meetingName.nonEmpty){
          this.meetingRoomInfo = meetingRoomInfo.map(_.copy(roomName = meetingName.get))
        }
        if(meetingDes.nonEmpty){
          this.meetingRoomInfo = meetingRoomInfo.map(_.copy(roomDes = meetingDes.get))
        }
        sender.foreach(_ ! ModifyRoomInfo(meetingName, meetingDes))
        Behaviors.same

      case ModifyRoomFailed(previousName, previousDes) =>
        this.meetingRoomInfo = meetingRoomInfo.map(_.copy(roomName = previousName))
        this.meetingRoomInfo = meetingRoomInfo.map(_.copy(roomDes = previousDes))
        Behaviors.same

      case LeaveRoom =>
        log.info(s"host back to home.")
        timer.cancel(HeartBeat)
        timer.cancel(PingTimeOut)
        sender.foreach(_ ! CompleteMsgClient)
        if (meetingStatus == MeetingStatus.LIVE) {
          joinAudienceList.foreach{ audList =>
            audList.foreach{ audInfo =>
              val playId = Ids.getPlayId(this.meetingRoomInfo.get.roomId, audInfo.userId)
//              mediaPlayer.stop(playId, meetingScene.resetBack)
              mediaPlayer.stop(playId, () => ())
            }
          }
          liveManager ! LiveManager.StopPull
          liveManager ! LiveManager.StopPush
        }
        liveManager ! LiveManager.DeviceOff
        Boot.addToPlatform {
//          meetingScene.stopPackageLoss()
          homeController.foreach(_.showScene())
        }
//        meetingScene.stopPackageLoss()
        System.gc()
        switchBehavior(ctx, "idle", idle(stageCtx, liveManager, mediaPlayer, homeController))

      case SendComment(userId, roomId, comment) =>
        log.info(s"rcv SendComment from meetingScene.")
        sender.foreach(_ ! Comment(userId, roomId, comment))
        Behaviors.same

      case TurnToAudience(newHostId) =>
        log.info(s"rcv TurnToAudience from meetingScene: newHostId == $newHostId")
        sender.foreach(_ ! changeHost(newHostId))
        this.meetingRoomInfo = meetingRoomInfo.map(_.copy(userId = newHostId))
        Boot.addToPlatform{
          meetingScene.refreshScene(false)
        }
        switchBehavior(ctx, "audienceBehavior", audienceBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer))


      case StopSelf =>
        log.info(s"rmManager stopped in host.")
        Behaviors.stopped

      case x =>
        log.warn(s"unknown msg in hostBehavior: $x")
        stashBuffer.stash(x)
        Behaviors.same
    }

  }


  private def audienceBehavior(
    stageCtx: StageContext,
    homeController: Option[HomeController] = None,
    meetingScene: MeetingScene,
    meetingController: MeetingController,
    liveManager: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    sender: Option[ActorRef[WsMsgFront]] = None,
    meetingStatus: Int = MeetingStatus.UNLIVE, //0-会议未开始，1-会议进行中
    joinAudienceList: Option[List[AudienceInfo]] = None
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] = Behaviors.receive[RmCommand]{ (ctx, msg) =>
    msg match{
      case AudienceWsEstablish =>
        //与roomManager建立ws
        assert(userInfo.nonEmpty && roomInfo.nonEmpty)

        def successFunc(): Unit = {
          //            hostScene.allowConnect()
          //            Boot.addToPlatform {
          //              hostController.showScene()
          //            }
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("webSocket连接成功！")
          }
        }

        def failureFunc(): Unit = {
          //            liveManager ! LiveManager.DeviceOff
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("webSocket连接失败！")
          }
        }

        val url = Routes.linkRoomManager(userInfo.get.userId, userInfo.get.token, meetingRoomInfo.map(_.roomId).get, meetingRoomInfo.get.userId)
        buildWebSocket(ctx, url, meetingController, successFunc(), failureFunc())
        Behaviors.same

      case msg: GetSender =>
        audienceBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer, Some(msg.sender))


      case HeartBeat =>
        sender.foreach(_ ! PingPackage)
        timer.cancel(PingTimeOut)
        timer.startSingleTimer(PingTimeOut, PingTimeOut, 30.seconds)
        Behaviors.same


      case PingTimeOut =>
        log.info(s"lose webSocket connection with roomManager!")
        //ws断了
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("webSocket连接断开！")
        }
        Behaviors.same

      case LeaveRoom =>
        log.debug(s"audience back to home.")
        timer.cancel(HeartBeat)
        timer.cancel(PingTimeOut)
        sender.foreach(_ ! CompleteMsgClient)

        if(meetingStatus == MeetingStatus.LIVE){
          assert(userInfo.nonEmpty)
          val userId = userInfo.get.userId
          liveManager ! LiveManager.StopPull

          joinAudienceList.foreach{ audList =>
            audList.foreach{ audInfo =>
              val playId = Ids.getPlayId(this.meetingRoomInfo.get.roomId, audInfo.userId)
//              mediaPlayer.stop(playId, meetingScene.resetBack)
              mediaPlayer.stop(playId, () => ())
            }
          }
          liveManager ! LiveManager.StopPush
        }

        liveManager ! LiveManager.DeviceOff

        Boot.addToPlatform {
//          meetingScene.stopPackageLoss()
          homeController.foreach {
            r =>
              r.showScene()
          }
        }
//        meetingScene.stopPackageLoss()
//        meetingScene.finalize()
        System.gc()
        switchBehavior(ctx, "idle", idle(stageCtx, liveManager, mediaPlayer, homeController))


      case HostClosedRoom =>
        log.info("host close room.")
        timer.cancel(HeartBeat)
        timer.cancel(PingTimeOut)
        sender.foreach(_ ! CompleteMsgClient)

        if(meetingStatus == MeetingStatus.LIVE){
          assert(userInfo.nonEmpty)
          val userId = userInfo.get.userId
          liveManager ! LiveManager.StopPull

          joinAudienceList.foreach{ audList =>
            audList.foreach{ audInfo =>
              val playId = Ids.getPlayId(this.meetingRoomInfo.get.roomId, audInfo.userId)
              //              mediaPlayer.stop(playId, meetingScene.resetBack)
              mediaPlayer.stop(playId, () => ())
            }
          }

          liveManager ! LiveManager.StopPush
        }

        liveManager ! LiveManager.DeviceOff

        Boot.addToPlatform {
          //          meetingScene.stopPackageLoss()
          homeController.foreach(_.showScene())
          WarningDialog.initWarningDialog("主持人离开，会议结束，房间已关闭！")
        }
        //        meetingScene.stopPackageLoss()
        //        meetingScene.finalize()
        System.gc()
        switchBehavior(ctx, "idle", idle(stageCtx, liveManager, mediaPlayer, homeController))


      case SendComment(userId, roomId, comment) =>
        log.info(s"rcv SendComment from meetingScene.")
        sender.foreach(_ ! Comment(userId, roomId, comment))
        Behaviors.same


      case RoomModified(newName, newDes) =>
        this.meetingRoomInfo = meetingRoomInfo.map(_.copy(roomName = newName))
        this.meetingRoomInfo = meetingRoomInfo.map(_.copy(roomDes = newDes))
        Behaviors.same

      case TurnToHost =>
        log.info(s"rcv TurnToHost from meetingScene")
        this.meetingRoomInfo = meetingRoomInfo.map(_.copy(userId = userInfo.get.userId))
        Boot.addToPlatform{
          meetingScene.refreshScene(true)
        }
        switchBehavior(ctx, "hostBehavior", hostBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer))


      case StopSelf =>
        log.info(s"rmManager stopped in audience.")
        Behaviors.stopped

      case x =>
        log.warn(s"unknown msg in hostBehavior: $x")
        stashBuffer.stash(x)
        Behaviors.same
    }

  }


  def buildWebSocket(
    ctx: ActorContext[RmCommand],
    url: String,
    controller: MeetingController,
    successFunc: => Unit,
    failureFunc: => Unit)(
    implicit timer: TimerScheduler[RmCommand]
  ): Unit = {
    log.debug(s"build ws with roomManager: $url")
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
    val source = getSource(ctx.self)

    val sink = getRMSink(controller = Some(controller))

    val (stream, response) =
      source
        .viaMat(webSocketFlow)(Keep.both)
        .toMat(sink)(Keep.left)
        .run()
    val connected = response.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        ctx.self ! GetSender(stream)
        successFunc
        Future.successful(s"link room manager success.")
      } else {
        failureFunc
        log.error(s"连接ws返回：${upgrade.response.status}")
        throw new RuntimeException(s"link room manager failed: ${upgrade.response.status}")
      }
    } //链接建立时
    connected.onComplete(i => log.info(i.toString))
  }


  def getSource(rmManager: ActorRef[RmCommand]): Source[BinaryMessage.Strict, ActorRef[WsMsgFront]] =
    ActorSource.actorRef[WsMsgFront](
      completionMatcher = {
        case CompleteMsgClient =>
          log.info("disconnected from room manager.")
      },
      failureMatcher = {
        case FailMsgClient(ex) ⇒
          log.error(s"ws failed: $ex")
          ex
      },
      bufferSize = 8,
      overflowStrategy = OverflowStrategy.fail
    ).collect {
      case message: WsMsgClient =>
        //println(message)
        val sendBuffer = new MiddleBufferInJvm(409600)
        BinaryMessage.Strict(ByteString(
          message.fillMiddleBuffer(sendBuffer).result()
        ))
    }

  def getRMSink(
    controller: Option[MeetingController] = None
//    hController: Option[HostController] = None,
//    aController: Option[AudienceController] = None
  )(
    implicit timer: TimerScheduler[RmCommand]
  ): Sink[Message, Future[Done]] = {
    Sink.foreach[Message] {
      case TextMessage.Strict(msg) =>
        controller.foreach(_.wsMessageHandle(TextMsg(msg)))

      case BinaryMessage.Strict(bMsg) =>
        val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
        val message = bytesDecode[WsMsgRm](buffer) match {
          case Right(rst) => rst
          case Left(_) => DecodeError
        }

        controller.foreach(_.wsMessageHandle(message))


      case msg: BinaryMessage.Streamed =>
        val futureMsg = msg.dataStream.runFold(new ByteStringBuilder().result()) {
          case (s, str) => s.++(str)
        }
        futureMsg.map { bMsg =>
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val message = bytesDecode[WsMsgRm](buffer) match {
            case Right(rst) => rst
            case Left(_) => DecodeError
          }
          controller.foreach(_.wsMessageHandle(message))
        }


      case _ => //do nothing

    }
  }



}
