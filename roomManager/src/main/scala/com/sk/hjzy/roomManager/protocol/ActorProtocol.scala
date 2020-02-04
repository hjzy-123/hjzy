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

  case class WebSocketMsgWithActor(userId:Long,roomId:Long,msg:WsMsgClient) extends RoomCommand

  case class UpdateSubscriber(join:Int,roomId:Long,userId:Long,temporary:Boolean,userActorOpt:Option[ActorRef[UserActor.Command]]) extends RoomCommand

  case class StartRoom4Anchor(userId:Long,roomId:Long,actor:ActorRef[UserActor.Command]) extends RoomCommand

  case class UserLeftRoom(userId:Long,temporary:Boolean,roomId:Long) extends RoomCommand

  final case class StartLiveAgain(roomId:Long) extends RoomCommand

  case class HostCloseRoom(roomId:Long) extends RoomCommand// 主播关闭房间

  case class AddUserActor4Test(userId:Long,roomId:Long,userActor: ActorRef[UserActor.Command])extends RoomCommand


  case class BanOnAnchor(roomId:Long) extends RoomCommand
}
