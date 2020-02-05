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
  val signInByMail: String = userUrl + "/signInByMail"
  val signUp: String = userUrl + "/signUp"
  val signIn: String = userUrl + "/signIn"
  val getRoomList: String = userUrl + "/getRoomList"
  val searchRoom: String = userUrl + "/searchRoom"
  val temporaryUser: String = userUrl + "/temporaryUser"
  val getRoomInfo: String = userUrl + "/getRoomInfo"

  //会议相关
  val meetingUrl: String =  baseUrl + "/Meeting"
  val newMeeting: String = meetingUrl + "/newMeeting"
  val joinMeeting: String = meetingUrl + "/joinMeeting"
}
