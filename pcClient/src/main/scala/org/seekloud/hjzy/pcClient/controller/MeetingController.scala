package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol.WsMsgRm
import org.seekloud.hjzy.pcClient.Boot
import org.seekloud.hjzy.pcClient.common.StageContext
import org.seekloud.hjzy.pcClient.component.WarningDialog
import org.seekloud.hjzy.pcClient.core.RmManager
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

  meetingScene.setListener(new MeetingSceneListener {
    override def startLive(): Unit = {

    }

    override def stopLive(): Unit = {

    }

    override def allowSbSpeak(): Unit = {

    }

    override def changeHost(): Unit = {

    }

    override def editMeetingDes(): Unit = {

    }

    override def editMeetingName(): Unit = {

    }

    override def exitFullScreen(): Unit = {

    }

    override def fullScreen(): Unit = {

    }

    override def kickSbOut(): Unit = {

    }

    override def refuseSbSpeak(): Unit = {

    }

    override def sendComment(): Unit = {

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



  })

  def showScene(): Unit = {
    Boot.addToPlatform(
      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
        context.switchScene(meetingScene.getScene, title = s"${RmManager.userInfo.get.userName}的直播间-${RmManager.roomInfo.get.roomName}")
      } else {
        WarningDialog.initWarningDialog(s"无房间信息！")
      }
    )
  }

  def wsMessageHandle(data: WsMsgRm): Unit = {
    data match {

      case x =>
        log.warn(s"host recv unknown msg from rm: $x")
    }

  }




}
