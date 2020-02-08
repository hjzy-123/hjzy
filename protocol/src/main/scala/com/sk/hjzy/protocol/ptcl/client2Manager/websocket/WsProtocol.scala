package com.sk.hjzy.protocol.ptcl.client2Manager.websocket

import com.sk.hjzy.protocol.ptcl.CommonProtocol.LiveInfo

object WsProtocol {

  sealed trait WsMsgFront

  sealed trait WsMsgManager

  sealed trait WsMsgClient extends WsMsgFront

  case object CompleteMsgClient extends WsMsgFront

  case class FailMsgClient(ex: Exception) extends WsMsgFront

  sealed trait WsMsgRm extends WsMsgManager

  case object CompleteMsgRm extends WsMsgManager

  case class FailMsgRm(ex: Exception) extends WsMsgManager

  case class Wrap(ws: Array[Byte]) extends WsMsgRm

  case class TextMsg(msg: String) extends WsMsgRm

  case object DecodeError extends WsMsgRm

  /**
   *
   * 主播端
   *
   **/

  /*client发送*/
  sealed trait WsMsgHost extends WsMsgClient

  /*roomManager发送*/
  sealed trait WsMsgRm2Host extends WsMsgRm

  /*心跳包*/

  case object PingPackage extends WsMsgClient with WsMsgRm

  case class HeatBeat(ts: Long) extends WsMsgRm

  case object AccountSealed extends WsMsgRm// 被封号

  case object NoUser extends WsMsgRm

  case object NoAuthor extends WsMsgRm


  //todo 开始会议
  case class StartMeetingReq(
                              userId: Long,
                              token: String
                            ) extends WsMsgHost


//  val StartMeetingRefused = StartMeetingRsp(errCode = 200001, msg = "start live refused.")
//  val StartMeetingRefused4LiveInfoError = StartMeetingRsp(errCode = 200001, msg = "start live refused because of getting live info from distributor error.")

  /*修改房间信息*/

  case class ModifyRoomInfo(
                             roomName: Option[String] = None,
                             roomDes: Option[String] = None
                           ) extends WsMsgHost

  case class ModifyRoomRsp(errCode: Int = 0, msg: String = "ok") extends WsMsgRm2Host

  val ModifyRoomError = ModifyRoomRsp(errCode = 200010, msg = "modify room error.")

  /*指派主持人*/
  case class changeHost(
                         userId: Long
                       ) extends WsMsgHost

  case class changeHostRsp(userId: Long, userName: String,errCode: Int = 0, msg: String = "ok") extends WsMsgRm2Host


  /**
   *
   * 观众端
   *
   **/


  /*client发送*/
  sealed trait WsMsgAudience extends WsMsgClient

  /*room manager发送*/
  sealed trait WsMsgRm2Audience extends WsMsgRm

  case object HostCloseRoom extends WsMsgRm2Audience //房主关闭房间通知房间所有用户
  case class HostCloseRoom() extends WsMsgRm2Audience //房主关闭房间通知房间所有用户，class方便后台一些代码的处理

  case class UpdateRoomInfo2Client(
                                    roomName: String,
                                    roomDec: String
                                  ) extends WsMsgRm2Audience

  case class ChangeHost2Client(
                                 userId: Long,
                                 userName: String
                               ) extends WsMsgRm2Audience

  /**
   * 所有用户  群发消息
   **/

  case class PartUserInfo(userId: Long, userName: String, headImgUrl:String)

  case class UserInfoListRsp(
                              UserInfoList: Option[List[PartUserInfo]] = None,
                              errCode: Int = 0,
                              msg: String = "ok"
                       ) extends WsMsgRm

  case class LeftUserRsp(
                              UserId: Long ,
                              errCode: Int = 0,
                              msg: String = "ok"
                            ) extends WsMsgRm

  case class Comment(
                      userId: Long,
                      roomId: Long,
                      comment: String,
                      color:String = "#FFFFFF",
                      extension: Option[String] = None
                    ) extends WsMsgClient


  case class RcvComment(
                         userId: Long,
                         userName: String,
                         comment: String,
                         color:String = "#FFFFFF",
                         extension: Option[String] = None
                       ) extends WsMsgRm

  case class PushLiveInfo(userId:Long, liveInfo:Option[LiveInfo] = None)

  case class PullLiveList(LiveIdList: List[(Long, Option[String])])

  case class StartMeetingRsp(
                              pushLiveInfo: PushLiveInfo,
                              pullLiveList: PullLiveList,
                              errCode: Int = 0,
                              msg: String = "ok"
                            ) extends WsMsgRm


}
