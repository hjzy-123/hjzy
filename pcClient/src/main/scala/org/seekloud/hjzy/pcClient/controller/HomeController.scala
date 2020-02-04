package org.seekloud.hjzy.pcClient.controller

import java.io.{BufferedWriter, File, FileWriter}

import akka.actor.typed.ActorRef
import javafx.scene.{Group, Scene}
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.{Constants, StageContext}
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.scene.HomeScene
import org.seekloud.hjzy.pcClient.scene.HomeScene.HomeSceneListener
import org.seekloud.hjzy.pcClient.utils.RMClient
import org.slf4j.LoggerFactory
import org.seekloud.hjzy.pcClient.Boot.executor
import org.seekloud.hjzy.pcClient.component.WarningDialog

import scala.concurrent.Future

/**
  * Author: zwq
  * Date: 2020/1/16
  * Time: 12:56
  */
class HomeController(
  context: StageContext,
  homeScene: HomeScene,
  loginController: LoginController,
//  editController: EditController,
  rmManager: ActorRef[RmManager.RmCommand]) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  var hasWaitingGif = false

  homeScene.setListener(new HomeSceneListener {
    override def gotoLive(): Unit = {
//      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
//        rmManager ! RmManager.GoToLive
//      } else {
//        gotoLogin(isToLive = true)
//      }
    }

    override def gotoRoomHall(): Unit = {
//      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
//        rmManager ! RmManager.GoToRoomHall
//      } else {
//        gotoLogin(isToWatch = true)
//      }
    }

    override def gotoLogin(
      userName: Option[String] = None,
      pwd: Option[String] = None,
      isToLive: Boolean,
      isToWatch: Boolean
    ): Unit = {
      // 弹出登陆窗口
      val userInfo = loginController.loginDialog()
      log.debug(s"用户输入登录信息：$userInfo")
//      if (userInfo.nonEmpty) {
//        loginBySelf(userInfo, isToLive, isToWatch)
//      }
    }

    override def gotoRegister(): Unit = {
      //弹出注册窗口
      val signUpInfo = loginController.registerDialog()
      log.debug(s"用户输入注册信息：$signUpInfo")
//      if (signUpInfo.nonEmpty) {
//        showLoading()
//        Boot.addToPlatform {
//          WarningDialog.initWarningDialog("邮件已发送到您的邮箱，请查收邮件完成注册！")
//        }
//        RMClient.signUp(signUpInfo.get._1.toString, signUpInfo.get._2.toString, signUpInfo.get._3.toString).map {
//          case Right(signUpRsp) =>
//            if (signUpRsp.errCode == 0) {
//              removeLoading()
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog("注册成功！")
//              }
//            } else {
//              log.error(s"sign up error: ${signUpRsp.msg}")
//              removeLoading()
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"${signUpRsp.msg}")
//              }
//            }
//          case Left(error) =>
//            log.error(s"sign up server error:$error")
//            removeLoading()
//            Boot.addToPlatform {
//              WarningDialog.initWarningDialog(s"验证超时！")
//            }
//        }
//      }
    }

    override def logout(): Unit = {
      rmManager ! RmManager.Logout
//      deleteLoginTemp()

    }

    override def editInfo(): Unit = {
//      val editInfo = editController.editDialog()
//      if (editInfo.nonEmpty) {
//        log.debug("start changeUserName...")
//        if (editInfo.get._3 != RmManager.userInfo.get.userName) {
//          RMClient.changeUserName(RmManager.userInfo.get.userId, editInfo.get._3).map {
//            case Right(rsp) =>
//              if (rsp.errCode == 0) {
//                rmManager ! RmManager.ChangeUserName(editInfo.get._3)
//                log.debug(s"changeUserName success.")
//              } else {
//                log.error(s"changeUserName error: ${rsp.msg},errCode:${rsp.errCode}")
//                Boot.addToPlatform {
//                  WarningDialog.initWarningDialog(s"${rsp.msg}")
//                }
//              }
//            case Left(error) =>
//              log.error(s"upload header server error:$error")
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"服务器出错: $error")
//              }
//          }
//        }
//        if (editInfo.get._1 != null) {
//          log.debug("start uploading header...")
//          RMClient.uploadImg(editInfo.get._1, RmManager.userInfo.get.userId, CommonInfo.ImgType.headImg).map {
//            case Right(imgChangeRsp) =>
//              if (imgChangeRsp.errCode == 0) {
//                val headerUrl = imgChangeRsp.url
//                rmManager ! RmManager.ChangeHeader(headerUrl)
//                log.debug(s"upload header success,url:$headerUrl")
//              } else {
//                log.error(s"upload header error: ${imgChangeRsp.msg},errCode:${imgChangeRsp.errCode}")
//                Boot.addToPlatform {
//                  WarningDialog.initWarningDialog(s"${imgChangeRsp.msg}")
//
//                }
//              }
//            case Left(error) =>
//              log.error(s"upload header server error:$error")
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"服务器出错: $error")
//              }
//          }
//        }
//        if (editInfo.get._2 != null) {
//          log.debug(s"start uploading cover...")
//          RMClient.uploadImg(editInfo.get._2, RmManager.userInfo.get.userId, CommonInfo.ImgType.coverImg).map {
//            case Right(imgChangeRsp) =>
//              if (imgChangeRsp.errCode == 0) {
//                val coverUrl = imgChangeRsp.url
//                rmManager ! RmManager.ChangeCover(coverUrl)
//                log.debug(s"upload cover success,url:$coverUrl")
//              } else {
//                log.error(s"upload cover error: ${imgChangeRsp.msg}")
//                Boot.addToPlatform {
//                  WarningDialog.initWarningDialog(s"${imgChangeRsp.msg}")
//
//                }
//              }
//            case Left(error) =>
//              log.error(s"upload cover server error:$error")
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"服务器出错: $error")
//              }
//          }
//        }
//      }

    }
  })




  def showScene(): Unit = {
    Boot.addToPlatform(
      context.switchScene(homeScene.getScene, title = "pc客户端-主页")
    )
  }

  def showLoading(): Unit = {
    Boot.addToPlatform {
      if (!hasWaitingGif) {
        homeScene.group.getChildren.add(homeScene.waitingGif)
        hasWaitingGif = true
      }
    }
  }
  def removeLoading(): Unit = {
    Boot.addToPlatform {
      if (hasWaitingGif) {
        homeScene.group.getChildren.remove(homeScene.waitingGif)
        hasWaitingGif = false
      }
    }
  }

  /**
    * 用户自己输入信息登录
    */
  def loginBySelf(
    userInfo: Option[(String, String, String)],
    isToLive: Boolean,
    isToWatch: Boolean
  ): Future[Unit] = {
    showLoading()
    val r =
      if (userInfo.get._3 == "userName") {
        RMClient.signInByName(userInfo.get._1, userInfo.get._2) //用户名密码登录
      } else {
        RMClient.signInByMail(userInfo.get._1, userInfo.get._2) //邮箱验证码登录
      }
    r.map {
      case Right(rsp) =>
        if (rsp.errCode == 0) {
          rmManager ! RmManager.SignInSuccess(rsp.userInfo.get, rsp.roomInfo.get)
          if (isToLive) {
            rmManager ! RmManager.GoToLive
          } else {
            if (isToWatch) {
              rmManager ! RmManager.GoToRoomHall
            } else {
              Boot.addToPlatform {
                removeLoading()
                showScene()
              }
            }
          }
//          deleteLoginTemp()
//          createLoginTemp(userInfo.get._2, rsp.userInfo.get, rsp.roomInfo.get)
        } else {
          log.error(s"sign in error: ${rsp.msg}")
          Boot.addToPlatform {
            removeLoading()
            WarningDialog.initWarningDialog(s"${rsp.msg}")
          }
        }
      case Left(e) =>
        log.error(s"sign in server error: $e")
        Boot.addToPlatform {
          removeLoading()
          WarningDialog.initWarningDialog(s"服务器错误: $e")
        }
    }

  }

//  /**
//    * 创建theia登录临时文件
//    */
//  def createLoginTemp(password: String, userInfo: UserInfo, roomInfo: RoomInfo): Unit = {
//
//    val file = Constants.loginInfoCache
//    val temp = File.createTempFile("theia", "cacheLogin", file) //为临时文件名称添加前缀和后缀
//    if (temp.exists() && temp.canWrite) {
//      val bufferedWriter = new BufferedWriter(new FileWriter(temp))
//      bufferedWriter.write(s"passWord:${jdkAESEncode(password)}\n")
//      bufferedWriter.write(s"userId:${userInfo.userId}\n")
//      bufferedWriter.write(s"userName:${userInfo.userName}\n")
//      bufferedWriter.write(s"headImgUrl:${userInfo.headImgUrl}\n")
//      bufferedWriter.write(s"token:${userInfo.token}\n")
//      bufferedWriter.write(s"tokenExistTime:${userInfo.tokenExistTime}\n")
//      bufferedWriter.write(s"roomId:${roomInfo.roomId}\n")
//      bufferedWriter.write(s"roomName:${roomInfo.roomName}\n")
//      bufferedWriter.write(s"roomDes:${roomInfo.roomDes}\n")
//      bufferedWriter.write(s"coverImgUrl:${roomInfo.coverImgUrl}\n")
//      bufferedWriter.write(s"getTokenTime:${System.currentTimeMillis()}\n")
//      bufferedWriter.close()
//    }
//    log.debug(s"create theia temp: $temp")
//  }

//  /**
//    * 删除登录临时文件
//    */
//  def deleteLoginTemp(): Unit = {
//    val dir = Constants.loginInfoCache
//    dir.listFiles().foreach { file =>
//      if (file.exists()) file.delete()
//      log.debug(s"delete theia temps: ${file.getName}")
//    }
//  }




}
