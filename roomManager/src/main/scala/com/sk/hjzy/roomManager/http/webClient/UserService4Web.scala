package com.sk.hjzy.roomManager.http.webClient

import java.net.{URLDecoder, URLEncoder}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.sk.hjzy.roomManager.Boot.{emailManager4Web, executor, roomManager, scheduler, timeout, userManager}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ErrorRsp, SuccessRsp}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.{GetUserInfoRsp, LoginByEmailReq, LoginReq, RegisterReq, ResetPassword}
import com.sk.hjzy.roomManager.common.AppSettings
import com.sk.hjzy.roomManager.core.webClient.EmailManager
import com.sk.hjzy.roomManager.http.{ServiceUtils, SessionBase}
import com.sk.hjzy.roomManager.http.SessionBase.UserSession
import com.sk.hjzy.roomManager.models.dao.UserInfoDao
import com.sk.hjzy.roomManager.utils.{CirceSupport, FileUtil, SecureUtil}
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import akka.stream.Materializer

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Author: wqf
  * Date: 2020/1/20
  * Time: 14:54
  */
trait UserService4Web extends CirceSupport with ServiceUtils with SessionBase{

  private val tokenExistTime = AppSettings.tokenExistTime * 1000L // seconds

  implicit val materializer: Materializer

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
                    complete(ErrorRsp(100003, "用户已注册"))
                  }else{
                    val timestamp = System.currentTimeMillis()
                    val token = SecureUtil.nonceStr(40)
                    dealFutureResult{
                      UserInfoDao.addUser(
                        req.email, req.userName,SecureUtil.getSecurePassword(req.password, req.email, timestamp),token,timestamp,SecureUtil.nonceStr(40)
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

  private val genLoginVerifyCode = (path("genLoginVerifyCode") & get){
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

  private val checkEmail = (path("checkEmail") & get) {
    parameters('email.as[String]){ email =>
      dealFutureResult{
        UserInfoDao.checkEmail(email).map{ user =>
          if(user.isDefined) complete(SuccessRsp(0, "ok"))
          else complete(ErrorRsp(100001, "邮箱未注册"))
        }
      }
    }
  }

  private val genPasswordVerifyCode = (path("genPasswordVerifyCode") & get){
    parameters('email.as[String]){ email =>
      dealFutureResult{
        UserInfoDao.checkEmail(email).map{ rst =>
          if(rst.isDefined){
            val futureRsp: Future[Boolean] = emailManager4Web ? (EmailManager.GenVerifyCode4Password(email, _))
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

  private val resetPassword = (path("resetPassword") & post){
    entity(as[Either[Error, ResetPassword]]){
      case Right(req) =>
        val verifyRsp: Future[Boolean] = emailManager4Web ? (EmailManager.Verify4Password(req.email, req.verifyCode, _))
        dealFutureResult{
          verifyRsp.map{ rst =>
            if(rst){
              dealFutureResult{
                UserInfoDao.checkEmail(req.email).map{ rst =>
                  if(rst.isDefined){
                    val password = SecureUtil.getSecurePassword(req.password, req.email, rst.get.createTime)
                    dealFutureResult{
                      UserInfoDao.updatePsw(rst.get.uid, password).map{ rst =>
                        complete(SuccessRsp(0, "ok"))
                      }.recover{
                        case e: Exception =>
                          println(s"update password error:${e.getMessage}")
                          complete(ErrorRsp(100007, "修改密码失败"))
                      }
                    }
                  }else{
                    complete(ErrorRsp(100006, "邮箱未注册"))
                  }
                }
              }
            }else{
              complete(ErrorRsp(100005, "验证码错误"))
            }
          }
        }
      case Left(err) =>
        complete(ErrorRsp(100003, "无效参数"))
    }
  }

  private val getUserInfo = (path("getUserInfo") & get){
    authUser{ user =>
      dealFutureResult{
        UserInfoDao.searchByName(user.playerName).map{rst =>
          if(rst.isDefined) complete(GetUserInfoRsp(user.playerName, rst.get.headImg, 0, "ok"))
          else complete(GetUserInfoRsp("", "", 100001, "获取信息失败"))
        }
      }
    }
  }

  private def storeFile(source: Source[ByteString, Any]): Directive1[java.io.File] = {
    val dest = java.io.File.createTempFile("hjzy", ".tmp")
    val file = source.runWith(FileIO.toPath(dest.toPath)).map(_ => dest)
    onComplete[java.io.File](file).flatMap {
      case Success(f) =>
        provide(f)
      case Failure(e) =>
        dest.deleteOnExit()
        failWith(e)
    }
  }

  private val updateInfo = (path("updateInfo") & post){
    authUser{user =>
      parameters('name.as[String]) {
        name =>
          fileUpload("fileUpload") {
            case (fileInfo, file) =>
              storeFile(file) { f =>
                val fileName = user.playerId + "." + fileInfo.fileName.split("\\.").last
                FileUtil.storeFile1(fileName, f, "data/headImg")
                f.deleteOnExit()
                dealFutureResult{
                  UserInfoDao.searchByName(user.playerName).map{ rst =>
                    if(rst.isDefined){
                      val player = rst.get
                      dealFutureResult{
                        UserInfoDao.updateNameAndImg(player.uid, name, s"/hjzy/roomManager/static/headImg/$fileName").map{rst2 =>
                          addSession(Map("playerName" -> name)){
                            complete(SuccessRsp(0, "ok"))
                          }
                        }
                      }
                    }else{
                      complete(ErrorRsp(100008, "无该用户信息"))
                    }
                  }
                }

              }
          }
      }
    }
  }


  private val logout = (path("logout") & get){
    authUser{ _ =>
      invalidateSession{
        complete(SuccessRsp(0, "ok"))
      }
    }
  }


  val webUserRoute: Route = pathPrefix("webUser"){
    genVerifyCode ~ register ~ login ~ genLoginVerifyCode ~ loginByEmail ~ checkEmail ~
    genPasswordVerifyCode ~ resetPassword ~ logout ~ getUserInfo ~ updateInfo
  }
}
