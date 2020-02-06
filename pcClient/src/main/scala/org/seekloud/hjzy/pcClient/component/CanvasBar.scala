package org.seekloud.hjzy.pcClient.component

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Button, ToggleButton, Tooltip}
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox

/**
  * Author: zwq
  * Date: 2020/2/6
  * Time: 22:48
  */
class CanvasBar(width: Double, height: Double) {

  /**
    * needSound & needImage & fullScreen
    */

  val soundToggleButton = new ToggleButton("")
  soundToggleButton.getStyleClass.add("soundBtn")
  soundToggleButton.setSelected(true)
  soundToggleButton.setDisable(false)
  Tooltip.install(soundToggleButton, new Tooltip("点击关闭直播声音"))


  val imageToggleButton = new ToggleButton("")
  imageToggleButton.getStyleClass.add("imageBtn")
  imageToggleButton.setSelected(true)
  imageToggleButton.setDisable(false)
  Tooltip.install(imageToggleButton, new Tooltip("点击关闭直播画面"))

  val fullScreenIcon = new Button("", new ImageView("img/button/full-screen.png"))
  fullScreenIcon.setPrefSize(32, 32)

  val liveBarBox = new HBox(10, soundToggleButton, imageToggleButton, fullScreenIcon)
  liveBarBox.setMaxSize(width, height)
  liveBarBox.setPadding(new Insets(0,10,0,0))
  liveBarBox.setAlignment(Pos.CENTER_RIGHT)
  liveBarBox.setStyle("-fx-background-color: #66808080")







}
