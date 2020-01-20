package com.sk.hjzy.roomManager.http.webClient

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.sk.hjzy.roomManager.Boot.{emailManager4Web, executor, roomManager, scheduler, timeout, userManager}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ErrorRsp, SuccessRsp}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.{LoginByEmailReq, LoginReq, RegisterReq}
import com.sk.hjzy.roomManager.common.AppSettings
import com.sk.hjzy.roomManager.core.webClient.EmailManager
import com.sk.hjzy.roomManager.http.ServiceUtils
import com.sk.hjzy.roomManager.http.SessionBase.UserSession
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.utils.SecureUtil
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Author: wqf
  * Date: 2020/1/20
  * Time: 14:54
  */
trait UserService4Web extends ServiceUtils {

  private val tokenExistTime = AppSettings.tokenExistTime * 1000L // seconds

  private val genVerifyCode = (path("genVerifyCode") & get){
    parameters('email.as[String]){ email =>
      val futureRsp: Future[Boolean] = emailManager4Web ? (EmailManager.GetVerifyCode4Register(email, _))
      dealFutureResult{
        futureRsp.map{rst =>
          println(s"rrr rst:$rst")
          if(rst) complete(SuccessRsp(0, "ok"))
          else complete(ErrorRsp(100001, "获取验证码失败"))
        }
      }
    }
  }

  private val register = (path("register") & post){
    entity(as[Either[Error, RegisterReq]]){
      case Right(req) =>
        val verify: Future[Boolean] = emailManager4Web ? (EmailManager.Verify4Register(req.email, req.verifyCode, _))
        dealFutureResult{
          verify.map{ rst =>
            if(rst){
              dealFutureResult{
                UserInfoDao.checkUser(req.email, req.userName).map{ userOpt =>
                  if(userOpt.isDefined){
                    complete(ErrorRsp(100003, "用户名已注册"))
                  }else{
                    val timestamp = System.currentTimeMillis()
                    val token = SecureUtil.nonceStr(40)
                    dealFutureResult{
                      UserInfoDao.addUser(
                        req.email, req.userName,SecureUtil.getSecurePassword(req.userName, req.email, timestamp),token,timestamp,SecureUtil.nonceStr(40)
                      ).map{ res =>
                        complete(SuccessRsp(0, "ok"))
                      }
                    }
                  }
                }
              }
            }else{
              complete(ErrorRsp(100002, "验证码错误"))
            }
          }
        }

      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }

  private val login = (path("login") & post){
    entity(as[Either[Error, LoginReq]]){
      case Right(req) =>
        dealFutureResult{
          UserInfoDao.searchByName(req.userName).map{
            case Some(rst) =>
              if (rst.password != SecureUtil.getSecurePassword(req.password, rst.email, rst.createTime)) {
                complete(ErrorRsp(100005, "密码错误"))
              }else if(rst.tokenCreateTime + tokenExistTime < System.currentTimeMillis()){
                println("update token")
                val token = SecureUtil.nonceStr(40)
                UserInfoDao.updateToken(rst.uid, token, System.currentTimeMillis())
                val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                addSession(session) {
                  complete(SuccessRsp(0, "ok"))
                }
              }else{
                val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                addSession(session) {
                  complete(SuccessRsp(0, "ok"))
                }
              }
            case None =>
              complete(100004, "不存在该用户")
          }
        }
      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }

  def genLoginVerifyCode = (path("genLoginVerifyCode") & get){
    parameters('email.as[String]){email =>
      dealFutureResult{
        UserInfoDao.checkEmail(email).map{ rst =>
          if(rst.isDefined){
            val futureRsp: Future[Boolean] = emailManager4Web ? (EmailManager.GenVerifyCode4Login(email, _))
            dealFutureResult{
              futureRsp.map{ rst =>
                if(rst) complete(SuccessRsp(0, "ok"))
                else complete(ErrorRsp(100007, "获取验证码失败"))
              }
            }
          }else{
            complete(100006, "该邮箱未注册")
          }

        }
      }
    }
  }

  private val loginByEmail = (path("loginByEmail") & post){
    entity(as[Either[Error, LoginByEmailReq]]){
      case Right(req) =>
        dealFutureResult{
          UserInfoDao.checkEmail(req.email).map{
            case Some(rst) =>
              dealFutureResult{
                val futureRsp: Future[Boolean] = emailManager4Web ? (EmailManager.Verify4Login(req.email, req.verifyCode, _))
                futureRsp.map{
                  case true =>
                    if(rst.tokenCreateTime + tokenExistTime < System.currentTimeMillis()){
                      println("update token")
                      val token = SecureUtil.nonceStr(40)
                      UserInfoDao.updateToken(rst.uid, token, System.currentTimeMillis())
                      val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                      addSession(session) {
                        complete(SuccessRsp(0, "ok"))
                      }
                    }else{
                      val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                      addSession(session) {
                        complete(SuccessRsp(0, "ok"))
                      }
                    }
                  case false =>
                    complete(100005, "验证码错误")
                }
              }
            case None =>
              complete(100004, "未注册邮箱")
          }
        }

      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }


  val webUserRoute: Route = pathPrefix("webUser"){
    genVerifyCode ~ register ~ login ~ genLoginVerifyCode ~ loginByEmail
  }
}
