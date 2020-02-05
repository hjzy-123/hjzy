package org.seekloud.hjzy.pcClient.scene

import javafx.geometry.Pos
import javafx.scene.control.{Button, Label}
import javafx.scene.image.ImageView
import javafx.scene.layout.{BorderPane, HBox, StackPane, VBox}
import javafx.scene.{Group, Scene}
import org.seekloud.hjzy.pcClient.common.Constants._
import org.seekloud.hjzy.pcClient.core.RmManager
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
  scene.getStylesheets.add(
    this.getClass.getClassLoader.getResource("css/homeSceneCss.css").toExternalForm
  )

  def getScene: Scene = {
    group.getChildren.remove(2,4)
    group.getChildren.addAll(genLeftArea(),genRightArea())
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

  /*leftArea*/
  def genLeftArea(): VBox = {
    val leftBox = new VBox(10)
    leftBox.setAlignment(Pos.CENTER)
    leftBox.setLayoutX(width*0.3 - 75)
    leftBox.setLayoutY(height*0.3 - 32)
    if(RmManager.userInfo.isEmpty){
      val loginPic = new ImageView("img/button/login.png")
      loginPic.setFitHeight(55)
      loginPic.setFitWidth(150)
      val loginBtn = new Button("", loginPic)
      loginBtn.getStyleClass.add("leftBtn")
      loginBtn.setOnAction(_ => listener.gotoLogin())

      val registerPic = new ImageView("img/button/register.png")
      registerPic.setFitHeight(55)
      registerPic.setFitWidth(150)
      val registerBtn = new Button("", registerPic)
      registerBtn.getStyleClass.add("leftBtn")
      registerBtn.setOnAction(_ => listener.gotoRegister())

      leftBox.getChildren.addAll(loginBtn, registerBtn)
    }

    leftBox
  }


  /*rightArea*/
  def genRightArea(): VBox = {
    val rightBox = new VBox(10)
    rightBox.setAlignment(Pos.CENTER)
    rightBox.setLayoutX(width*0.67 - 75)
    rightBox.setLayoutY(height*0.7 - 32)

    if(RmManager.userInfo.nonEmpty){
      val userName = RmManager.userInfo.get.userName
      val nameLabel = new Label(userName)
      nameLabel.setStyle("-fx-font: 20 KaiTi;-fx-fill: #141518")
      val rightStickyPic = new ImageView("img/button/rightSticky.png")
      rightStickyPic.setFitHeight(50)
      rightStickyPic.setFitWidth(150)
      val nameStackPane = new StackPane(rightStickyPic, nameLabel)
      nameStackPane.setAlignment(Pos.CENTER)
      nameStackPane.getStyleClass.add("nameStackPane")

      val logoutPic = new ImageView("img/button/logout.png")
      logoutPic.setFitHeight(50)
      logoutPic.setFitWidth(150)
      val logoutBtn = new Button("", logoutPic)
      logoutBtn.getStyleClass.add("leftBtn")
      logoutBtn.setOnAction(_ => listener.logout())

      rightBox.getChildren.addAll(nameStackPane, logoutBtn)
    }

    rightBox

  }


  /*middleArea*/
  val joinMeeting = new ImageView("img/button/joinMeeting.png")
  joinMeeting.setFitHeight(150)
  joinMeeting.setFitWidth(180)
  val joinBtn = new Button("", joinMeeting)
  joinBtn.getStyleClass.add("middleBtn")
  joinBtn.setOnAction(_ => listener.gotoRoomHall())

  val createMeeting = new ImageView("img/button/createMeeting.png")
  createMeeting.setFitHeight(150)
  createMeeting.setFitWidth(180)
  val createBtn = new Button("", createMeeting)
  createBtn.getStyleClass.add("middleBtn")
  createBtn.setOnAction(_ => listener.gotoLive())

  val middleBox = new VBox(10, joinBtn, createBtn)
  middleBox.setAlignment(Pos.CENTER)
  middleBox.setLayoutX(width*0.49 - 90)
  middleBox.setLayoutY(height*0.45 - 80)

  group.getChildren.addAll(background, middleBox, genLeftArea(), genRightArea())

}
