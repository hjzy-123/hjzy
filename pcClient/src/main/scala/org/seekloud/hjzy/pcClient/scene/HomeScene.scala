package org.seekloud.hjzy.pcClient.scene

import javafx.scene.control.Button
import javafx.scene.image.ImageView
import javafx.scene.{Group, Scene}
import org.seekloud.hjzy.pcClient.common.Constants._
import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2020/1/16
  * Time: 12:19
  * 首页
  */
object HomeScene{

  trait HomeSceneListener{

  }

}

class HomeScene {
  import HomeScene._

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private val width = AppWindow.width * 0.9
  private val height = AppWindow.height * 0.75

  val group = new Group()

  private val scene = new Scene(group, width, height)
//  scene.getStylesheets.add(
//    this.getClass.getClassLoader.getResource("css/common.css").toExternalForm
//  )

  def getScene: Scene = {
    this.scene
  }

  var listener: HomeSceneListener = _

  def setListener(listener: HomeSceneListener): Unit = {
    this.listener = listener
  }

  /*background*/
  val background = new ImageView("img/picture/background.jpg")
  background.setFitHeight(height)
  background.setFitWidth(width)

  /*topArea*/
  val loginBtn = new Button("登录")
  val registerBtn = new Button("注册")





  /*middleArea*/



  group.getChildren.addAll(background)

}
