package org.seekloud.hjzy.pcClient

import akka.actor.typed.{ActorRef, DispatcherSelector}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ActorSystem, Scheduler}
import akka.dispatch.MessageDispatcher
import akka.stream.ActorMaterializer
import akka.util.Timeout
import javafx.application.Platform
import javafx.scene.text.Font
import javafx.stage.Stage
import org.seekloud.hjzy.pcClient.common.StageContext
import org.seekloud.hjzy.pcClient.controller.HomeController
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.core.RmManager.StopSelf
import org.seekloud.hjzy.pcClient.scene.HomeScene
import org.seekloud.hjzy.capture.sdk.DeviceUtil
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


/**
  * Author: zwq
  * Date: 2020/1/16
  * Time: 12:28
  */
object Boot {

  import org.seekloud.hjzy.pcClient.common.AppSettings._

  implicit val system: ActorSystem = ActorSystem("hjzy", config)
  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  val blockingDispatcher: DispatcherSelector = DispatcherSelector.fromConfig("akka.actor.my-blocking-dispatcher")

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val scheduler: Scheduler = system.scheduler
  implicit val timeout: Timeout = Timeout(20 seconds)

//  val netImageProcessor: ActorRef[NetImageProcessor.Command] = system.spawn(NetImageProcessor.create(), "netImageProcessor")


  def addToPlatform(fun: => Unit): Unit = {
    Platform.runLater(() => fun)
  }

}


class Boot extends javafx.application.Application {

  import Boot._

  private[this] val log = LoggerFactory.getLogger(this.getClass)



  override def start(primaryStage: Stage): Unit = {
//    DeviceUtil.init

    val context = new StageContext(primaryStage)

    val rmManager = system.spawn(RmManager.create(context), "rmManager")

//    val loginController = new LoginController(context, rmManager)
//    val editController = new EditController(context,rmManager,primaryStage)

    val homeScene = new HomeScene()
    val homeSceneController = new HomeController(context, homeScene, rmManager)
    rmManager ! RmManager.GetHomeItems(homeScene, homeSceneController)
    homeSceneController.showScene()

//    addToPlatform {
//      homeSceneController.loginByTemp()
//    }

    primaryStage.setOnCloseRequest(event => {
      rmManager ! StopSelf
      println("OnCloseRequest...")
      System.exit(0)
    })

  }

}
