package org.seekloud.hjzy.pcClient.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.hjzy.pcClient.common.{AppSettings, StageContext}
import org.seekloud.hjzy.pcClient.controller.HomeController
import org.seekloud.hjzy.pcClient.scene.HomeScene
import org.seekloud.hjzy.player.sdk.MediaPlayer
import org.slf4j.LoggerFactory

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

  sealed trait RmCommand

  final case class GetHomeItems(homeScene: HomeScene, homeController: HomeController) extends RmCommand

  final case object GoToLive extends RmCommand

  final case object GoToRoomHall extends RmCommand

  final case object Logout extends RmCommand

  final case object StopSelf extends RmCommand


  /*消息定义*/

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
      //        val liveManager = ctx.spawn(LiveManager.create(ctx.self, mediaPlayer), "liveManager")
      idle(stageCtx, mediaPlayer)
    }
  }

  private def idle(
    stageCtx: StageContext,
    mediaPlayer: MediaPlayer,
    homeController: Option[HomeController] = None,
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] = Behaviors.receive[RmCommand] { (ctx, msg) =>
    msg match {
      case msg: GetHomeItems =>
        idle(stageCtx, mediaPlayer)

      case StopSelf =>
        log.info(s"rmManager stopped in idle.")
        Behaviors.stopped

//      case GoToLive =>
//        val hostScene = new HostScene(stageCtx.getStage)
//        val hostController = new HostController(stageCtx, hostScene, ctx.self)
//
//        def callBack(): Unit = Boot.addToPlatform(hostScene.changeToggleAction())
//
//        liveManager ! LiveManager.DevicesOn(hostScene.gc, callBackFunc = Some(callBack))
//        ctx.self ! HostWsEstablish
//        Boot.addToPlatform {
//          if (homeController != null) {
//            homeController.get.removeLoading()
//          }
//          hostController.showScene()
//        }
//        switchBehavior(ctx, "hostBehavior", hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer))
//
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
        log.info(s"logout.")
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








}
