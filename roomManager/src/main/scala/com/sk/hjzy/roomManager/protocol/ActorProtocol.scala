package com.sk.hjzy.roomManager.protocol

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.CommonInfo.LiveInfo
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol.WsMsgClient
import com.sk.hjzy.roomManager.core.{RoomActor, RoomManager, UserActor}

/**
  * created by benyafang on 2019.9.6 16:34
  * */
object ActorProtocol {

  trait RoomCommand extends RoomManager.Command with RoomActor.Command


}
