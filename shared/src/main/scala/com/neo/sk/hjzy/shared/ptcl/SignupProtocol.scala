package com.neo.sk.hjzy.shared.ptcl

/**
  * Author: wqf
  * Date: 2019/3/27
  * Time: 21:04
  */
object SignupProtocol {
  case class UserSignupReq(
    userName: String,
    password: String
  )
}
