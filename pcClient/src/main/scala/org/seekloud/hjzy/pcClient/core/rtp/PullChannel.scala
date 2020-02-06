package org.seekloud.hjzy.pcClient.core.rtp

import java.net.InetSocketAddress

import org.slf4j.LoggerFactory

/**
  * User: TangYaruo
  * Date: 2019/8/20
  * Time: 21:55
  */
class PullChannel {
  private val log = LoggerFactory.getLogger(this.getClass)

  /*PULL*/
  val serverPullAddr = new InetSocketAddress(rtpServerHost, rtpServerPullPort)
}
