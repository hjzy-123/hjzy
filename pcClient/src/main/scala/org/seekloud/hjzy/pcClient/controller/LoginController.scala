package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import javafx.geometry.{Insets, Pos}
import javafx.scene.Group
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.image.ImageView
import javafx.scene.layout.{GridPane, HBox, VBox}
import javafx.scene.text.{Font, Text}
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.StageContext
import org.seekloud.hjzy.pcClient.component.WarningDialog
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.utils.RMClient
import org.slf4j.LoggerFactory
import org.seekloud.hjzy.pcClient.Boot.executor


/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 11:27
  */
class LoginController(
  context: StageContext,
  rmManager: ActorRef[RmManager.RmCommand]
) {
  private[this] val log = LoggerFactory.getLogger(this.getClass)

  //登录弹窗
  def loginDialog(): Option[(String, String, String)] = {
    val dialog = new Dialog[(String, String, String)]()
    dialog.setTitle("登录")

    val welcomeText = new Text("欢迎登录")
    welcomeText.setStyle("-fx-font: 35 KaiTi;-fx-fill: #333f50")
    val upBox = new HBox()
    upBox.setAlignment(Pos.TOP_CENTER)
    upBox.setPadding(new Insets(40, 200, 0, 200))
    upBox.getChildren.add(welcomeText)

    // toggleButton
    val tb1Icon = new ImageView("img/icon/userName.png")
    tb1Icon.setFitHeight(30)
    tb1Icon.setFitWidth(30)
    val tb2Icon = new ImageView("img/icon/email.png")
    tb2Icon.setFitHeight(30)
    tb2Icon.setFitWidth(30)
    val tb1 = new ToggleButton("用户名密码登录", tb1Icon)
    tb1.getStyleClass.add("hostScene-leftArea-toggleButton")
    tb1.setPrefWidth(170)
    val tb2 = new ToggleButton("邮箱验证码登录", tb2Icon)
    tb2.setPrefWidth(170)
    tb2.getStyleClass.add("hostScene-leftArea-toggleButton")
    tb1.setSelected(true)

    val toggleGroup = new ToggleGroup
    tb1.setToggleGroup(toggleGroup)
    tb2.setToggleGroup(toggleGroup)
    val tbBox = new HBox()
    tbBox.setAlignment(Pos.CENTER)
    tbBox.getChildren.addAll(tb1, tb2)

    //userNameGrid
    val userNameIcon = new ImageView("img/icon/userName.png")
    userNameIcon.setFitHeight(30)
    userNameIcon.setFitWidth(30)
    val userNameLabel = new Label("用户名:")
    userNameLabel.setFont(Font.font(15))
    val userNameField = new TextField("")

    val passwordIcon = new ImageView("img/icon/passWord.png")
    passwordIcon.setFitHeight(30)
    passwordIcon.setFitWidth(30)
    val passWordLabel = new Label("密码:")
    passWordLabel.setFont(Font.font(15))
    val passWordField = new PasswordField()

    val userNameGrid = new GridPane
    userNameGrid.setHgap(20)
    userNameGrid.setVgap(30)
    userNameGrid.add(userNameIcon, 0, 0)
    userNameGrid.add(userNameLabel, 1, 0)
    userNameGrid.add(userNameField, 2, 0)
    userNameGrid.add(passwordIcon, 0, 1)
    userNameGrid.add(passWordLabel, 1, 1)
    userNameGrid.add(passWordField, 2, 1)
    userNameGrid.setStyle("-fx-background-color:#ddc1ad;")
    userNameGrid.setPadding(new Insets(60, 20, 60, 20))


    //emailGrid
    val emailIcon = new ImageView("img/icon/email.png")
    emailIcon.setFitHeight(28)
    emailIcon.setFitWidth(28)
    val emailLabel = new Label("邮箱:")
    emailLabel.setFont(Font.font(15))
    val emailField = new TextField("")
    emailField.setMaxWidth(130)
    val getCodeBtn = new Button("发送验证码")
    getCodeBtn.setFont(Font.font(12))
    getCodeBtn.setOnAction(_ => {
      if(emailField.getText().nonEmpty){
        RMClient.genLoginVerifyCode(emailField.getText()).map{
          case Right(rsp) =>
            if(rsp.errCode == 0){
              Boot.addToPlatform {
                WarningDialog.initWarningDialog("获取邮箱验证码成功！")
              }
            } else {
              Boot.addToPlatform {
                WarningDialog.initWarningDialog("获取邮箱验证码失败！")
              }
            }
          case Left(error) =>
            Boot.addToPlatform {
              WarningDialog.initWarningDialog(s"获取邮箱验证码失败：$error")
            }
        }
      } else {
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("请填写邮箱地址！")
        }
      }
    })

    val emailPasswordIcon = new ImageView("img/icon/passWord.png")
    emailPasswordIcon.setFitHeight(30)
    emailPasswordIcon.setFitWidth(30)
    val emailPassWordLabel = new Label("验证码:")
    emailPassWordLabel.setFont(Font.font(15))
    val emailPassWordField = new TextField()
    emailPassWordField.setMaxWidth(130)

    val emailGrid = new GridPane
    emailGrid.setHgap(8)
    emailGrid.setVgap(30)
    emailGrid.add(emailIcon, 0, 0)
    emailGrid.add(emailLabel, 1, 0)
    emailGrid.add(emailField, 2, 0)
    emailGrid.add(getCodeBtn, 3, 0)
    emailGrid.add(emailPasswordIcon, 0, 1)
    emailGrid.add(emailPassWordLabel, 1, 1)
    emailGrid.add(emailPassWordField, 2, 1)
    emailGrid.setStyle("-fx-background-color:#ddc1ad")
    emailGrid.setPadding(new Insets(60, 15, 60, 15))

    //bottomBox
    val bottomBox = new VBox()
    bottomBox.getChildren.addAll(tbBox, userNameGrid)
    bottomBox.setAlignment(Pos.CENTER)
    //    bottomBox.setStyle("-fx-background-color:#d4dbe3;-fx-background-radius: 10")
    bottomBox.setPadding(new Insets(10, 100, 50, 100))

    tb1.setOnAction(_ => {
      if (!tb2.isSelected) tb1.setSelected(true)
      bottomBox.getChildren.clear()
      bottomBox.getChildren.addAll(tbBox, userNameGrid)
    }
    )
    tb2.setOnAction(_ => {
      if (!tb1.isSelected) tb2.setSelected(true)
      bottomBox.getChildren.clear()
      bottomBox.getChildren.addAll(tbBox, emailGrid)
    }
    )

    val box = new VBox()
    box.getChildren.addAll(upBox, bottomBox)
    box.setAlignment(Pos.CENTER)
    box.setSpacing(30)
    box.setStyle("-fx-background-color:#e6d9d1")

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.addAll(box)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (dialogButton == confirmButton) {
        //        log.debug(s"tb1selected:${tb1.isSelected},tb2selected:${tb2.isSelected},userName:${userNameField.getText()},userPwd：${passWordField.getText()},email:${emailField.getText()},emailPwd:${emailPassWordField.getText()}")
        if (tb1.isSelected && userNameField.getText().nonEmpty && passWordField.getText().nonEmpty) {
          (userNameField.getText(), passWordField.getText(), "userName")
        } else {
          if (tb2.isSelected && emailField.getText().nonEmpty && emailPassWordField.getText().nonEmpty) {
            (emailField.getText(), emailPassWordField.getText(), "email")
          } else {
            Boot.addToPlatform {
              WarningDialog.initWarningDialog("请填写完整信息！")
            }
            null
          }
        }
      } else {
        null
      }
    )
    var loginInfo: Option[(String, String, String)] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a._1 != null && a._2 != null && a._3 != null && a._1 != "" && a._2 != "" && a._3 != "")
        loginInfo = Some((a._1, a._2, a._3))
      else
        None
    }
    loginInfo
  }

  //注册弹窗
  def registerDialog(): Option[(String, String, String, String)] = {
    val dialog = new Dialog[(String, String, String, String)]()
    dialog.setTitle("注册")

    val welcomeText = new Text("欢迎注册")
    welcomeText.setStyle("-fx-font: 35 KaiTi;-fx-fill: #333f50")
    val upBox = new HBox()
    upBox.setAlignment(Pos.TOP_CENTER)
    upBox.setPadding(new Insets(40, 200, 0, 200))
    upBox.getChildren.add(welcomeText)

    val emailIcon = new ImageView("img/icon/email.png")
    emailIcon.setFitHeight(28)
    emailIcon.setFitWidth(28)
    val emailLabel = new Label("邮箱:")
    emailLabel.setFont(Font.font(15))
    val emailField = new TextField()

    val getCodeBtn = new Button("获取验证码")
    getCodeBtn.setFont(Font.font(12))
    getCodeBtn.setOnAction(_ => {
      if(emailField.getText().nonEmpty){
        RMClient.genRegisterVerifyCode(emailField.getText()).map{
          case Right(rsp) =>
            if(rsp.errCode == 0){
              Boot.addToPlatform {
                WarningDialog.initWarningDialog("获取邮箱验证码成功！")
              }
            } else {
              Boot.addToPlatform {
                WarningDialog.initWarningDialog("获取邮箱验证码失败！")
              }
            }
          case Left(error) =>
            Boot.addToPlatform {
              WarningDialog.initWarningDialog(s"获取邮箱验证码失败：$error")
            }
        }
      } else {
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("请填写邮箱地址！")
        }
      }
    })

    val emailCodeIcon = new ImageView("img/icon/email.png")
    emailCodeIcon.setFitHeight(28)
    emailCodeIcon.setFitWidth(28)
    val emailCode = new Label("验证码:")
    emailCode.setFont(Font.font(15))
    val emailCodeField = new TextField()

    val userNameIcon = new ImageView("img/icon/userName.png")
    userNameIcon.setFitHeight(30)
    userNameIcon.setFitWidth(30)
    val userNameLabel = new Label("用户名:")
    userNameLabel.setFont(Font.font(15))
    val userNameField = new TextField()

    val passWordIcon = new ImageView("img/icon/passWord.png")
    passWordIcon.setFitHeight(30)
    passWordIcon.setFitWidth(30)
    val passWordLabel = new Label("密码:")
    passWordLabel.setFont(Font.font(15))
    val passWordField = new PasswordField()

    val passWordIcon1 = new ImageView("img/icon/passWord.png")
    passWordIcon1.setFitHeight(30)
    passWordIcon1.setFitWidth(30)
    val passWordLabel1 = new Label("确认密码:")
    passWordLabel1.setFont(Font.font(15))
    val passWordField1 = new PasswordField()

    val grid = new GridPane
    grid.setHgap(15)
    grid.setVgap(25)
    grid.add(emailIcon, 0, 0)
    grid.add(emailLabel, 1, 0)
    grid.add(emailField, 2, 0)
    grid.add(getCodeBtn, 3, 0)
    grid.add(emailCodeIcon, 0, 1)
    grid.add(emailCode, 1, 1)
    grid.add(emailCodeField, 2, 1)
    grid.add(userNameIcon, 0, 2)
    grid.add(userNameLabel, 1, 2)
    grid.add(userNameField, 2, 2)
    grid.add(passWordIcon, 0, 3)
    grid.add(passWordLabel, 1, 3)
    grid.add(passWordField, 2, 3)
    grid.add(passWordIcon1, 0, 4)
    grid.add(passWordLabel1, 1, 4)
    grid.add(passWordField1, 2, 4)
    grid.setStyle("-fx-background-color:#ddc1ad;-fx-background-radius: 10")
    grid.setPadding(new Insets(60, 20, 60, 20))

    val bottomBox = new HBox()
    bottomBox.getChildren.add(grid)
    bottomBox.setAlignment(Pos.BOTTOM_CENTER)
    bottomBox.setPadding(new Insets(10, 100, 50, 100))

    val box = new VBox()
    box.getChildren.addAll(upBox, bottomBox)
    box.setAlignment(Pos.CENTER)
    box.setSpacing(30)
    box.setStyle("-fx-background-color:#e6d9d1")

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(box)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (userNameField.getText().nonEmpty && passWordField.getText().nonEmpty && emailField.getText().nonEmpty && emailCodeField.getText.nonEmpty) {
        if (passWordField.getText() == passWordField1.getText()) {
          if (dialogButton == confirmButton)
             (userNameField.getText(),passWordField.getText(), emailField.getText(), emailCodeField.getText)
          else
            null
        } else {
          Boot.addToPlatform(
            WarningDialog.initWarningDialog("注册失败：两次输入密码不一致")
          )
          null
        }
      } else {
        Boot.addToPlatform(
          WarningDialog.initWarningDialog("输入不能为空！")
        )
        null
      }
    )
    var registerInfo: Option[(String, String, String, String)] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a._1 != null && a._2 != null && a._3 != null && a._4 != null && a._1 != "" && a._2 != "" && a._3 != "" && a._4 != "")
        registerInfo = Some((a._1, a._2, a._3, a._4))
      else
        None
    }
    registerInfo
  }

  //创建会议弹窗
  def createMeetingDialog(roomId: Long): Option[(String, String, String)] = {
    val dialog = new Dialog[(String, String, String)]()
    dialog.setTitle("创建会议")

    //会议房间号
    val roomIdIcon = new ImageView("img/icon/email.png")
    roomIdIcon.setFitHeight(28)
    roomIdIcon.setFitWidth(28)
    val roomIdLabel = new Label("房间号")
    roomIdLabel.setFont(Font.font(15))
    val roomIdText = new Label(s"$roomId")
    roomIdText.setFont(Font.font(15))

    //会议房间密码
    val passwordIcon = new ImageView("img/icon/email.png")
    passwordIcon.setFitHeight(28)
    passwordIcon.setFitWidth(28)
    val passwordLabel = new Label("房间密码")
    passwordLabel.setFont(Font.font(15))
    val passwordField = new TextField()

    //会议名
    val roomNameIcon = new ImageView("img/icon/userName.png")
    roomNameIcon.setFitHeight(30)
    roomNameIcon.setFitWidth(30)
    val roomNameLabel = new Label("会议名")
    roomNameLabel.setFont(Font.font(15))
    val roomNameField = new TextField()

    //会议描述
    val describeIcon = new ImageView("img/icon/passWord.png")
    describeIcon.setFitHeight(30)
    describeIcon.setFitWidth(30)
    val describeLabel = new Label("会议描述")
    describeLabel.setFont(Font.font(15))
//    val describeField = new TextField()
    val describeField  = new TextArea()
    describeField.setPrefSize(160, 100)
    describeField.setWrapText(true)

    //邀请参会者
    val inviteIcon = new ImageView("img/icon/userName.png")
    inviteIcon.setFitHeight(30)
    inviteIcon.setFitWidth(30)
    val inviteLabel = new Label(s"参会者列表")
    inviteLabel.setFont(Font.font(15))
    val addBtn = new Button("添加+")
    addBtn.setStyle("-fx-background-color: #00000000;-fx-cursor: hand;-fx-border-color: black; -fx-border-radius: 6; -fx-font-size: 12;")
    val inviteLabelBox = new HBox(15, inviteIcon, inviteLabel, addBtn)
    inviteLabelBox.setAlignment(Pos.CENTER_LEFT)

    val nameListBox = new VBox()
    nameListBox.setPrefSize(200, 200)
    nameListBox.setPadding(new Insets(0,0,0,50))

    val inviteBox = new VBox(15, inviteLabelBox, nameListBox)

    import scala.collection.mutable

    var list: List[String] = List()
    addBtn.setOnAction{_ =>
      if(nameListBox.getChildren.size() <= 5){
        val dialogRst = inviteDialog()
        if (dialogRst.nonEmpty){
          list = dialogRst.get :: list
          val nameLabel = new Label(s"${dialogRst.get}")
          nameLabel.setStyle("-fx-text-fill: #6495ED; -fx-font-size: 12;")
          nameLabel.autosize()
          val deleteIcon = new ImageView("img/button/delete.png")
          val deleteBtn = new Button("", deleteIcon)
          val nameBox = new HBox(10, nameLabel, deleteBtn)
          nameBox.setAlignment(Pos.CENTER_LEFT)
          nameListBox.getChildren.add(nameBox)
          deleteBtn.setOnAction{_ =>
            list = list.filterNot(_ == dialogRst.get)
            nameListBox.getChildren.remove(nameBox)
          }
          deleteBtn.setStyle("-fx-background-color: #00000000;-fx-cursor: hand;-fx-border-color: #00000000;")

        }
      } else {
        WarningDialog.initWarningDialog("最多邀请六名参会者！")
      }
    }

    val grid = new GridPane()
    grid.setHgap(15)
    grid.setVgap(25)
    grid.add(roomIdIcon, 0, 0)
    grid.add(roomIdLabel, 1, 0)
    grid.add(roomIdText, 2, 0)
    grid.add(passwordIcon, 0, 1)
    grid.add(passwordLabel, 1, 1)
    grid.add(passwordField, 2, 1)
    grid.add(roomNameIcon, 0, 2)
    grid.add(roomNameLabel, 1, 2)
    grid.add(roomNameField, 2, 2)
    grid.add(describeIcon, 0, 3)
    grid.add(describeLabel, 1, 3)
    grid.add(describeField, 2, 3)

    val wholeBox = new HBox(20, grid, inviteBox)
    wholeBox.setAlignment(Pos.TOP_CENTER)
    wholeBox.setPadding(new Insets(50, 50, 50, 50))
    wholeBox.setStyle("-fx-background-color:#e6d9d1")

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(wholeBox)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (dialogButton == confirmButton){
        if (passwordField.getText().nonEmpty && roomNameField.getText().nonEmpty && describeField.getText().nonEmpty){
          (passwordField.getText(), roomNameField.getText(),describeField.getText())
        } else {
          Boot.addToPlatform(
            WarningDialog.initWarningDialog("输入不能为空！")
          )
          null
        }
      } else {
        null
      }
    )
    var createMeetingInfo: Option[(String, String, String)] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a._1 != null && a._2 != null && a._3 != null && a._1 != "" && a._2 != "" && a._3 != "")
        createMeetingInfo = Some((a._1, a._2, a._3))
      else
        None
    }
    createMeetingInfo
  }

  //添加参会者弹窗
  def inviteDialog() = {
    val dialog = new Dialog[String]()
    dialog.setTitle("邀请参会者")
    val nameLabel = new Label(s"用户名：")
    val nameField = new TextField()

    val box = new HBox(5, nameLabel, nameField)
    box.setAlignment(Pos.CENTER)
    box.setPadding(new Insets(20,20,20,20))
//    box.setStyle("-fx-background-color:#e6d9d1")


    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(box)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (dialogButton == confirmButton){
        if (nameField.getText().nonEmpty){
          nameField.getText()
        } else {
          Boot.addToPlatform(
            WarningDialog.initWarningDialog("输入不能为空！")
          )
          null
        }
      } else null
    )
    var info: Option[String] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a != null && a != "" )
        info = Some(a)
      else
        None
    }
    info

  }

  //加入会议弹窗
  def joinMeetingDialog(): Option[(String, String)] = {
    val dialog = new Dialog[(String, String)]()
    dialog.setTitle("加入会议")

    val welcomeText = new Text("加入会议")
    welcomeText.setStyle("-fx-font: 35 KaiTi;-fx-fill: #333f50")
    val upBox = new HBox()
    upBox.setAlignment(Pos.TOP_CENTER)
    upBox.setPadding(new Insets(40, 200, 0, 200))
    upBox.getChildren.add(welcomeText)

    //会议房间号
    val roomIdIcon = new ImageView("img/icon/email.png")
    roomIdIcon.setFitHeight(28)
    roomIdIcon.setFitWidth(28)
    val roomIdLabel = new Label("房间号:")
    roomIdLabel.setFont(Font.font(15))
    val roomIdField = new TextField()

    //会议房间密码
    val passwordIcon = new ImageView("img/icon/email.png")
    passwordIcon.setFitHeight(28)
    passwordIcon.setFitWidth(28)
    val passwordLabel = new Label("房间密码:")
    passwordLabel.setFont(Font.font(15))
    val passwordField = new PasswordField()

    val grid = new GridPane
    grid.setHgap(15)
    grid.setVgap(25)
    grid.add(roomIdIcon, 0, 0)
    grid.add(roomIdLabel, 1, 0)
    grid.add(roomIdField, 2, 0)
    grid.add(passwordIcon, 0, 1)
    grid.add(passwordLabel, 1, 1)
    grid.add(passwordField, 2, 1)

    grid.setStyle("-fx-background-color:#ddc1ad;-fx-background-radius: 10")
    grid.setPadding(new Insets(60, 20, 60, 20))

    val bottomBox = new HBox()
    bottomBox.getChildren.add(grid)
    bottomBox.setAlignment(Pos.BOTTOM_CENTER)
    bottomBox.setPadding(new Insets(10, 100, 50, 100))

    val box = new VBox()
    box.getChildren.addAll(upBox, bottomBox)
    box.setAlignment(Pos.CENTER)
    box.setSpacing(30)
    box.setStyle("-fx-background-color:#e6d9d1")

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(box)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (roomIdField.getText().nonEmpty && passwordField.getText().nonEmpty) {
        if (dialogButton == confirmButton)
          (roomIdField.getText(), passwordField.getText())
        else
          null
      } else {
        Boot.addToPlatform(
          WarningDialog.initWarningDialog("输入不能为空！")
        )
        null
      }
    )
    var joinMeetingInfo: Option[(String, String)] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a._1 != null && a._2 != null && a._1 != "" && a._2 != "")
        joinMeetingInfo = Some((a._1, a._2))
      else
        None
    }
    joinMeetingInfo
  }


}
