package com.sk.hjzy.protocol.ptcl.webClientManager

import com.sk.hjzy.protocol.ptcl.CommonProtocol.{RoomInfo, UserInfo}
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ComRsp, CommonRsp}

/**
  * Author: wqf
  * Date: 2020/1/19
  * Time: 1:03
  */
object UserProtocol {

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

  case class ResetPassword(
    email: String,
    password: String,
    verifyCode: String
  )

  case class GetUserInfoRsp(
    userName: String,
    headImg: String,
    errCode: Int,
    msg: String
  ) extends CommonRsp

  case class UpdateNameReq(
    name: String
  )
}
