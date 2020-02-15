package org.seekloud.hjzy.pcClient.component

import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.RcvComment
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.text.Font
import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2020/2/7
  * Time: 16:04
  */
class CommentBoard(boardWidth: Double, boardHeight: Double) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  var count = 0

  protected val emojiFont = "Segoe UI Emoji"

//  //label
//  val messageIcon = new ImageView("img/messageIcon.png")
//  messageIcon.setFitHeight(25)
//  messageIcon.setFitWidth(30)
//  val messageLabel = new Label("留言区", messageIcon)
//  messageLabel.getStyleClass.add("hostScene-rightArea-label")

  //default message
  val defaultText = new Label("还没有留言哦~")
  defaultText.setFont(Font.font("Verdana", 15))
  defaultText.setPrefSize(200, 15)

  //commentBoard
  val commentBoard = new VBox(5)
  commentBoard.getChildren.add(defaultText)
  commentBoard.setPrefWidth(boardWidth)
  commentBoard.setPrefHeight(boardHeight)
  commentBoard.getStyleClass.add("commentBoard")


  def updateComment(RecComment: RcvComment): Unit = {
    val userName = RecComment.userId match {
      case -1 =>
        "[系统消息]："
      case _ =>
        s"[${RecComment.userName}]："
    }

    val nameLabel = new Label(userName)
    val commentLabel = new Label(RecComment.comment)
    nameLabel.setFont(Font.font(emojiFont, 15))
    nameLabel.setWrapText(false)
    commentLabel.setFont(Font.font(emojiFont, 15))
    commentLabel.setWrapText(false)

    if (RecComment.userId == -1) {
      nameLabel.getStyleClass.add("system_comment")
      commentLabel.getStyleClass.add("system_comment")
    } else {
      nameLabel.getStyleClass.add("user_comment_name")
      commentLabel.getStyleClass.add("user_comment_text")
    }

    val oneCommentBox = new HBox(0)
    oneCommentBox.getChildren.addAll(nameLabel, commentLabel)

    if (count == 0) {
      commentBoard.getChildren.remove(0)
      commentBoard.getChildren.add(oneCommentBox)
      count += 1
    } else {
      if (count < 20) { //最多显示20条留言
        commentBoard.getChildren.add(oneCommentBox)
        count += 1
      } else {
        commentBoard.getChildren.remove(0)
        commentBoard.getChildren.add(oneCommentBox)
      }
    }
  }


}
