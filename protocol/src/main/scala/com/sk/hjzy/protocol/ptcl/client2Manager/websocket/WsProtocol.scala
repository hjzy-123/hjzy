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

  case class StartMeetingReq(
                              userId: Long,
                              token: String
                            ) extends WsMsgHost

  case class StopMeetingReq(
                             userId: Long,
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
  case class ChangeHost(
                         userId: Long
                       ) extends WsMsgHost

  case class ChangeHostRsp(userId: Long, userName: String,errCode: Int = 0, msg: String = "ok") extends WsMsgRm2Host

  /*主持人屏蔽用户的声音或图像*/
  case class CloseSoundFrame(
                         userId: Long,
                         sound: Int = 0,   //0:不变   -1：屏蔽   1：恢复
                         frame: Int = 0
                       ) extends WsMsgHost

//  case class CloseSoundFrameRsp(
//                                 errCode: Int = 0,
//                                 msg: String = "ok"
//                               ) extends WsMsgRm2Host

  /*主持人强制某人退出会议*/
  case class ForceOut(userId: Long) extends WsMsgHost

//  case class ForceOutRsp(
//                          errCode: Int = 0,
//                          msg: String = "ok"
//                        ) extends WsMsgRm2Host

  /*主持人审批某人发言请求*/
  case class ApplySpeak2Host( userId: Long, userName: String ) extends WsMsgRm2Host   //申请发言用户id

  case class ApplySpeakAccept( userId: Long, userName: String, accept: Boolean) extends WsMsgHost //审批某个用户发言请求

//  case class SpeakAcceptRsp( errCode: Int = 0,  msg: String = "ok") extends WsMsgRm2Host

  /*主持人制定某人发言*/
  case class AppointSpeak(userId: Long, userName: String) extends WsMsgHost

//  case class AppointSpeakRsp( errCode: Int = 0,  msg: String = "ok") extends WsMsgRm2Host

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

  case class CloseSoundFrame2Client(
                              sound: Int = 0,   //0:不变   -1：屏蔽   1：恢复
                              frame: Int = 0
                            ) extends WsMsgRm2Audience

//  case class CloseSoundFrame2ClientRsp(
//                                        userId: Long,
//                                        errCode: Int = 0,
//                                        msg: String = "ok"
//                                      ) extends WsMsgAudience     //客户端是否成功关闭声音或画面

  case class ForceOut2Client(
                              userId: Long
                            ) extends WsMsgRm2Audience

  /*申请发言*/   //todo
  case class ApplySpeak(
                         userId: Long,
                         userName: String
                       ) extends WsMsgAudience

  case class ApplySpeakRsp(
                            errCode: Int = 0,
                            msg: String = "ok"
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

  case class StartMeetingRsp(
                              pushLiveInfo: Option[LiveInfo] = None,
                              pullLiveIdList: List[(Long, String)], //(userId, liveId)
                              errCode: Int = 0,
                              msg: String = "ok"
                            ) extends WsMsgRm

  //单独申请LiveInfo
  case class GetLiveInfoReq(
                              userId: Long
                            ) extends WsMsgClient

  case class GetLiveInfoRsp(
                             pushLiveInfo: Option[LiveInfo] = None,
                             errCode: Int = 0,
                             msg: String = "ok"
                           ) extends WsMsgRm

  case class GetLiveId4Other(
                        userId: Long,
                        liveId: String
                      ) extends WsMsgRm

  case class StopMeetingRsp(
                             errCode: Int = 0,
                             msg: String = "ok"
                           ) extends WsMsgRm

}
