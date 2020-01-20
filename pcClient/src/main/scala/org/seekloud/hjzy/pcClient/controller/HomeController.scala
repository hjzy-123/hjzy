package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import javafx.scene.{Group, Scene}
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.StageContext
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.scene.HomeScene

/**
  * Author: zwq
  * Date: 2020/1/16
  * Time: 12:56
  */
class HomeController(
  context: StageContext,
  homeScene: HomeScene,
//  loginController: LoginController,
//  editController: EditController,
  rmManager: ActorRef[RmManager.RmCommand]) {

  def showScene(): Unit = {
    Boot.addToPlatform(
      context.switchScene(homeScene.getScene, title = "pc客户端-主页")
    )
  }




}
