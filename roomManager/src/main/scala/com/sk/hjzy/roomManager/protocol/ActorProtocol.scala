package com.sk.hjzy.roomManager.protocol

import akka.actor.typed.ActorRef
import com.sk.hjzy.protocol.ptcl.CommonProtocol.RoomInfo
import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common.{JoinMeetingRsp, NewMeetingRsp}
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.WsMsgClient
import com.sk.hjzy.roomManager.core.RoomManager.Command
import com.sk.hjzy.roomManager.core.UserActor.Command
import com.sk.hjzy.roomManager.core.{RoomActor, RoomManager, UserActor, UserManager}

/**
  * created by benyafang on 2019.9.6 16:34
  * */
object ActorProtocol {

  trait RoomCommand extends RoomManager.Command with RoomActor.Command

  case class NewRoom(userId:Long, roomId: Long, roomName: String, roomDes: String, password: String, replyTo: ActorRef[NewMeetingRsp]) extends RoomCommand

  case class JoinRoom(roomId: Long, password: String,replyTo: ActorRef[JoinMeetingRsp]) extends RoomCommand

  case class GetUserInfoList(roomId:Long, userId: Long) extends RoomCommand

  case class UpdateSubscriber(join:Int,roomId:Long,userId:Long, userActorOpt:Option[ActorRef[UserActor.Command]]) extends RoomCommand

  case class WebSocketMsgWithActor(userId:Long,roomId:Long,msg:WsMsgClient) extends RoomCommand

//  case class HostCloseRoom(roomId:Long) extends RoomCommand   //主持人结束会议

  case class HostLeaveRoom(roomId:Long) extends RoomCommand   //主持人结束会议

  case class Stop(roomId: Long) extends RoomCommand
//  case class StartMeeting(userId:Long,roomId:Long,actor:ActorRef[UserActor.Command]) extends RoomCommand

  trait UserCommand extends UserManager.Command with UserActor.Command

  final case class ChangeBehaviorToHost(userId:Long, hostId: Long) extends UserCommand

  final case class ChangeBehaviorToParticipant(userId:Long, hostId: Long) extends UserCommand


}
