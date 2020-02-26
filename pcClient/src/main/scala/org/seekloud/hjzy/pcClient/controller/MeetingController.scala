package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol._
import javafx.geometry.{Insets, Pos}
import javafx.scene.Group
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{GridPane, HBox, VBox}
import javafx.scene.text.{Font, Text}
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.{Constants, StageContext}
import org.seekloud.hjzy.pcClient.component.WarningDialog
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.core.RmManager.{GetLiveInfoReq, StartMeetingReq, _}
import org.seekloud.hjzy.pcClient.scene.HomeScene.HomeSceneListener
import org.seekloud.hjzy.pcClient.scene.MeetingScene
import org.seekloud.hjzy.pcClient.scene.MeetingScene.{ApplySpeakListInfo, MeetingSceneListener}
import org.slf4j.LoggerFactory
import org.seekloud.hjzy.pcClient.Boot.executor

import scala.collection.mutable
import scala.concurrent.Future


/**
  * Author: zwq
  * Date: 2020/2/6
  * Time: 0:04
  */
class MeetingController(
  context: StageContext,
  meetingScene: MeetingScene,
  rmManager: ActorRef[RmManager.RmCommand],
  ifHostWhenCreate: Boolean) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  case class PartInfo(userName: String, imageStatus: Int, soundStatus: Int) //1->open, -1->close

  var isHost: Boolean = this.ifHostWhenCreate

  var isLiving: Boolean = false

  var someoneSpeaking: Boolean = false

  var partUserMap: Map[Int, Long] = Map() // canvasId -> userId
  var partInfoMap = mutable.HashMap.empty[Long, this.PartInfo] // userId -> PartInfo(userName, imageStatus, soundStatus)

  var selfImageStatus: Int = 1 //1->open, -1->close
  var selfSoundStatus: Int = 1 //1->open, -1->close

  var previousMeetingName = ""
  var previousMeetingDes = ""

  meetingScene.setListener(new MeetingSceneListener {
    override def startLive(): Unit = {
      log.info(s"点击开始会议")
      rmManager ! StartMeetingReq
    }

    override def stopLive(): Unit = {
      log.info(s"点击结束会议")
      rmManager ! RmManager.StopMeetingReq
    }

    override def changeHost(): Unit = {
      if(partInfoMap.nonEmpty){
        val newHostId = chooseAudienceDialog(toChangeHost = Some(true))
        if(newHostId.nonEmpty) {
            rmManager ! TurnToAudience(newHostId.get, meetingScene.audSpeakApplyMap)
          }
      } else {
        Boot.addToPlatform{
          WarningDialog.initWarningDialog("当前房间无其他人！")
        }
      }
    }

    override def modifyRoom(roomName: Option[String] = None, roomDes: Option[String] = None): Unit = {
      log.info(s"点击更改房间信息：name == $roomName, des == $roomDes")
      if(roomName.nonEmpty) previousMeetingName = RmManager.meetingRoomInfo.get.roomName
      if(roomDes.nonEmpty) previousMeetingDes = RmManager.meetingRoomInfo.get.roomDes
      rmManager ! ModifyRoom(roomName, roomDes)
    }

    override def exitFullScreen(): Unit = {

    }

    override def fullScreen(): Unit = {

    }

    override def kickSbOut(canvasId: Int): Unit = {
      log.info(s"点击强制某人退出房间，canvasId = $canvasId")
      val userId = partUserMap.get(canvasId)
      if(userId.nonEmpty) rmManager ! KickSbOut(userId.get)
    }

    override def applyForSpeak(): Unit = {
      log.info(s"点击申请发言")
      rmManager ! ApplyForSpeak
    }

    override def appointSbSpeak(): Unit = {
      if(isLiving){
        if(partInfoMap.nonEmpty){
          val speakerId = chooseAudienceDialog(toAppointSpeak = Some(true))
          log.info(s"点击指派某人发言，userId = $speakerId")
          speakerId.foreach(rmManager ! AppointSpeaker(_))
        } else {
          Boot.addToPlatform{
            WarningDialog.initWarningDialog("当前会议无其他人！")
          }
        }
      } else {
        Boot.addToPlatform{
          WarningDialog.initWarningDialog("会议未开始！")
        }
      }
    }

    override def handleSpeakApply(userId: Long, userName: String, accept: Boolean, newRequest: ApplySpeakListInfo): Unit = {
      log.info(s"点击处理某人发言申请: userId = $userId, userName = $userName, accept = $accept")
      if (!someoneSpeaking) {
        rmManager ! RmManager.SpeakAcceptance(userId, userName, accept)
        meetingScene.audObservableList.remove(newRequest)
        meetingScene.audSpeakApplyMap.remove(userId)
      } else {
        if (someoneSpeaking && !accept) {
          rmManager ! RmManager.SpeakAcceptance(userId, userName, accept)
          meetingScene.audObservableList.remove(newRequest)
          meetingScene.audSpeakApplyMap.remove(userId)

        } else {
          Boot.addToPlatform {
            WarningDialog.initWarningDialog(s"两人无法同时发言，请先结束当前发言!")
          }
        }
      }
    }

    override def stopSbSpeak(userId: Long): Unit = {
      log.info(s"点击结束某人发言，userId：$userId")
      rmManager ! StopSbSpeak(userId)
    }

    override def sendComment(comment: String): Unit = {
      log.info(s"点击发送留言：$comment")
      rmManager ! SendComment(RmManager.userInfo.get.userId, RmManager.meetingRoomInfo.get.roomId, comment)

    }

    override def controlOnesImage(orderNum: Int, targetStatus: Int): Unit = {
      log.info(s"点击控制某观众画面，canvasId = $orderNum, targetStatus = $targetStatus")
      val userId = partUserMap.get(orderNum)
      if(userId.nonEmpty) rmManager ! RmManager.ControlOthersImageAndSound(userId.get, targetStatus, 0)
    }

    override def controlOnesSound(orderNum: Int, targetStatus: Int): Unit = {
      log.info(s"点击控制某观众声音，canvasId = $orderNum, targetStatus = $targetStatus")
      val userId = partUserMap.get(orderNum)
      if(userId.nonEmpty) rmManager ! RmManager.ControlOthersImageAndSound(userId.get, 0, targetStatus)
    }

    override def controlSelfImage(targetStatus: Int): Unit = {
      log.info(s"点击控制自己画面，targetStatus: $targetStatus")
      selfImageStatus = targetStatus
      rmManager ! RmManager.ControlSelfImageAndSound(image = targetStatus)

    }

    override def controlSelfSound(targetStatus: Int): Unit = {
      log.info(s"点击控制自己声音，targetStatus: $targetStatus")
      selfSoundStatus = targetStatus
      rmManager ! RmManager.ControlSelfImageAndSound(sound = targetStatus)

    }

    override def invite(): Unit = {
      if(partUserMap.toList.length < 6){
        val info = inviteDialog()
        if(info.nonEmpty){
          rmManager ! RmManager.Invite(info.get)
        }
      } else {
        Boot.addToPlatform{
          WarningDialog.initWarningDialog("当前会议人数已满！")
        }
      }

    }

    override def leaveRoom(): Unit = {
      log.info(s"点击离开房间")
      rmManager ! LeaveRoom()

    }



  })

  def showScene(isHost: Boolean): Unit = {
    Boot.addToPlatform(
      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
        context.switchScene(meetingScene.getScene(isHost), title = s"会议室-${RmManager.roomInfo.get.roomName}")
      } else {
        WarningDialog.initWarningDialog(s"无房间信息！")
      }
    )
  }

  def addPartUser(userId: Long, userName: String): Unit = {
//    log.info(s"addPartUser !!!")
    if(partUserMap.keys.toList.length < 6){
      partInfoMap.put(userId, PartInfo(userName, 1, 1))
      val canvasId = List(1,2,3,4,5,6).filterNot(i => partUserMap.keys.toList.contains(i)).min
      log.info(s"为新用户分配canvas，id= $canvasId")
      partUserMap = partUserMap.updated(canvasId, userId)
      Boot.addToPlatform{
        meetingScene.nameLabelMap(canvasId).setText(userName)
        meetingScene.nameLabelMap(canvasId).addEventHandler(MouseEvent.MOUSE_CLICKED,(_: MouseEvent) => {
          val clickUserId = partUserMap.get(canvasId)
          if(clickUserId.nonEmpty){
            rmManager ! GetInfo(clickUserId.get)
          }
        })
        meetingScene.addLiveBarToCanvas(canvasId, userId)

      }
    }
  }

  def reducePartUser(userId: Long): Unit = {
//    log.info(s"reducePartUser !!!")
    val userReduced = partUserMap.find(_._2 == userId)
    if(userReduced.nonEmpty){
      partInfoMap.remove(userId)
      val canvasId = userReduced.get._1
      partUserMap = partUserMap - canvasId
      log.info(s"回收离开用户canvas，id = $canvasId")
      val imageCanvasBg = new Image("img/picture/background.jpg")
      meetingScene.canvasMap(canvasId)._2.drawImage(
        imageCanvasBg, 0, 0, Constants.DefaultPlayer.width/3, Constants.DefaultPlayer.height/3)
      Boot.addToPlatform{
        meetingScene.nameLabelMap(canvasId).setText("")
        meetingScene.removeLiveBarFromCanvas(canvasId)
      }
    }
  }

  //选择一名参会者弹窗
  def chooseAudienceDialog(toChangeHost: Option[Boolean] = None, toAppointSpeak: Option[Boolean] = None): Option[Long] = {
    val dialog = new Dialog[String]()
    if(toChangeHost.nonEmpty) dialog.setTitle("变更主持人")
    if(toAppointSpeak.nonEmpty) dialog.setTitle("指派发言人")

    val changeHostLabel = if(toChangeHost.nonEmpty) new Label(s"请选择新的会议主持人：") else new Label(s"请选择发言人：")

    val btnBox = new VBox(5)

    val toggleGroup = new ToggleGroup

    partInfoMap.map{ user =>
      val radioBtn = new RadioButton(s"${user._2.userName}")
      radioBtn.setToggleGroup(toggleGroup)
      radioBtn.setUserData(user._1)
      btnBox.getChildren.add(radioBtn)
    }
    btnBox.setAlignment(Pos.CENTER)
    btnBox.setPadding(new Insets(20,20,20,20))

    val wholeBox = new VBox(10, changeHostLabel, btnBox)

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(wholeBox)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (toggleGroup.getSelectedToggle != null) {
        if (dialogButton == confirmButton){
          toggleGroup.getSelectedToggle.getUserData.toString
        }
        else
          null
      } else {
        Boot.addToPlatform(
          WarningDialog.initWarningDialog("请选择一名参会者！")
        )
        null
      }
    )
    var changeHostOpt: Option[Long] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a != null)
        changeHostOpt = Some(a.toLong)
      else
        None
    }
    changeHostOpt
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

  //重置canvas背景
  def resetBack(canvasId: Int): Unit = {
    log.info(s"resetBack ########, canvasId: $canvasId")
    if(List(1,2,3,4,5,6).contains(canvasId)){
      Boot.addToPlatform{
        val imageCanvasBg = new Image("img/picture/background.jpg")
        meetingScene.canvasMap(canvasId)._2.drawImage(
          imageCanvasBg, 0, 0, Constants.DefaultPlayer.width/3, Constants.DefaultPlayer.height/3)
      }
    }
  }

  //突出显示发言者
  def emphasizeSpeaker(userId: Long, userName: String, toEmphasize: Boolean) = {
    val canvasIdOpt = partUserMap.find(_._2 == userId).map(_._1)
    if(List(1,2,3,4,5,6).contains(canvasIdOpt.getOrElse(-1))){
      val canvasId = canvasIdOpt.get
      Boot.addToPlatform{
        if(toEmphasize){
          val speakerIcon = new ImageView("img/icon/speaker.png")
          speakerIcon.setFitWidth(25)
          speakerIcon.setFitHeight(25)
          meetingScene.nameLabelMap(canvasId).setGraphic(speakerIcon)
          meetingScene.nameLabelMap(canvasId).setStyle("-fx-text-fill: #6495ED; -fx-font-weight: bolder;")

        } else {
          val unSpeakIcon = new ImageView("img/icon/unSpeak.png")
          unSpeakIcon.setFitWidth(25)
          unSpeakIcon.setFitHeight(25)
          meetingScene.nameLabelMap(canvasId).setGraphic(unSpeakIcon)
          meetingScene.nameLabelMap(canvasId).setStyle("-fx-text-fill: #000000; -fx-font-weight: normal;")

        }
      }
    }
  }

  def wsMessageHandle(data: WsMsgRm): Unit = {
    data match {
      case msg: HeatBeat =>
        log.info(s"rcv HeatBeat from rm: ${msg.ts}")
        rmManager ! HeartBeat

        //通知其余人：房间来了新用户
      case msg: UserInfoListRsp =>
        log.info(s"rcv UserInfoListRsp from rm: $msg")
        val addPartListOpt = msg.UserInfoList
        addPartListOpt.foreach{ addPartList =>
          addPartList.foreach{ partUser =>
            addPartUser(partUser.userId, partUser.userName)
          }
        }

        //通知其余人：某人离开房间
      case msg: LeftUserRsp =>
        log.info(s"rcv LeftUserRsp from rm: $msg")
        val userReduced = partUserMap.find(_._2 == msg.UserId)
        val canvasId = if(userReduced.nonEmpty) userReduced.get._1 else -1
        rmManager ! SomeoneLeave(msg.UserId, canvasId)
        reducePartUser(msg.UserId)

        //收到留言
      case msg: RcvComment =>
        log.info(s"rcv RcvComment from rm: $msg")
        Boot.addToPlatform {
          meetingScene.commentBoard.updateComment(msg)
        }

        //通知主持人：修改房间信息结果
      case msg: ModifyRoomRsp =>
        log.info(s"rcv ModifyRoomRsp from rm: $msg")
        if(msg.errCode != 0){
          Boot.addToPlatform{
            meetingScene.meetingNameField.setText(previousMeetingName)
            meetingScene.meetingDesField.setText(previousMeetingName)
            WarningDialog.initWarningDialog("修改房间信息失败！")
          }
          rmManager ! ModifyRoomFailed(previousMeetingName, previousMeetingDes)
        }

        //通知观众：房间信息被更改
      case msg: UpdateRoomInfo2Client =>
        log.info(s"rcv UpdateRoomInfo2Client from rm: $msg")
        Boot.addToPlatform{
          meetingScene.meetingNameValue.setText(msg.roomName)
          meetingScene.meetingDesValue.setText(msg.roomDec)
        }

        //通知主持人：更换主持人的结果
      case msg: ChangeHostRsp =>
        log.info(s"rcv ChangeHostRsp from rm: $msg")
        if(msg.errCode == 0){
          Boot.addToPlatform{
            meetingScene.meetingHostValue.setText(msg.userName)
            meetingScene.isHost = false
          }
        } else {
          rmManager ! TurnToHost
        }

        //通知原主持人以外的观众：新主持人信息
      case msg: ChangeHost2Client =>
        log.info(s"rcv ChangeHost2Client from rm: $msg")
        Boot.addToPlatform{
          meetingScene.meetingHostValue.setText(msg.userName)
        }
        if(msg.userId == RmManager.userInfo.get.userId){
          rmManager ! TurnToHost
          Boot.addToPlatform{
            meetingScene.isHost = true
            meetingScene.refreshScene(true)
            msg.audSpeakApplyMap.foreach{ i =>
              meetingScene.updateSpeakApplier(i._1, i._2)
            }
            meetingScene.liveToggleButton.setSelected(isLiving)
          }
        }

        //得到自己的liveId和liveCode（推）以及房间其余人的liveId（拉）
      case msg: StartMeetingRsp =>
        log.info(s"rcv StartMeetingRsp from rm: $msg")
        Boot.addToPlatform{
          meetingScene.meetingStateLabel.setText("会议进行中")
        }
        if(msg.errCode == 0){
          rmManager ! StartMeeting(msg.pushLiveInfo, msg.pullLiveIdList)
        } else {
          rmManager ! GetLiveInfoReq
        }

        //得到自己的liveId和liveCode
      case msg: GetLiveInfoRsp =>
        Boot.addToPlatform{
          meetingScene.meetingStateLabel.setText("会议进行中")
        }
        log.info(s"rcv GetLiveInfoRsp from rm: $msg")
        if(msg.errCode == 0){
          rmManager ! ToPush(msg.pushLiveInfo.get)
        } else {
          rmManager ! GetLiveInfoReq
        }

        //通知房间里其余人新拉一路流
      case msg: GetLiveId4Other =>
        log.info(s"rcv GetLiveInd4Other from rm: $msg")
        rmManager ! ToPull(msg.userId, msg.liveId)


        //通知某观众：你被主持人踢出房间
      case msg: ForceOut2Client =>
        log.info(s"rcv ForceOut2Client from rm: $msg")
        rmManager ! LeaveRoom(isKicked = true)

        //通知某观众：你被关闭/打开了声音/画面
      case msg: CloseSoundFrame2Client =>
        log.info(s"rcv CloseSoundFrame2Client from rm: $msg")
        Boot.addToPlatform{
          msg.frame match{
            case 1 =>
              meetingScene.selfCanvasBar.imageToggleButton.setSelected(true)
              selfImageStatus = 1
            case -1 =>
              meetingScene.selfCanvasBar.imageToggleButton.setSelected(false)
              selfImageStatus = -1
            case x => //do nothing
          }
        }
        Boot.addToPlatform{
          msg.sound match{
            case 1 =>
              meetingScene.selfCanvasBar.soundToggleButton.setSelected(true)
              selfSoundStatus = 1
            case -1 =>
              meetingScene.selfCanvasBar.soundToggleButton.setSelected(false)
              selfSoundStatus = -1
            case x => //do nothing
          }
        }
        rmManager ! ControlSelfImageAndSound(msg.frame, msg.sound)

        //通知主持人：某观众关闭/打开了声音/画面
      case msg: ClientCloseSoundFrame =>
        log.info(s"rcv ClientCloseSoundFrame from rm: $msg")
        val userId = msg.userId
        val userInfo = partInfoMap.find(_._1 == userId)
        val canvasId = partUserMap.find(l => l._2 == userId).map(_._1)
        if(canvasId.nonEmpty){
          Boot.addToPlatform{
            msg.sound match{
              case 1 =>
                meetingScene.liveBarMap(canvasId.get)._3.setSelected(true)
                if(userInfo.nonEmpty){
                  partInfoMap.put(userId,
                    PartInfo(userInfo.get._2.userName, 1, userInfo.get._2.soundStatus))
                }
              case -1 =>
                meetingScene.liveBarMap(canvasId.get)._3.setSelected(false)
                if(userInfo.nonEmpty){
                  partInfoMap.put(userId,
                    PartInfo(userInfo.get._2.userName, -1, userInfo.get._2.soundStatus))
                }
              case x => // do nothing
            }
            msg.frame match{
              case 1 =>
                meetingScene.liveBarMap(canvasId.get)._2.setSelected(true)
                if(userInfo.nonEmpty){
                  partInfoMap.put(userId,
                    PartInfo(userInfo.get._2.userName, userInfo.get._2.imageStatus, 1))
                }
              case -1 =>
                meetingScene.liveBarMap(canvasId.get)._2.setSelected(false)
                if(userInfo.nonEmpty){
                  partInfoMap.put(userId,
                    PartInfo(userInfo.get._2.userName, userInfo.get._2.imageStatus, -1))
                }
              case x => // do nothing
            }
          }
        }

        //通知主持人：某观众申请发言
      case msg: ApplySpeak2Host =>
        log.info(s"rcv ApplySpeak2Host from rm: $msg")
        Boot.addToPlatform{
          meetingScene.updateSpeakApplier(msg.userId, msg.userName)
        }

        //通知所有人：某观众开始发言
      case msg: SpeakingUser =>
        log.info(s"rcv SpeakingUser from rm: $msg")
        this.someoneSpeaking = true
        emphasizeSpeaker(msg.userId, msg.userName, true)
        Boot.addToPlatform{
          meetingScene.speakStateValue.setText(s"${msg.userName}")
          meetingScene.editControlSpeakBtn(toStop = true, userId = Some(msg.userId))
        }

        //通知所有人：某观众结束发言
      case msg: StopSpeakingUser =>
        log.info(s"rcv StopSpeakingUser from rm: $msg")
        this.someoneSpeaking = false
        emphasizeSpeaker(msg.userId, msg.userName, false)
        Boot.addToPlatform{
          meetingScene.speakStateValue.setText(s"无")
          meetingScene.editControlSpeakBtn(toAppoint = true)
        }

        //通知主持人：邀请某用户的反馈
      case msg: InviteOthersRsp =>
        log.info(s"rcv InviteOthersRsp from rm: $msg")
        if(isHost){
          if(msg.errCode == 0) {
            Boot.addToPlatform{
              WarningDialog.initWarningDialog("邀请邮件已发送！")
            }
          } else {
            Boot.addToPlatform{
              WarningDialog.initWarningDialog(s"${msg.msg}")
            }
          }
        }

      case msg: RcvUserInfo =>
        log.info(s"rcv RcvUserInfo from rm: $msg")

      case msg: StopMeetingRsp =>
        log.info(s"rcv StopMeetingRsp from rm: $msg")
        rmManager ! LeaveRoom(meetingStopped = true)


      case x =>
        log.warn(s"host recv unknown msg from rm: $x")
    }

  }




}
