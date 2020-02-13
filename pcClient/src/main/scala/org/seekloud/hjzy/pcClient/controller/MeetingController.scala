package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol._
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
import org.seekloud.hjzy.pcClient.core.RmManager.{GetLiveInfoReq, StartMeetingReq, _}
import org.seekloud.hjzy.pcClient.scene.HomeScene.HomeSceneListener
import org.seekloud.hjzy.pcClient.scene.MeetingScene
import org.seekloud.hjzy.pcClient.scene.MeetingScene.MeetingSceneListener
import org.slf4j.LoggerFactory

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

  var isHost: Boolean = this.ifHostWhenCreate

  var isLiving: Boolean = false

  var partUserMap: Map[Int, Long] = Map() // canvas序号 -> userId
  var partInfoList: List[(Long, String)] = List() // (userId, userName)

  var previousMeetingName = ""
  var previousMeetingDes = ""

  meetingScene.setListener(new MeetingSceneListener {
    override def startLive(): Unit = {
      rmManager ! StartMeetingReq
    }

    override def stopLive(): Unit = {
      rmManager ! RmManager.StopMeetingReq
    }

    override def changeHost(): Unit = {
      if(partInfoList.nonEmpty){
        val newHostId = chooseAudienceDialog(toChangeHost = Some(true))
        newHostId.foreach(rmManager ! TurnToAudience(_))
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

    }

    override def allowSbSpeak(): Unit = {

    }

    override def appointSbSpeak(): Unit = {
      if(isLiving){
        if(partInfoList.nonEmpty){
          val speakerId = chooseAudienceDialog(toAppointSpeak = Some(true))
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

    override def refuseSbSpeak(): Unit = {

    }

    override def stopSbSpeak(): Unit = {

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
      rmManager ! RmManager.ControlSelfImageAndSound(targetStatus, 0)
    }

    override def controlSelfSound(targetStatus: Int): Unit = {
      log.info(s"点击控制自己声音，targetStatus: $targetStatus")
      rmManager ! RmManager.ControlSelfImageAndSound(0, targetStatus)
    }

    override def leaveRoom(): Unit = {
      log.info(s"点击离开房间")
      rmManager ! LeaveRoom

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
    if(partUserMap.keys.toList.length < 6){
      partInfoList = (userId, userName) :: partInfoList
      val num = List(1,2,3,4,5,6).filterNot(i => partUserMap.keys.toList.contains(i)).min
      partUserMap = partUserMap.updated(num, userId)
      Boot.addToPlatform{
        meetingScene.nameLabelMap(num).setText(userName)
        if(isHost) meetingScene.addLiveBarToCanvas(num)

      }

    }
  }

  def reducePartUser(userId: Long): Unit = {
    val userReduced = partUserMap.find(_._2 == userId)
    if(userReduced.nonEmpty){
      partInfoList = partInfoList.filterNot(_._1 == userId)
      val num = userReduced.get._1
      partUserMap = partUserMap - num
      Boot.addToPlatform{
        meetingScene.nameLabelMap(num).setText("")
        if(isHost) meetingScene.removeLiveBarFromCanvas(num)
      }
    }
  }

  //变更主持人弹窗
  def chooseAudienceDialog(toChangeHost: Option[Boolean] = None, toAppointSpeak: Option[Boolean] = None): Option[Long] = {
    val dialog = new Dialog[String]()
    if(toChangeHost.nonEmpty) dialog.setTitle("变更主持人")
    if(toAppointSpeak.nonEmpty) dialog.setTitle("指派发言人")

    val changeHostLabel = if(toChangeHost.nonEmpty) new Label(s"请选择新的会议主持人：") else new Label(s"请选择发言人：")

    val btnBox = new VBox(5)

    val toggleGroup = new ToggleGroup

    partInfoList.map{ user =>
      val radioBtn = new RadioButton(s"${user._2}")
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


  def wsMessageHandle(data: WsMsgRm): Unit = {
    data match {
      case msg: HeatBeat =>
        log.info(s"rcv HeatBeat from rm: ${msg.ts}")
        rmManager ! HeartBeat

      case msg: UserInfoListRsp =>
        log.info(s"rcv UserInfoListRsp from rm: $msg")
        val addPartListOpt = msg.UserInfoList
        addPartListOpt.foreach{ addPartList =>
          addPartList.foreach{ partUser =>
            addPartUser(partUser.userId, partUser.userName)
          }
        }

      case msg: LeftUserRsp =>
        log.info(s"rcv LeftUserRsp from rm: $msg")
        rmManager ! SomeoneLeft(msg.UserId)
        reducePartUser(msg.UserId)

      case msg: RcvComment =>
        log.info(s"rcv RcvComment from rm: $msg")
        Boot.addToPlatform {
          meetingScene.commentBoard.updateComment(msg)
        }

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

      case msg: UpdateRoomInfo2Client =>
        log.info(s"rcv UpdateRoomInfo2Client from rm: $msg")
        Boot.addToPlatform{
          meetingScene.meetingNameValue.setText(msg.roomName)
          meetingScene.meetingDesValue.setText(msg.roomDec)
        }

      case msg: ChangeHostRsp =>
        log.info(s"rcv ChangeHostRsp from rm: $msg")
        if(msg.errCode == 0){
          Boot.addToPlatform{
            meetingScene.meetingHostValue.setText(msg.userName)
          }
          isHost = false
        } else {
          rmManager ! TurnToHost
        }

      case msg: ChangeHost2Client =>
        log.info(s"rcv ChangeHost2Client from rm: $msg")
        Boot.addToPlatform{
          meetingScene.meetingHostValue.setText(msg.userName)
        }
        if(msg.userId == RmManager.userInfo.get.userId){
          rmManager ! TurnToHost
          isHost = true
        }

      case msg: StartMeetingRsp =>
        log.info(s"rcv StartMeetingRsp from rm: $msg")
        if(msg.errCode == 0){
          rmManager ! StartMeeting(msg.pushLiveInfo, msg.pullLiveIdList)
        } else {
          rmManager ! GetLiveInfoReq
        }

      case msg: GetLiveInfoRsp =>
        log.info(s"rcv GetLiveInfoRsp from rm: $msg")
        if(msg.errCode == 0){
          rmManager ! ToPush(msg.pushLiveInfo.get)
        } else {
          rmManager ! GetLiveInfoReq
        }

      case msg: GetLiveId4Other =>
        log.info(s"rcv GetLiveInd4Other from rm: $msg")
        rmManager ! ToPull(msg.userId, msg.liveId)


      case msg: ForceOut2Client =>
        log.info(s"rcv ForceOut2Client from rm: $msg")
        rmManager ! LeaveRoom

      case msg: CloseSoundFrame2Client =>
        log.info(s"rcv CloseSoundFrame2Client from rm: $msg")
        Boot.addToPlatform{
          msg.frame match{
            case 1 => meetingScene.selfImageToggleBtn.setSelected(false)
            case -1 => meetingScene.selfImageToggleBtn.setSelected(true)
            case x => //do nothing
          }
        }
        Boot.addToPlatform{
          msg.sound match{
            case 1 => meetingScene.selfSoundToggleBtn.setSelected(false)
            case -1 => meetingScene.selfSoundToggleBtn.setSelected(true)
            case x => //do nothing
          }
        }
        rmManager ! ControlSelfImageAndSound(msg.frame, msg.sound)

      case x =>
        log.warn(s"host recv unknown msg from rm: $x")
    }

  }




}
