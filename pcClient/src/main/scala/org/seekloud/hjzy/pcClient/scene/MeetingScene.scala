package org.seekloud.hjzy.pcClient.scene

import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import org.seekloud.hjzy.pcClient.common.Constants.AppWindow
import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2020/2/5
  * Time: 22:45
  */
object MeetingScene {
  trait MeetingSceneListener{



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



  group.getChildren.addAll()


}
