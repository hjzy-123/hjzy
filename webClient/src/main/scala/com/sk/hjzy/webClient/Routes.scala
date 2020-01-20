package com.sk.hjzy.webClient

/**
  * Created by haoshuhan on 2018/6/4.
  */
object Routes {
  val base = "/hjzy/roomManager"

  object User{
    val baseUrl = base + "/webUser"
    def genVerifyCode(email: String) = baseUrl + s"/genVerifyCode?email=$email"
    val register = baseUrl + "/register"
    val login = baseUrl + "/login"
    val loginByEmail = baseUrl + "/loginByEmail"
    def genLoginVerifyCode(email: String) = baseUrl + s"/genLoginVerifyCode?email=$email"
  }
}
