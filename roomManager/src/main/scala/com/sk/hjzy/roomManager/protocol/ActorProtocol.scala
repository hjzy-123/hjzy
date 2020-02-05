package com.sk.hjzy.roomManager.protocol

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.AuthProtocol.WsMsgClient
import com.sk.hjzy.roomManager.core.RoomManager.Command
import com.sk.hjzy.roomManager.core.{RoomActor, RoomManager, UserActor}

/**
  * created by benyafang on 2019.9.6 16:34
  * */
object ActorProtocol {

  trait RoomCommand extends RoomManager.Command with RoomActor.Command

  case class NewRoom(roomId: Long, roomName: String, roomDes: String, password: String) extends RoomManager.Command with RoomActor.Command

  case class UpdateSubscriber(join:Int,roomId:Long,userId:Long, userActorOpt:Option[ActorRef[UserActor.Command]]) extends RoomCommand

  case class WebSocketMsgWithActor(userId:Long,roomId:Long,msg:WsMsgClient) extends RoomCommand

  case class HostCloseRoom(roomId:Long) extends RoomCommand   //主持人结束会议

  case class StartMeeting(userId:Long,roomId:Long,actor:ActorRef[UserActor.Command]) extends RoomCommand


}
