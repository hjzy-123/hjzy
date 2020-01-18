package com.sk.hjzy.webClient

/**
  * Created by haoshuhan on 2018/6/4.
  */
object Routes {
  val base = "/hjzy"

  object User{
    val baseUrl = base + "/user"
    def genVerifyCode(email: String) = baseUrl + s"/genVerifyCode?email=$email"
  }
}
