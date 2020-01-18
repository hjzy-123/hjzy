package com.sk.hjzy.roomManager.service

import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ErrorRsp, SuccessRsp}
import org.slf4j.LoggerFactory
import com.sk.hjzy.roomManager.Boot.emailManager
import com.sk.hjzy.roomManager.core.webClient.EmailManager
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import scala.concurrent.Future
/**
  * Author: wqf
  * Date: 2020/1/19
  * Time: 0:54
  */
object UserService {

  val log = LoggerFactory.getLogger(this.getClass)
}

trait UserService extends ServiceUtils with BaseService with SessionBase{

  import UserService._

  private def genVerifyCode: Route = (path("genVerifyCode") & get){
    parameters('email.as[String]){ email =>
      val futureRsp: Future[Boolean] = emailManager ? (EmailManager.GetVerifyCode4Register(email, _))
      dealFutureResult{
        futureRsp.map{rst =>
          if(rst) complete(SuccessRsp())
          else complete(ErrorRsp(100001, "发送验证码失败"))
        }
      }
    }
  }

  val userRoute = pathPrefix("user"){
    genVerifyCode
  }
}
