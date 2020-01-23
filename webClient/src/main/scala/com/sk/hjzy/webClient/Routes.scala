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
    def checkEmail(email: String) = baseUrl + s"/checkEmail?email=$email"
    def genPasswordVerifyCode(email: String) = baseUrl + s"/genPasswordVerifyCode?email=$email"
    val resetPassword = baseUrl + "/resetPassword"
    val logout = baseUrl + "/logout"
    val getUserInfo = baseUrl + "/getUserInfo"
    val updateName = baseUrl + s"/updateName"
    val updateHeadImg = baseUrl + s"/updateHeadImg"
  }

  object Record{
    val baseUrl = base + "/webRecords"
    def getRecords(pageNum: Int, pageSize: Int) = baseUrl + s"/getMyRecords?pageNum=$pageNum&pageSize=$pageSize"
  }

}
