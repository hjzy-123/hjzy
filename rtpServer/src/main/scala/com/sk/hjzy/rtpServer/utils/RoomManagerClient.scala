package com.sk.hjzy.rtpServer.utils

import com.sk.hjzy.rtpServer.common.AppSettings
import com.sk.hjzy.rtpServer.protocol.RoomManagerProtocol.PostRsp
import com.sk.hjzy.rtpServer.common.AppSettings
import com.sk.hjzy.rtpServer.protocol.RoomManagerProtocol._

import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.slf4j.LoggerFactory
import com.sk.hjzy.rtpServer.Boot.executor

import scala.concurrent.Future

/**
  * Created by haoshuhan on 2019/7/16.
  */
object RoomManagerClient extends HttpUtil with CirceSupport{

  private val log = LoggerFactory.getLogger("neo.sk.hjzy.rtpServer.utils.RoomManagerClient")

  val domain: String = AppSettings.roomManagerDomain
  val appId: String = AppSettings.roomManagerAppId
  val secureKey: String = AppSettings.roomManagerSecureKey
  val url: String = AppSettings.roomManagerProtocol + "://" + AppSettings.roomManagerHost + ":" + AppSettings.roomManagerPort + "/theia/roomManager/rtp/verify"


  def getVarified(liveId: String, liveCode: String) = {

    val sn = appId + System.currentTimeMillis().toString

    val sendData = SendPostData(LiveInfo(liveId, liveCode)).asJson.noSpaces

//    val (timestamp, nonce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, sendData), secureKey)

//    val params = SendReq(appId, sn, timestamp, nonce, signature, sendData).asJson.noSpaces

    postJsonRequestSend(s"postUrl $domain", url, Nil, sendData).map{
      case Right(str) =>
        decode[PostRsp](str) match {
          case Right(rsp) =>
            if(rsp.errCode == 0) {
              if (rsp.access) Right("Verified")
              else Left("Unverified")
            }else{
              Left(s"error:errorCode:${rsp.errCode}")
            }
          case Left(e) =>
            log.error(s"decode error.$e")
            Left("error")
        }
      case Left(error) =>
        log.error(s"verifyIdentity $url parse error.$error")
        Left("error")
    }
  }

  def main(args: Array[String]): Unit = {
    getVarified("11", "aaa").map{
      case Right(rsp) =>
      case Left(error) =>
    }
  }

}

