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
import org.seekloud.hjzy.pcClient.core.RmManager.{CreateMeetingSuccess, JoinMeetingSuccess}

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
    override def gotoCreateMeeting(): Unit = {
      if(RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty){
        val createMeetingInfo = loginController.createMeetingDialog(RmManager.roomInfo.get.roomId)
        if(createMeetingInfo.nonEmpty){
          val info = createMeetingInfo.get
          createMeeting(RmManager.userInfo.get.userId, RmManager.roomInfo.get.roomId, info._1, info._2, info._3)
        }
      } else {
        gotoLogin(isToCreate = true)
      }
    }

    override def gotoJoinMeeting(): Unit = {
      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
        val joinMeetingInfo = loginController.joinMeetingDialog()
        if(joinMeetingInfo.nonEmpty){
          val info = joinMeetingInfo.get
          joinMeeting(info._1.toLong, info._2)
        }
      } else {
        gotoLogin(isToJoin = true)
      }
    }

    override def gotoLogin(
      userName: Option[String] = None,
      pwd: Option[String] = None,
      isToCreate: Boolean,
      isToJoin: Boolean
    ): Unit = {
      val userInfo = loginController.loginDialog() //弹出登陆窗口
      log.debug(s"用户输入登录信息：$userInfo")
      if (userInfo.nonEmpty) {
        loginBySelf(userInfo, isToCreate, isToJoin)
      }
    }

    override def gotoRegister(): Unit = {
      val signUpInfo = loginController.registerDialog() //弹出注册窗口
      log.debug(s"用户输入注册信息：$signUpInfo")
      if(signUpInfo.nonEmpty){
        val info = signUpInfo.get
        registerBySelf(info._1, info._2, info._3, info._4)
      }
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
  def loginBySelf(userInfo: Option[(String, String, String)], isToCreate: Boolean, isToJoin: Boolean): Future[Unit] = {
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
          rmManager ! RmManager.LogInSuccess(rsp.userInfo.get, rsp.roomInfo.get)
          Boot.addToPlatform {
            removeLoading()
          }
          if (isToCreate) {
            val createMeetingInfo = loginController.createMeetingDialog(RmManager.roomInfo.get.roomId)
            if(createMeetingInfo.nonEmpty){
              val info = createMeetingInfo.get
              createMeeting(RmManager.userInfo.get.userId, RmManager.roomInfo.get.roomId, info._1, info._2, info._3)
            }

          } else {
            if (isToJoin) {
              val joinMeetingInfo = loginController.joinMeetingDialog()
              if(joinMeetingInfo.nonEmpty){
                val info = joinMeetingInfo.get
                joinMeeting(info._1.toLong, info._2)
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

  /**
    * 注册
    */
  def registerBySelf(userName: String, password: String, email: String, verifyCode: String): Future[Unit] = {
    showLoading()
    RMClient.register(userName, password, verifyCode, email).map {
      case Right(rsp) =>
        if(rsp.errCode == 0){
          removeLoading()
        } else {
          log.error(s"register error: ${rsp.msg}")
          Boot.addToPlatform {
            removeLoading()
            WarningDialog.initWarningDialog(s"${rsp.msg}")
          }
        }
      case Left(error) =>
        log.error(s"register server error: $error")
        Boot.addToPlatform {
          removeLoading()
          WarningDialog.initWarningDialog(s"服务器错误: $error")
        }
    }
  }

  /**
    * 创建会议
    */

  def createMeeting(userId: Long, roomId:Long, password: String, roomName: String, roomDes: String): Future[Unit] ={
    showLoading()
    RMClient.createMeeting(userId, roomId, password, roomName, roomDes).map {
      case Right(rsp) =>
        if(rsp.errCode == 0){
          rmManager ! CreateMeetingSuccess(rsp.roomInfo)
        } else {
          log.error(s"createMeeting error: ${rsp.msg}")
          Boot.addToPlatform {
            removeLoading()
            WarningDialog.initWarningDialog(s"${rsp.msg}")
          }
        }
      case Left(error) =>
        log.error(s"createMeeting server error: $error")
        Boot.addToPlatform {
          removeLoading()
          WarningDialog.initWarningDialog(s"服务器错误: $error")
        }
    }
  }

  /**
    * 加入会议
    */

  def joinMeeting(roomId: Long, password: String): Future[Unit] ={
    showLoading()
    RMClient.joinMeeting(roomId, password).map {
      case Right(rsp) =>
        if(rsp.errCode == 0){
          rmManager ! JoinMeetingSuccess(rsp.roomInfo)
          removeLoading()
        } else {
          log.error(s"joinMeeting error: ${rsp.msg}")
          Boot.addToPlatform {
            removeLoading()
            WarningDialog.initWarningDialog(s"${rsp.msg}")
          }
        }
      case Left(error) =>
        log.error(s"joinMeeting server error: $error")
        Boot.addToPlatform {
          removeLoading()
          WarningDialog.initWarningDialog(s"服务器错误: $error")
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
