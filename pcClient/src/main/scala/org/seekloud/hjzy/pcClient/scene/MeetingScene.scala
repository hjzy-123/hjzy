package org.seekloud.hjzy.pcClient.scene

import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{BorderPane, HBox, StackPane, VBox}
import javafx.scene.text.Font
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import org.seekloud.hjzy.pcClient.common.Constants
import org.seekloud.hjzy.pcClient.common.Constants.AppWindow
import org.seekloud.hjzy.pcClient.component.{CanvasBar, CommentBoard, WarningDialog}
import org.seekloud.hjzy.pcClient.core.RmManager
import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2020/2/5
  * Time: 22:45
  */
object MeetingScene {
  trait MeetingSceneListener{

    def startLive()

    def stopLive()

    def changeHost()

    def modifyRoom(roomName: Option[String] = None, roomDes: Option[String] = None)

    def stopSelfImage()

    def stopSelfSound()

    def stopOnesImage()

    def stopOnesSound()

    def fullScreen()

    def exitFullScreen()

    def allowSbSpeak()

    def refuseSbSpeak()

    def stopSbSpeak()

    def kickSbOut()

    def sendComment(comment: String)

    def leaveRoom()


  }

}
class MeetingScene(stage: Stage){
  import MeetingScene._

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private val width = AppWindow.width * 0.9
  private val height = AppWindow.height * 0.75

  val group = new Group()

  private val scene = new Scene(group, width, height)
  scene.getStylesheets.add(
    this.getClass.getClassLoader.getResource("css/meetingSceneCss.css").toExternalForm
  )

  def getScene(isHost: Boolean): Scene = {
    addToMeetingHostBox(meetingHostBox, isHost)
    addToMeetingNameBox(meetingNameBox, isHost)
    addToMeetingDesBox(meetingDesBox, isHost)
    addToSpeakStateBox(speakStateBox, isHost)
    this.scene
  }

  var listener: MeetingSceneListener = _

  def setListener(listener: MeetingSceneListener): Unit = {
    this.listener = listener
  }

  /*background*/
  val background = new ImageView("img/picture/meetingSceneBg.jpg")
  background.setFitHeight(height)
  background.setFitWidth(width)

  /**
    * left Area
    *
    **/

  /*meetingInfo*/
  val leaveImg = new ImageView("img/button/back.png")
  leaveImg.setFitWidth(25)
  leaveImg.setFitHeight(25)
  val leaveBtn = new Button("", leaveImg)
  leaveBtn.getStyleClass.add("confirmBtn")
  leaveBtn.setOnAction(_ => this.listener.leaveRoom())

  val meetingInfoLabel = new Label(s"会议信息")
  meetingInfoLabel.setFont(Font.font(18))

  val roomIdLabel = new Label("房间号:")
  val roomIdValue = new Label(s"${RmManager.meetingRoomInfo.get.roomId}")
  roomIdValue.setMaxWidth(100)
  val roomIdBox = new HBox(5, roomIdLabel, roomIdValue)
  roomIdBox.setAlignment(Pos.CENTER_LEFT)

  val meetingHostLabel = new Label(s"会议主持人:")
  val meetingHostValue = new Label(s"${RmManager.meetingRoomInfo.get.userName}")
  meetingHostValue.setPrefWidth(95)
  val changeHostBtn = new Button(s"变更")
  changeHostBtn.setOnAction(_ => listener.changeHost())
  changeHostBtn.getStyleClass.add("confirmBtn")

  val meetingHostBox = new HBox(5)
  meetingHostBox.setAlignment(Pos.CENTER_LEFT)
  def addToMeetingHostBox(meetingHostBox: HBox, isHost: Boolean): Boolean = {
    meetingHostBox.getChildren.removeAll()
    if(isHost){
      meetingHostBox.getChildren.addAll(meetingHostLabel, meetingHostValue, changeHostBtn)
    } else {
      meetingHostBox.getChildren.addAll(meetingHostLabel, meetingHostValue)
    }
  }

  val meetingNameLabel = new Label(s"会议名称:")
  val meetingNameValue = new Label(s"${RmManager.meetingRoomInfo.get.roomName}")
  meetingNameValue.setMaxWidth(100)
  val meetingNameField = new TextField(s"${RmManager.meetingRoomInfo.get.roomName}")
  meetingNameField.setPrefWidth(160)
  val editMeetingNameBtn = new Button(s"确认")
  editMeetingNameBtn.getStyleClass.add("confirmBtn")
  editMeetingNameBtn.setOnAction(_ => listener.modifyRoom(roomName = Some(meetingNameField.getText)))
  val editMeetingNameBox = new HBox(5, meetingNameField, editMeetingNameBtn)
  editMeetingNameBox.setAlignment(Pos.BOTTOM_LEFT)

  val meetingNameBox = new VBox(10)
  meetingNameBox.setAlignment(Pos.CENTER_LEFT)
  def addToMeetingNameBox(meetingNameBox: VBox, isHost: Boolean): Boolean = {
    meetingNameBox.getChildren.removeAll()
    if(isHost){
      meetingNameBox.getChildren.addAll(meetingNameLabel, editMeetingNameBox)
    } else {
      meetingNameBox.getChildren.addAll(meetingNameLabel, meetingNameValue)
    }
  }

  val meetingDesLabel = new Label(s"会议描述:")
  val meetingDesField  = new TextArea(s"${RmManager.meetingRoomInfo.get.roomDes}")
  meetingDesField.setPrefSize(160, 60)
  meetingDesField.setWrapText(true)
  val meetingDesValue = new TextField(s"${RmManager.meetingRoomInfo.get.roomDes}")
  meetingDesValue.setEditable(false)
  meetingDesValue.setMaxSize(100, 60)
  val editMeetingDesBtn = new Button(s"确认")
  editMeetingDesBtn.getStyleClass.add("confirmBtn")
  editMeetingDesBtn.setOnAction(_ => listener.modifyRoom(roomDes = Some(meetingDesField.getText)))
  val editMeetingDesBox = new HBox(5, meetingDesField, editMeetingDesBtn)
  editMeetingDesBox.setAlignment(Pos.BOTTOM_LEFT)

  val meetingDesBox = new VBox(10)
  meetingDesBox.setAlignment(Pos.CENTER_LEFT)
  def addToMeetingDesBox(meetingDesBox: VBox, isHost: Boolean): Boolean = {
    meetingDesBox.getChildren.removeAll()
    if(isHost){
      meetingDesBox.getChildren.addAll(meetingDesLabel, editMeetingDesBox)
    } else {
      meetingDesBox.getChildren.addAll(meetingDesLabel, meetingDesValue)
    }
  }

  val meetingInfoBox = new VBox(15, leaveBtn, meetingInfoLabel, roomIdBox, meetingHostBox, meetingNameBox, meetingDesBox)
  meetingInfoBox.setPadding(new Insets(10,30,20,30))


//  def genMeetingInfoBox(): VBox = {
//    val isHost: Boolean =
//      if(RmManager.userInfo.get.userId == RmManager.meetingRoomInfo.get.userId) true else false
//
//    val meetingHostBox = if(isHost){
//      new HBox(5, meetingHostLabel, meetingHostValue, changeHostBtn)
//    } else {
//      new HBox(5, meetingHostLabel, meetingHostValue)
//    }
//    meetingHostBox.setAlignment(Pos.CENTER_LEFT)
//
//
//    val meetingNameBox = if(isHost){
//      val vBox = new VBox(5, meetingNameLabel, meetingNameField)
//      val hBox = new HBox(5, vBox, editMeetingNameBtn)
//      hBox.setAlignment(Pos.BOTTOM_LEFT)
//      hBox
//    } else {
//      new HBox(5, meetingNameLabel, meetingNameValue)
//    }
//
//    val meetingDesBox = if(isHost){
//      val vBox = new VBox(5, meetingDesLabel, meetingDesField)
//      val hBox = new HBox(5, vBox, editMeetingDesBtn)
//      hBox.setAlignment(Pos.BOTTOM_LEFT)
//      hBox
//    } else {
//      new HBox(5, meetingDesLabel, meetingDesValue)
//    }
//
//    val meetingInfoBox = new VBox(15, leaveBtn, meetingInfoLabel, roomIdBox, meetingHostBox, meetingNameBox, meetingDesBox)
//    meetingInfoBox.setPadding(new Insets(10,30,20,30))
//    meetingInfoBox
//  }

  /*speakInfo*/
  val speakInfoLabel = new Label(s"发言信息")
  speakInfoLabel.setFont(Font.font(18))

  val speakStateLabel = new Label(s"当前发言者：")
  val speakStateValue = new Label(s"无")
  speakStateValue.setPrefWidth(85)
  val appointSpeakBtn = new Button(s"指派")
  appointSpeakBtn.getStyleClass.add("confirmBtn")

  val speakStateBox = new HBox(5)
  speakStateBox.setAlignment(Pos.CENTER_LEFT)
  def addToSpeakStateBox(speakStateBox: HBox, isHost: Boolean) = {
    speakStateBox.getChildren.removeAll()
    if(isHost){
      speakStateBox.getChildren.addAll(speakStateLabel, speakStateValue, appointSpeakBtn)
    } else {
      speakStateBox.getChildren.addAll(speakStateLabel, speakStateValue)
    }
  }
  val speakInfoBox = new VBox(15, speakInfoLabel, speakStateBox)
  speakInfoBox.setPadding(new Insets(10,30,20,30))

//  def genSpeakInfoBox(): VBox = {
//    val isHost = if(RmManager.userInfo.get.userId == RmManager.meetingRoomInfo.get.userId) true else false
//    val stateBox = if(isHost){
//      new HBox(5, speakStateLabel, speakStateValue, appointSpeakBtn)
//    } else {
//      new HBox(5, speakStateLabel, speakStateValue)
//    }
//    stateBox.setAlignment(Pos.CENTER_LEFT)
//    val speakInfoBox = new VBox(15, speakInfoLabel, stateBox)
//    speakInfoBox.setPadding(new Insets(10,30,20,30))
//    speakInfoBox
//
//  }

  val leftArea = new VBox(20, meetingInfoBox, speakInfoBox)





  /**
    * middle Area
    *
    **/

  /*live control Button*/
  val liveToggleButton = new ToggleButton("")
  liveToggleButton.getStyleClass.add("liveBtn")
  liveToggleButton.setDisable(true)
  Tooltip.install(liveToggleButton, new Tooltip("设备准备中"))
  val meetingStateLabel = new Label(s"会议未开始")
  meetingStateLabel.setFont(Font.font(15))

  val controlBox = new HBox(5)
  controlBox.setAlignment(Pos.CENTER)
  def addToControlBox(controlBox: HBox, isHost: Boolean): Boolean = {
    controlBox.getChildren.removeAll()
    if(isHost){
      controlBox.getChildren.addAll(liveToggleButton, meetingStateLabel)
    } else {
      controlBox.getChildren.addAll(meetingStateLabel)

    }
  }

  def changeToggleAction(): Unit = {
    liveToggleButton.setDisable(false)
    liveToggleButton.setOnAction {
      _ =>
        if (liveToggleButton.isSelected) {
          listener.startLive()
          meetingStateLabel.setText("会议进行中")
          Tooltip.install(liveToggleButton, new Tooltip("点击停止会议"))
        } else {
          listener.stopLive()
          meetingStateLabel.setText("会议未开始")
          Tooltip.install(liveToggleButton, new Tooltip("点击开始会议"))
        }

    }

  }

  /*self canvas*/
  val selfImageCanvas = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
  val selfImageGc: GraphicsContext = selfImageCanvas.getGraphicsContext2D
  val selfImageCanvasBg = new Image("img/picture/background.jpg")
  selfImageGc.drawImage(selfImageCanvasBg, 0, 0, Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)

  /*self liveBar*/
  val selfCanvasBar = new CanvasBar(Constants.DefaultPlayer.width, 40)
  val selfImageToggleBtn: ToggleButton = selfCanvasBar.imageToggleButton
  val selfSoundToggleBtn: ToggleButton = selfCanvasBar.soundToggleButton
  val selfLiveBar: HBox = selfCanvasBar.liveBarBox

  selfImageToggleBtn.setOnAction {
    _ =>

  }

  selfSoundToggleBtn.setOnAction {
    _ =>

  }

  /*self livePane*/
  val selfLivePane = new StackPane(selfImageCanvas)

  selfLivePane.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
    selfLivePane.setAlignment(Pos.BOTTOM_RIGHT)
    selfLivePane.getChildren.add(selfLiveBar)
  })

  selfLivePane.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
    selfLivePane.setAlignment(Pos.BOTTOM_RIGHT)
    selfLivePane.getChildren.remove(selfLiveBar)
  })


  /*others' canvas*/
  val canvasMap: Map[Int, (Canvas, GraphicsContext)] = List(1,2,3,4,5,6).map{ i =>
    val imageCanvas = new Canvas(Constants.DefaultPlayer.width/3, Constants.DefaultPlayer.height/3)
    val imageGc: GraphicsContext = imageCanvas.getGraphicsContext2D
    val imageCanvasBg = new Image("img/picture/background.jpg")
    imageGc.drawImage(imageCanvasBg, 0, 0, Constants.DefaultPlayer.width/3, Constants.DefaultPlayer.height/3)

    i -> (imageCanvas, imageGc)
  }.toMap

  /*others' nameLabel*/
  val nameLabelMap: Map[Int, Label] = List(1,2,3,4,5,6).map{ i =>
    val nameLabel = new Label("")
    nameLabel.setPrefSize(Constants.DefaultPlayer.width/3, 30)
    nameLabel.setFont(Font.font(15))
    nameLabel.setAlignment(Pos.BOTTOM_CENTER)
    i -> nameLabel
  }.toMap

//  /*others' liveBar*/
//  val liveBarMap: List[(Int, HBox)] = List(1,2,3,4,5,6).map{ i =>
//    val canvasBar = new CanvasBar(Constants.DefaultPlayer.width/3, 40)
//    val imageToggleBtn: ToggleButton = canvasBar.imageToggleButton
//    val soundToggleBtn: ToggleButton = canvasBar.soundToggleButton
//    val liveBar: HBox = canvasBar.liveBarBox
//
//    imageToggleBtn.setOnAction {
//      _ =>
//
//    }
//
//    soundToggleBtn.setOnAction {
//      _ =>
//
//    }
//    i -> liveBar
//  }

  val box1 = new HBox(
    new VBox(nameLabelMap(1),canvasMap(1)._1),
    new VBox(nameLabelMap(2),canvasMap(2)._1),
    new VBox(nameLabelMap(3),canvasMap(3)._1)
  )
  val box2 = new HBox(
    new VBox(nameLabelMap(4),canvasMap(4)._1),
    new VBox(nameLabelMap(5),canvasMap(5)._1),
    new VBox(nameLabelMap(6),canvasMap(6)._1)
  )
  val othersCanvasBox = new VBox(box1, box2)
  val canvasBox = new VBox(5, selfLivePane, othersCanvasBox)

  val middleBox = new VBox(10, controlBox, canvasBox)
  middleBox.setAlignment(Pos.TOP_CENTER)
  middleBox.setPadding(new Insets(20,0,0,0))

//  def genMiddleBox(): VBox = {
//    val isHost = if(RmManager.userInfo.get.userId == RmManager.meetingRoomInfo.get.userId) true else false
//    val topBox = if(isHost){
//      new HBox(5,liveToggleButton, meetingStateLabel)
//    } else {
//      new HBox(meetingStateLabel)
//    }
//    topBox.setAlignment(Pos.CENTER)
//
//    val middleBox = new VBox(10, topBox, canvasBox)
//    middleBox.setAlignment(Pos.TOP_CENTER)
//    middleBox.setPadding(new Insets(20,0,0,0))
//    middleBox
//  }




  /**
    * right Area
    *
    **/

  /*commentArea*/
  val commentLabel = new Label(s"消息区")
  commentLabel.setFont(Font.font(18))

//  val commentArea = new TextArea()
//  commentArea.setPrefSize(200, 500)
//  commentArea.setEditable(false)
//  commentArea.setWrapText(true)
  val commentBoard = new CommentBoard(250, 500)
  val commentArea: VBox = commentBoard.commentBoard

  val commentBox = new VBox(15, commentLabel, commentArea)
  commentBox.setAlignment(Pos.TOP_LEFT)

  /*writeArea*/
  val writeField = new TextField()
  writeField.setPrefWidth(200)
  writeField.setPromptText("输入你的留言~")

  val sendBtn = new Button(s"发送")
  sendBtn.getStyleClass.add("confirmBtn")
  sendBtn.setOnAction{_ =>
    if(writeField.getText != "" && writeField.getText != null){
      listener.sendComment(writeField.getText)
      writeField.clear()
    } else {
      WarningDialog.initWarningDialog("请输入评论！")
    }
  }
  val writeBox = new HBox(5, writeField, sendBtn)
  writeBox.setAlignment(Pos.CENTER_LEFT)

  val rightArea = new VBox(15, commentBox, writeBox)
  rightArea.setPadding(new Insets(23,30,20,30))




  /**
    * Layout
    *
    **/
  val borderPane = new BorderPane()
  borderPane.setLeft(leftArea)
  borderPane.setCenter(middleBox)
  borderPane.setRight(rightArea)

  group.getChildren.addAll(background, borderPane)


  /**
    * refresh Func
    *
    **/
  def refreshScene(isHost: Boolean): Unit = {
    addToMeetingHostBox(meetingHostBox, isHost)
    addToMeetingNameBox(meetingNameBox, isHost)
    addToMeetingDesBox(meetingDesBox, isHost)
    addToSpeakStateBox(speakStateBox, isHost)
    addToControlBox(controlBox, isHost)

  }


}
