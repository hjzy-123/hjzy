package org.seekloud.hjzy.pcClient.scene

import javafx.beans.property.{ObjectProperty, SimpleObjectProperty, SimpleStringProperty, StringProperty}
import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.control._
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.effect.Glow
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{BorderPane, HBox, StackPane, VBox}
import javafx.scene.text.Font
import javafx.scene.{Group, Node, Scene}
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

  case class ApplySpeakListInfo(
    userInfo: StringProperty,
    agreeBtn: ObjectProperty[Button],
    refuseBtn: ObjectProperty[Button]
  ) {
    def getUserInfo: String = userInfo.get()

    def setUserInfo(info: String): Unit = userInfo.set(info)

    def getAgreeBtn: Button = agreeBtn.get()

    def setAgreeBtn(btn: Button): Unit = agreeBtn.set(btn)

    def getRefuseBtn: Button = refuseBtn.get()

    def setRefuseBtn(btn: Button): Unit = refuseBtn.set(btn)

  }

  trait MeetingSceneListener{

    def startLive()

    def stopLive()

    def changeHost()

    def modifyRoom(roomName: Option[String] = None, roomDes: Option[String] = None)

    def controlSelfImage(targetStatus: Int) //1 -> open, -1-> close

    def controlSelfSound(targetStatus: Int) //1 -> open, -1-> close

    def controlOnesImage(orderNum: Int, targetStatus: Int) //1 -> open, -1-> close

    def controlOnesSound(orderNum: Int, targetStatus: Int) //1 -> open, -1-> close

    def fullScreen()

    def exitFullScreen()

    def applyForSpeak()

    def handleSpeakApply(userId: Long, userName: String, accept: Boolean, newRequest: ApplySpeakListInfo)

    def appointSbSpeak()

    def stopSbSpeak(userId: Long)

    def kickSbOut(canvasId: Int)

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
    addToMeetingHostBox(isHost)
    addToMeetingNameBox(isHost)
    addToMeetingDesBox(isHost)
    addToSpeakStateBox(isHost)
    addToControlBox(isHost)
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
  def addToMeetingHostBox(isHost: Boolean): Boolean = {
    meetingHostBox.getChildren.clear()
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
  def addToMeetingNameBox(isHost: Boolean): Boolean = {
    meetingNameBox.getChildren.clear()
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
  def addToMeetingDesBox(isHost: Boolean): Boolean = {
    meetingDesBox.getChildren.clear()
    if(isHost){
      meetingDesBox.getChildren.addAll(meetingDesLabel, editMeetingDesBox)
    } else {
      meetingDesBox.getChildren.addAll(meetingDesLabel, meetingDesValue)
    }
  }

  val meetingInfoBox = new VBox(15, leaveBtn, meetingInfoLabel, roomIdBox, meetingHostBox, meetingNameBox, meetingDesBox)
  meetingInfoBox.setPadding(new Insets(10,30,20,30))



  /*speakInfo*/
  val speakInfoLabel = new Label(s"发言信息")
  speakInfoLabel.setFont(Font.font(18))

  val speakStateLabel = new Label(s"当前发言者：")
  val speakStateValue = new Label(s"无")
  speakStateValue.setPrefWidth(85)

  val controlSpeakBtn = new Button(s"指派")
  controlSpeakBtn.getStyleClass.add("confirmBtn")
  controlSpeakBtn.setOnAction(_ => listener.appointSbSpeak())

  def editControlSpeakBtn(toAppoint: Boolean = false, toStop: Boolean = false, userId: Option[Long] = None) = {
    if(toAppoint){
      controlSpeakBtn.setText(s"指派")
      controlSpeakBtn.setOnAction(_ => listener.appointSbSpeak())
    }
    if(toStop && userId.nonEmpty){
      controlSpeakBtn.setText(s"结束")
      controlSpeakBtn.setOnAction(_ => listener.stopSbSpeak(userId.get))
    }
  }

  val applyForSpeakBtn = new Button(s"申请")
  applyForSpeakBtn.getStyleClass.add("confirmBtn")
  applyForSpeakBtn.setOnAction(_ => listener.applyForSpeak())

  val speakStateBox = new HBox(5)
  speakStateBox.setAlignment(Pos.CENTER_LEFT)
  def addToSpeakStateBox(isHost: Boolean): Boolean = {
    speakStateBox.getChildren.clear()
    if(isHost){
      speakStateBox.getChildren.addAll(speakStateLabel, speakStateValue, controlSpeakBtn)
    } else {
      speakStateBox.getChildren.addAll(speakStateLabel, speakStateValue, applyForSpeakBtn)
    }
  }

  val applySpeakTable = new TableView[ApplySpeakListInfo]()
  applySpeakTable.getStyleClass.add("table-view")
  val audObservableList: ObservableList[ApplySpeakListInfo] = FXCollections.observableArrayList()

  val userInfoCol = new TableColumn[ApplySpeakListInfo, String]("申请用户")
  userInfoCol.setPrefWidth(width * 0.15)
  userInfoCol.setCellValueFactory(new PropertyValueFactory[ApplySpeakListInfo, String]("userInfo"))

  val agreeBtnCol = new TableColumn[ApplySpeakListInfo, Button]("同意")
  agreeBtnCol.setCellValueFactory(new PropertyValueFactory[ApplySpeakListInfo, Button]("agreeBtn"))
  agreeBtnCol.setPrefWidth(width * 0.05)

  val refuseBtnCol = new TableColumn[ApplySpeakListInfo, Button]("拒绝")
  refuseBtnCol.setCellValueFactory(new PropertyValueFactory[ApplySpeakListInfo, Button]("refuseBtn"))
  refuseBtnCol.setPrefWidth(width * 0.05)

  applySpeakTable.setItems(audObservableList)
  applySpeakTable.getColumns.addAll(userInfoCol, agreeBtnCol, refuseBtnCol)
  applySpeakTable.setPrefHeight(200)


  def updateSpeakApplier(userId: Long, userName: String): Unit = {
    val agreeBtn = new Button("", new ImageView("img/button/agreeBtn.png"))
    val refuseBtn = new Button("", new ImageView("img/button/refuseBtn.png"))
    agreeBtn.getStyleClass.add("tableBtn")
    refuseBtn.getStyleClass.add("tableBtn")
    val glow = new Glow()
    agreeBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      agreeBtn.setEffect(glow)
    })
    agreeBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      agreeBtn.setEffect(null)
    })
    refuseBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      refuseBtn.setEffect(glow)
    })
    refuseBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      refuseBtn.setEffect(null)
    })
    val newRequest = ApplySpeakListInfo(
      new SimpleStringProperty(s"$userName($userId)"),
      new SimpleObjectProperty[Button](agreeBtn),
      new SimpleObjectProperty[Button](refuseBtn)
    )
    audObservableList.add(newRequest)

    agreeBtn.setOnAction { _ =>listener.handleSpeakApply(userId = userId, userName = userName, accept = true, newRequest)}
    refuseBtn.setOnAction { _ =>listener.handleSpeakApply(userId = userId, userName = userName, accept = false, newRequest)}

  }


  val speakInfoBox = new VBox(15, speakInfoLabel, speakStateBox, applySpeakTable)
  speakInfoBox.setPadding(new Insets(10,30,20,30))



  val leftArea = new VBox(20, meetingInfoBox, speakInfoBox)
  leftArea.setPrefWidth(275)





  /**
    * middle Area
    *
    **/

  /*live control Button*/
  val liveToggleButton = new ToggleButton("")
  liveToggleButton.getStyleClass.add("liveBtn")
  liveToggleButton.setDisable(true)
  Tooltip.install(liveToggleButton, new Tooltip("设备准备中"))
  val meetingStateLabel = new Label(s"设备准备中")
  meetingStateLabel.setFont(Font.font(15))

  val controlBox = new HBox(5)
  controlBox.setAlignment(Pos.CENTER)
  def addToControlBox(isHost: Boolean): Boolean = {
    controlBox.getChildren.clear()
    if(isHost){
      controlBox.getChildren.addAll(liveToggleButton, meetingStateLabel)
    } else {
      controlBox.getChildren.addAll(meetingStateLabel)
    }
  }

  def changeToggleAction(): Unit = {
    log.info(s"changeToggleAction")
    meetingStateLabel.setText("会议未开始")
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
  val selfCanvasBar = new CanvasBar(Constants.DefaultPlayer.width, 40, true)

  selfCanvasBar.imageToggleButton.setOnAction { _ =>
    if (selfCanvasBar.imageToggleButton.isSelected) {
      listener.controlSelfImage(1)
//      Tooltip.install(selfCanvasBar.imageToggleButton, new Tooltip("点击关闭画面"))
    } else {
      listener.controlSelfImage(-1)
//      Tooltip.install(selfCanvasBar.imageToggleButton, new Tooltip("点击打开画面"))
    }
  }

  selfCanvasBar.soundToggleButton.setOnAction { _ =>
    if (selfCanvasBar.soundToggleButton.isSelected) {
      listener.controlSelfSound(1)
//      Tooltip.install(selfCanvasBar.soundToggleButton, new Tooltip("点击关闭声音"))
    } else {
      listener.controlSelfSound(-1)
//      Tooltip.install(selfCanvasBar.soundToggleButton, new Tooltip("点击打开声音"))
    }
  }

  /*self livePane*/
  val selfLivePane = new StackPane(selfImageCanvas)

  selfLivePane.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
    selfLivePane.setAlignment(Pos.BOTTOM_RIGHT)
    selfLivePane.getChildren.add(selfCanvasBar.liveBarBox)
  })

  selfLivePane.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
    selfLivePane.setAlignment(Pos.BOTTOM_RIGHT)
    selfLivePane.getChildren.remove(selfCanvasBar.liveBarBox)
  })


  /*others' canvas*/
  val canvasMap: Map[Int, (Canvas, GraphicsContext, StackPane)] = List(1,2,3,4,5,6).map{ i =>
    val imageCanvas = new Canvas(Constants.DefaultPlayer.width/3, Constants.DefaultPlayer.height/3)
    val imageGc: GraphicsContext = imageCanvas.getGraphicsContext2D
    val imageCanvasBg = new Image("img/picture/background.jpg")
    imageGc.drawImage(imageCanvasBg, 0, 0, Constants.DefaultPlayer.width/3, Constants.DefaultPlayer.height/3)
    val stackPane = new StackPane()
    stackPane.getChildren.add(imageCanvas)
    stackPane.setAlignment(Pos.BOTTOM_CENTER)

    i -> (imageCanvas, imageGc, stackPane)
  }.toMap

  /*others' nameLabel*/
  val nameLabelMap: Map[Int, Label] = List(1,2,3,4,5,6).map{ i =>
    val nameLabel = new Label("")
    nameLabel.setPrefSize(Constants.DefaultPlayer.width/3, 30)
    nameLabel.setFont(Font.font(15))
    nameLabel.setAlignment(Pos.BOTTOM_CENTER)
    i -> nameLabel
  }.toMap

  /*others' liveBar*/
  val liveBarMap: Map[Int, (HBox, ToggleButton, ToggleButton)] = List(1,2,3,4,5,6).map{ i =>
    val canvasBar = new CanvasBar(Constants.DefaultPlayer.width/3, 40, false)
    val liveBar: HBox = canvasBar.liveBarBox

    canvasBar.imageToggleButton.setOnAction { _ =>
      if (canvasBar.imageToggleButton.isSelected) {
        listener.controlOnesImage(orderNum = i, 1)
//        Tooltip.install(canvasBar.imageToggleButton, new Tooltip("点击关闭画面"))
      } else {
        listener.controlOnesImage(orderNum = i, -1)
//        Tooltip.install(canvasBar.imageToggleButton, new Tooltip("点击打开画面"))
      }
    }

    canvasBar.soundToggleButton.setOnAction { _ =>
      if (canvasBar.soundToggleButton.isSelected) {
        listener.controlOnesSound(orderNum = i, 1)
//        Tooltip.install(canvasBar.soundToggleButton, new Tooltip("点击关闭声音"))
      } else {
        listener.controlOnesSound(orderNum = i, -1)
//        Tooltip.install(canvasBar.soundToggleButton, new Tooltip("点击打开声音"))
      }
    }

    canvasBar.kickBtn.setOnAction(_ => listener.kickSbOut(i))
    i -> (liveBar, canvasBar.imageToggleButton, canvasBar.soundToggleButton)
  }.toMap

  def addLiveBarToCanvas(orderNum: Int) = {
    val liveBar: HBox = liveBarMap(orderNum)._1
    canvasMap(orderNum)._3.getChildren.add(liveBar)
  }

  def removeLiveBarFromCanvas(orderNum: Int) = {
    canvasMap(orderNum)._3.getChildren.clear()
    canvasMap(orderNum)._3.getChildren.add(canvasMap(orderNum)._1)
  }

  val box1 = new HBox(
    new VBox(nameLabelMap(1),canvasMap(1)._3),
    new VBox(nameLabelMap(2),canvasMap(2)._3),
    new VBox(nameLabelMap(3),canvasMap(3)._3)
  )
  val box2 = new HBox(
    new VBox(nameLabelMap(4),canvasMap(4)._3),
    new VBox(nameLabelMap(5),canvasMap(5)._3),
    new VBox(nameLabelMap(6),canvasMap(6)._3)
  )
  val othersCanvasBox = new VBox(box1, box2)
  val canvasBox = new VBox(5, selfLivePane, othersCanvasBox)

  val middleBox = new VBox(10, controlBox, canvasBox)
  middleBox.setAlignment(Pos.TOP_CENTER)
  middleBox.setPadding(new Insets(20,0,0,0))



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
  writeField.setOnKeyPressed { e =>
    if (e.getCode == javafx.scene.input.KeyCode.ENTER) {
      if(writeField.getText != "" && writeField.getText != null){
        listener.sendComment(writeField.getText)
        writeField.clear()
      } else {
        WarningDialog.initWarningDialog("请输入评论！")
      }

    }
  }

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
    addToMeetingHostBox(isHost)
    addToMeetingNameBox(isHost)
    addToMeetingDesBox(isHost)
    addToSpeakStateBox(isHost)
    addToControlBox(isHost)
  }


}
