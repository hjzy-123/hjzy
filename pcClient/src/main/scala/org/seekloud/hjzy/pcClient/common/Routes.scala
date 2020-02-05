package org.seekloud.hjzy.pcClient.common

import org.seekloud.hjzy.pcClient.common.AppSettings._


/**
  * Author: zwq
  * Date: 2020/2/4
  * Time: 14:21
  */
object Routes {

  /*roomManager*/
  val baseUrl: String = rmProtocol + "://" + rmDomain + "/" + rmUrl

  val userUrl: String = baseUrl + "/user"

  //登录
  def genLoginVerifyCode(email: String): String = userUrl + s"/genLoginVerifyCode?email=$email"//登录获取邮箱验证码
  val signInByMail: String = userUrl + "/signInByMail" //邮箱验证码登录
  val signIn: String = userUrl + "/signIn" //用户名密码登录

  //注册
  def genVerifyCode(email: String): String = userUrl + s"/genVerifyCode?email=$email" //注册获取邮箱验证码
  val signUp: String = userUrl + "/signUp" //注册

  val getRoomList: String = userUrl + "/getRoomList"
  val searchRoom: String = userUrl + "/searchRoom"
  val temporaryUser: String = userUrl + "/temporaryUser"
  val getRoomInfo: String = userUrl + "/getRoomInfo"



  //会议相关
  val meetingUrl: String =  baseUrl + "/Meeting"
  val newMeeting: String = meetingUrl + "/newMeeting"  //创建会议
  val joinMeeting: String = meetingUrl + "/joinMeeting" //加入会议
}
