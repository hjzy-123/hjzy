package com.sk.hjzy.processor.utils

import com.sk.hjzy.processor.common.AppSettings._
import com.sk.hjzy.processor.Boot.executor
import com.sk.hjzy.processor.common.AppSettings._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
object RMClient extends HttpUtil{

  val url = s"http://$roomManagerHost/theia/roomManager/rtp/getLiveInfo"

  case class LiveInfo(liveId:String, liveCode:String)
  case class LiveRsp(liveInfo: Option[LiveInfo], errCode:Int = 0, msg:String = "ok")

  def getLiveId() = {
    val data = SecureUtil.genPostEnvelope(appId,System.nanoTime().toString,"{}",secureKey).asJson.noSpaces
    postJsonRequestSend("post", url,List(),data).map{
      case Right(value) =>
        decode[LiveRsp](value) match {
          case Right(live) =>
            if(live.errCode == 0) {
              Right(live.liveInfo.get)
            }else{
              Left(live.msg)
            }
          case Left(e) => Left(e)
        }
      case Left(e) =>
        log.error(s"ask liveId from rm error：$e")
        Left(e)
    }
  }

}
