package org.seekloud.hjzy.pcClient.scene

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.image.ImageView
import javafx.scene.layout.{BorderPane, HBox, VBox}
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

    def gotoLogin(
      userName: Option[String] = None,
      pwd: Option[String] = None,
      isToLive: Boolean = false,
      isToWatch: Boolean = false
    )

    def gotoRegister()

    def logout()

    def gotoRoomHall()

    def gotoLive()

    def editInfo()

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

  /*waitingGif*/
  val waitingGif = new ImageView("img/gif/waiting.gif")
  waitingGif.setFitHeight(50)
  waitingGif.setFitWidth(50)
  waitingGif.setLayoutX(width / 2 - 25)
  waitingGif.setLayoutY(height / 2 - 25)

  /*background*/
  val background = new ImageView("img/picture/background.jpg")
  background.setFitHeight(height)
  background.setFitWidth(width)

  /*topArea*/
  val loginBtn = new Button("登录")
  loginBtn.setOnAction(_ => listener.gotoLogin())
  val registerBtn = new Button("注册")
  registerBtn.setOnAction(_ => listener.gotoRegister())
  val topBox = new HBox(20, loginBtn, registerBtn)
  topBox.setPrefSize(width, height*0.2)
  topBox.setAlignment(Pos.CENTER)




  /*middleArea*/
  val joinBtn = new Button("参加会议")
  joinBtn.setOnAction(_ => listener.gotoRoomHall())
  val createBtn = new Button("创建会议")
  createBtn.setOnAction(_ => listener.gotoLive())
  val middleBox = new VBox(30, joinBtn, createBtn)
  middleBox.setPrefSize(width, height*0.6)
  middleBox.setAlignment(Pos.CENTER)


  /*layout*/
  val borderPane = new BorderPane()
  borderPane.setTop(topBox)
  borderPane.setCenter(middleBox)


  group.getChildren.addAll(background, borderPane)

}
