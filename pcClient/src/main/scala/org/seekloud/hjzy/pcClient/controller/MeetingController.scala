package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol._
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.StageContext
import org.seekloud.hjzy.pcClient.component.WarningDialog
import org.seekloud.hjzy.pcClient.core.RmManager
import org.seekloud.hjzy.pcClient.core.RmManager._
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
  rmManager: ActorRef[RmManager.RmCommand]) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  var partUserMap: Map[Int, Long] = Map() // canvas序号 -> userId
  var partInfoList: List[(Long, String)] = List() // (userId, userName)

  var previousMeetingName = ""
  var previousMeetingDes = ""

  meetingScene.setListener(new MeetingSceneListener {
    override def startLive(): Unit = {

    }

    override def stopLive(): Unit = {

    }

    override def allowSbSpeak(): Unit = {

    }

    override def changeHost(): Unit = {

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

    override def kickSbOut(): Unit = {

    }

    override def refuseSbSpeak(): Unit = {

    }

    override def sendComment(comment: String): Unit = {
      log.info(s"点击发送留言：$comment")
      rmManager ! SendComment(RmManager.userInfo.get.userId, RmManager.meetingRoomInfo.get.roomId, comment)

    }

    override def stopOnesImage(): Unit = {

    }

    override def stopOnesSound(): Unit = {

    }

    override def stopSbSpeak(): Unit = {

    }

    override def stopSelfImage(): Unit = {

    }

    override def stopSelfSound(): Unit = {

    }

    override def leaveRoom(): Unit = {
      log.info(s"点击离开房间")
      rmManager ! LeaveRoom

    }



  })

  def showScene(): Unit = {
    Boot.addToPlatform(
      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
        context.switchScene(meetingScene.getScene, title = s"会议室-${RmManager.roomInfo.get.roomName}")
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
      }

    }
  }

  def wsMessageHandle(data: WsMsgRm): Unit = {
    data match {
      case msg: HeatBeat =>
        log.info(s"rcv HeatBeat from rm: ${msg.ts}")
        rmManager ! HeartBeat

      case HostCloseRoom =>
        log.info(s"rcv HostCloseRoom from rm")
        rmManager ! HostClosedRoom

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
        val userId = msg.UserId
        reducePartUser(userId)

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



      case x =>
        log.warn(s"host recv unknown msg from rm: $x")
    }

  }




}
