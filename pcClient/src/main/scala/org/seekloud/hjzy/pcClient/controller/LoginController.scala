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
import org.slf4j.LoggerFactory

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
    userNameGrid.setStyle("-fx-background-color:#d4dbe3;")
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
    emailGrid.setStyle("-fx-background-color:#d4dbe3")
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
    box.setStyle("-fx-background-color:#f2f5fb")

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

  //  注册弹窗
  def registerDialog(): Option[(String, String, String)] = {
    val dialog = new Dialog[(String, String, String)]()
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
    grid.setStyle("-fx-background-color:#d4dbe3;-fx-background-radius: 10")
    grid.setPadding(new Insets(60, 20, 60, 20))

    val bottomBox = new HBox()
    bottomBox.getChildren.add(grid)
    bottomBox.setAlignment(Pos.BOTTOM_CENTER)
    bottomBox.setPadding(new Insets(10, 100, 50, 100))

    val box = new VBox()
    box.getChildren.addAll(upBox, bottomBox)
    box.setAlignment(Pos.CENTER)
    box.setSpacing(30)
    box.setStyle("-fx-background-color:#f2f5fb")

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(box)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (userNameField.getText().nonEmpty && passWordField.getText().nonEmpty && emailField.getText().nonEmpty) {
        if (passWordField.getText() == passWordField1.getText()) {
          if (dialogButton == confirmButton)
             (emailField.getText(), userNameField.getText(),passWordField.getText())
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
    var registerInfo: Option[(String, String, String)] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a._1 != null && a._2 != null && a._3 != null && a._1 != "" && a._2 != "" && a._3 != "")
        registerInfo = Some((a._1, a._2, a._3))
      else
        None
    }
    registerInfo
  }
}