package org.seekloud.hjzy.pcClient.controller

import akka.actor.typed.ActorRef
import org.seekloud.hjzy.pcClient.common.StageContext
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


  })



}
