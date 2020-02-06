package org.seekloud.hjzy.pcClient.common

import org.seekloud.hjzy.pcClient.common.AppSettings._


/**
  * Author: zwq
  * Date: 2020/2/4
  * Time: 14:21
  */
object Routes {

  /*roomManager*/
  val baseUrl: String = rmProtocol + "://" + rmHostName + ":" + rmPort + "/" + rmUrl

  val userUrl: String = baseUrl + "/User"

  //登录
  def genLoginVerifyCode(email: String): String = userUrl + s"/genLoginVerifyCode?email=$email"//登录获取邮箱验证码
  val loginByEmail: String = userUrl + "/loginByEmail" //邮箱验证码登录
  val login: String = userUrl + "/login" //用户名密码登录

  //注册
  def genVerifyCode(email: String): String = userUrl + s"/genVerifyCode?email=$email" //注册获取邮箱验证码
  val signUp: String = userUrl + "/register" //注册

  val getRoomList: String = userUrl + "/getRoomList"
  val searchRoom: String = userUrl + "/searchRoom"
  val temporaryUser: String = userUrl + "/temporaryUser"
  val getRoomInfo: String = userUrl + "/getRoomInfo"


  //会议相关
  val meetingUrl: String =  baseUrl + "/Meeting"
  val newMeeting: String = meetingUrl + "/newMeeting"  //创建会议
  val joinMeeting: String = meetingUrl + "/joinMeeting" //加入会议


  val wsBase: String = rmWsProtocol + "://" + rmHostName + ":" + rmPort + "/" + rmUrl + "/user"
  def linkRoomManager(userId: Long, token: String, roomId: Long): String = wsBase + "/setupWebSocket" + s"?userId=$userId&token=$token&roomId=$roomId"

}
