package com.sk.hjzy.protocol.ptcl.client2Manager.http

import com.sk.hjzy.protocol.ptcl.CommonProtocol.{RoomInfo, UserInfo}
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

}
