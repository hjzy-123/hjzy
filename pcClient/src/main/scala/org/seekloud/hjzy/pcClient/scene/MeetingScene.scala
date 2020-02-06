package org.seekloud.hjzy.pcClient.scene

import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.control._
import javafx.scene.image.Image
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{BorderPane, HBox, StackPane, VBox}
import javafx.scene.text.Font
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import org.seekloud.hjzy.pcClient.common.Constants
import org.seekloud.hjzy.pcClient.common.Constants.AppWindow
import org.seekloud.hjzy.pcClient.component.CanvasBar
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

  def getScene: Scene = {
    this.scene
  }

  var listener: MeetingSceneListener = _

  def setListener(listener: MeetingSceneListener): Unit = {
    this.listener = listener
  }

  /**
    * left Area
    *
    **/

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

  val meetingNameLabel = new Label(s"会议名称:")
  val meetingNameField = new TextField(s"${RmManager.meetingRoomInfo.get.roomName}")
  meetingNameField.setPrefWidth(160)
  val meetingNameValue = new Label(s"${RmManager.meetingRoomInfo.get.roomName}")
  meetingNameValue.setMaxWidth(100)
  val editMeetingNameBtn = new Button(s"确认")

  val meetingDesLabel = new Label(s"会议描述:")
  val meetingDesField  = new TextArea(s"${RmManager.meetingRoomInfo.get.roomName}")
  meetingDesField.setPrefSize(160, 60)
  meetingDesField.setWrapText(true)
  val meetingDesValue = new TextField(s"${RmManager.meetingRoomInfo.get.roomName}")
  meetingDesValue.setEditable(false)
  meetingDesValue.setMaxSize(100, 60)
  val editMeetingDesBtn = new Button(s"确认")

  def genMeetingInfoBox: VBox = {
    val isHost: Boolean =
      if(RmManager.userInfo.get.userId == RmManager.meetingRoomInfo.get.userId) true else false

    val meetingHostBox = if(isHost){
      new HBox(5, meetingHostLabel, meetingHostValue, changeHostBtn)
    } else {
      new HBox(5, meetingHostLabel, meetingHostValue)
    }
    meetingHostBox.setAlignment(Pos.CENTER_LEFT)


    val meetingNameBox = if(isHost){
      val vBox = new VBox(5, meetingNameLabel, meetingNameField)
      val hBox = new HBox(5, vBox, editMeetingNameBtn)
      hBox.setAlignment(Pos.BOTTOM_LEFT)
      hBox
    } else {
      new HBox(5, meetingNameLabel, meetingNameValue)
    }

    val meetingDesBox = if(isHost){
      val vBox = new VBox(5, meetingDesLabel, meetingDesField)
      val hBox = new HBox(5, vBox, editMeetingDesBtn)
      hBox.setAlignment(Pos.BOTTOM_LEFT)
      hBox
    } else {
      new HBox(5, meetingDesLabel, meetingDesValue)
    }

    val meetingInfoBox = new VBox(10, meetingInfoLabel, roomIdBox, meetingHostBox, meetingNameBox, meetingDesBox)
    meetingInfoBox.setPadding(new Insets(20,20,20,20))
    meetingInfoBox
  }




  /**
    * middle Area
    *
    **/

  /*live control Button*/
  val liveToggleButton = new ToggleButton("")
  liveToggleButton.getStyleClass.add("liveBtn")
  liveToggleButton.setDisable(true)
  Tooltip.install(liveToggleButton, new Tooltip("设备准备中"))

  def changeToggleAction(): Unit = {
    liveToggleButton.setDisable(false)
    liveToggleButton.setOnAction {
      _ =>
        if (liveToggleButton.isSelected) {
          listener.startLive()
          Tooltip.install(liveToggleButton, new Tooltip("点击停止直播"))
        } else {
          listener.stopLive()
          Tooltip.install(liveToggleButton, new Tooltip("点击开始直播"))
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

  val box1 = new HBox(canvasMap(1)._1, canvasMap(2)._1, canvasMap(3)._1)
  val box2 = new HBox(canvasMap(4)._1, canvasMap(5)._1, canvasMap(6)._1)
  val othersCanvasBox = new VBox(box1, box2)
  val canvasBox = new VBox(5, selfLivePane, othersCanvasBox)

  val middleBox = new VBox(10, liveToggleButton, canvasBox)
  middleBox.setAlignment(Pos.TOP_CENTER)




  /**
    * right Area
    *
    **/
















  /**
    * Layout
    *
    **/
  val borderPane = new BorderPane()
  borderPane.setLeft(genMeetingInfoBox)
  borderPane.setCenter(middleBox)




  group.getChildren.addAll(borderPane)


}
