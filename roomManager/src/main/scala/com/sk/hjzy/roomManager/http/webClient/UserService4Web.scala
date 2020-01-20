package com.sk.hjzy.roomManager.http.webClient

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.sk.hjzy.roomManager.Boot.{emailManager4Web, executor, roomManager, scheduler, timeout, userManager}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ErrorRsp, SuccessRsp}
import com.sk.hjzy.roomManager.core.webClient.EmailManager
import com.sk.hjzy.roomManager.http.ServiceUtils
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.Future

/**
  * Author: wqf
  * Date: 2020/1/20
  * Time: 14:54
  */
trait UserService4Web extends ServiceUtils {

  private val genVerifyCode = (path("genVerifyCode") & get){
    parameters('email.as[String]){ email =>
      val futureRsp: Future[Boolean] = emailManager4Web ? (EmailManager.GetVerifyCode4Register(email, _))
      dealFutureResult{
        futureRsp.map{rst =>
          if(rst) complete(SuccessRsp)
          else complete(ErrorRsp(100001, "获取验证码失败"))
        }
      }
    }
  }
}
