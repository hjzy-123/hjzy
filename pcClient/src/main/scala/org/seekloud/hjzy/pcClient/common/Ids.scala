package org.seekloud.hjzy.pcClient.common

import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2019/9/23
  * Time: 10:59
  */
object Ids {

  private[this] val log = LoggerFactory.getLogger(this.getClass)


  def getPlayId(roomId: Long, pusherId: Long): String = {
    s"room$roomId-pusher$pusherId"
  }

//  def getCameraOption(position: String): Int = {
//    position match {
//      case "左上" => 0
//      case "右上" => 1
//      case "右下" => 2
//      case "左下" => 3
//    }
//  }



}
