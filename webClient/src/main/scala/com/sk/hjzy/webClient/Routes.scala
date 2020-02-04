package com.sk.hjzy.webClient

/**
  * Created by haoshuhan on 2018/6/4.
  */
object Routes {
  val base = "/hjzy/roomManager"

  object User{
    val baseUrl: String = base + "/User"
    def genVerifyCode(email: String): String = baseUrl + s"/genVerifyCode?email=$email"
    val register: String = baseUrl + "/register"
    val login: String = baseUrl + "/login"
    val loginByEmail: String = baseUrl + "/loginByEmail"
    def genLoginVerifyCode(email: String): String = baseUrl + s"/genLoginVerifyCode?email=$email"
    def checkEmail(email: String): String = baseUrl + s"/checkEmail?email=$email"
    def genPasswordVerifyCode(email: String): String = baseUrl + s"/genPasswordVerifyCode?email=$email"
    val resetPassword: String = baseUrl + "/resetPassword"
    val logout: String = baseUrl + "/logout"
    val getUserInfo: String = baseUrl + "/getUserInfo"
    val updateName: String = baseUrl + s"/updateName"
    val updateHeadImg: String = baseUrl + s"/updateHeadImg"
  }

  object Record{
    val baseUrl: String = base + "/webRecords"
    def getRecords(pageNum: Int, pageSize: Int): String = baseUrl + s"/getMyRecords?pageNum=$pageNum&pageSize=$pageSize"
    val updateAllowUser: String = baseUrl + "/updateAllowUser"
    def getOtherRecords(pageNum: Int, pageSize: Int): String = baseUrl + s"/getOtherRecords?pageNum=$pageNum&pageSize=$pageSize"
    def getRecordInfo(recordId: Long): String = baseUrl + s"/getRecordInfo?recordId=$recordId"
    def getComments(recordId: Long): String = baseUrl + s"/getComments?recordId=$recordId"
    val sendComment: String = baseUrl + "/sendComment"
    def deleteComment(commentId: Long): String = baseUrl + s"/deleteComment?commentId=$commentId"
  }

}
