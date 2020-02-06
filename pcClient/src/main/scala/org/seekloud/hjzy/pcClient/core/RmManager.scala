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
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.Constants.HostStatus
import org.seekloud.hjzy.pcClient.common.{AppSettings, Routes, StageContext}
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

  /*消息定义*/
  sealed trait RmCommand

  final case class GetHomeItems(homeScene: HomeScene, homeController: HomeController) extends RmCommand

  final case class LogInSuccess(userInfo: UserInfo, roomInfo: RoomInfo, getTokenTime: Option[Long] = None) extends RmCommand

  final case object Logout extends RmCommand

  final case object CreateMeetingSuccess extends RmCommand

  final case object JoinMeetingSuccess extends  RmCommand

  final case class GetSender(sender: ActorRef[WsMsgFront]) extends RmCommand

  final case object StopSelf extends RmCommand

  /*主播*/
  final case object HostWsEstablish extends RmCommand




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

      case CreateMeetingSuccess =>
        log.debug(s"create meeting success.")
        val meetingScene = new MeetingScene(stageCtx.getStage)
        val meetingController = new MeetingController(stageCtx, meetingScene, ctx.self)

        def callBack(): Unit = Boot.addToPlatform(meetingScene.changeToggleAction())
        liveManager ! LiveManager.DevicesOn(meetingScene.gc, callBackFunc = Some(callBack))

        ctx.self ! HostWsEstablish
        Boot.addToPlatform{
          if (homeController != null) homeController.get.removeLoading()
          meetingController.showScene()
        }
        switchBehavior(ctx, "hostBehavior", hostBehavior(stageCtx, homeController, meetingScene, meetingController, liveManager, mediaPlayer))

      case JoinMeetingSuccess =>
        log.debug(s"join meeting success.")
        Behaviors.same

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
    hostStatus: Int = HostStatus.LIVE, //0-会议未开始，1-会议进行中
    joinAudience: Option[AudienceInfo] = None
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
        }

        def failureFunc(): Unit = {
          //            liveManager ! LiveManager.DeviceOff
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("连接失败！")
          }
        }

        val url = Routes.linkRoomManager(userInfo.get.userId, userInfo.get.token, roomInfo.map(_.roomId).get)
        buildWebSocket(ctx, url, meetingController, successFunc(), failureFunc())
        Behaviors.same





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
