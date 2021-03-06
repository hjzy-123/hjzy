package com.sk.hjzy.protocol.ptcl.client2Manager.http

import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo, UserInfo}
import com.sk.hjzy.protocol.ptcl.Response

object Common {

  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  class ComRsp(
                val errCode: Int = 0,
                val msg: String = "ok"
              ) extends CommonRsp

  final case class ErrorRsp(
                             override val errCode: Int,
                             override val msg: String
                           ) extends ComRsp

  final case class SuccessRsp(
                               override val errCode: Int = 0,
                               override val msg: String = "ok"
                             ) extends ComRsp

  /**
   * 注册 & 登录
   *
   * POST
   *
   **/
  case class RegisterReq(
                          userName: String,
                          password: String,
                          verifyCode: String,
                          email: String
                        )

  case class LoginReq(
                       userName: String,
                       password: String
                     )

  case class LoginByEmailReq(
                              email: String,
                              verifyCode: String
                            )

  case class SignInRsp(
                        userInfo: Option[UserInfo] = None,
                        roomInfo: Option[RoomInfo] = None,
                        errCode: Int = 0,
                        msg: String = "ok"
                      ) extends CommonRsp

  /**
   * 创建会议 & 加入会议
   *
   * POST
   *
   **/

  case class NewMeeting(
                         userId: Long,
                         roomId: Long,
                         roomName: String,
                         roomDes: String,
                         password: String,
                         invitees: List[String]
                        )

  case class NewMeetingRsp(
                            roomInfo: Option[RoomInfo],
                            errCode: Int = 0,
                            msg: String = "ok"
                          )extends CommonRsp

  case class JoinMeeting(
                         roomId: Long,
                         password: String
                       )

  case class JoinMeetingRsp(
                             roomInfo:Option[RoomInfo],
                             errCode: Int = 0,
                             msg: String = "ok"
                          )extends CommonRsp

  /**
   * 获取liveinfo
   **/
  case class GetLiveInfoRsp(
                             liveInfo: LiveInfo,
                             errCode: Int = 0,
                             msg: String = "ok"
                           ) extends Response

  case class GetLiveInfoRsp4RM(
                                liveInfo: Option[LiveInfo],
                                errCode: Int = 0,
                                msg: String = "ok"
                              ) extends Response


}
