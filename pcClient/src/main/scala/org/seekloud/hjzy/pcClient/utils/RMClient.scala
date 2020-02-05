package org.seekloud.hjzy.pcClient.utils

import java.io.File

import com.sk.hjzy.protocol.ptcl.client2Manager.http.Common._
import org.seekloud.hjzy.pcClient.common.Routes
import org.slf4j.LoggerFactory
import org.seekloud.hjzy.pcClient.Boot.executor
import io.circe.{Encoder, Json}
import io.circe.syntax._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 11:33
  */
object RMClient extends HttpUtil {

  import io.circe.generic.auto._
  import io.circe.parser.decode
  import io.circe.syntax._


  private val log = LoggerFactory.getLogger(this.getClass)

  //注册
  def register(username: String, pwd: String, verifyCode: String, email: String): Future[Either[Throwable, SuccessRsp]] = {
    val methodName = "register"
    val url = Routes.signUp
    val data = RegisterReq(username, pwd, verifyCode, email).asJson.noSpaces

    postJsonRequestSend(methodName, url, Nil, data, timeOut = 60 * 1000, needLogRsp = false).map {
      case Right(jsonStr) =>
        decode[SuccessRsp](jsonStr)
      case Left(error) =>
        log.debug(s"sign up error: $error")
        Left(error)
    }
  }

  //注册时获取邮箱验证码
  def genRegisterVerifyCode(email: String): Future[Either[Throwable, SuccessRsp]] = {
    val methodName = "genRegisterVerifyCode"
    val url = Routes.genVerifyCode(email)

    getRequestSend(methodName, url, Nil, needLogRsp = false).map{
      case Right(jsonStr) =>
        decode[SuccessRsp](jsonStr)
      case Left(error) =>
        log.debug(s"genRegisterVerifyCode error: $error")
        Left(error)
    }
  }

  //用户名密码登录
  def signInByName(userName: String, pwd: String): Future[Either[Throwable, SignInRsp]] = {

    val methodName = "signInByName"
    val url = Routes.signIn

    val data = LoginReq(userName, pwd).asJson.noSpaces
    log.debug(s"signIn by userName post data:$data")
    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
      case Right(jsonStr) =>
        decode[SignInRsp](jsonStr)
      case Left(error) =>
        log.debug(s"signIn by userName error: $error")
        Left(error)
    }

  }

  //邮箱验证码登录
  def signInByMail(email: String, verifyCode: String): Future[Either[Throwable, SignInRsp]] = {
    val methodName = "signInByMail"
    val url = Routes.signInByMail
    val data = LoginByEmailReq(email, verifyCode).asJson.noSpaces
    log.debug(s"signIn by mail post data: $data")
    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
      case Right(jsonStr) =>
        decode[SignInRsp](jsonStr)
      case Left(error) =>
        log.debug(s"signIn by mail error: $error")
        Left(error)
    }

  }

  //登录时获取邮箱验证码
  def genLoginVerifyCode(email: String): Future[Either[Throwable, SuccessRsp]] = {
    val methodName = "genLoginVerifyCode"
    val url = Routes.genLoginVerifyCode(email)

    getRequestSend(methodName, url, Nil, needLogRsp = false).map{
      case Right(jsonStr) =>
        decode[SuccessRsp](jsonStr)
      case Left(error) =>
        log.debug(s"genLoginVerifyCode error: $error")
        Left(error)
    }
  }

  //创建会议
  def createMeeting(roomId: Long, password: String, roomName: String, roomDes: String): Future[Either[Throwable, SuccessRsp]] = {
    val methodName = "createMeeting"
    val url = Routes.newMeeting
    val data = NewMeeting(roomId, roomName, roomDes, password).asJson.noSpaces
    log.debug(s"createMeeting post data: $data")
    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
      case Right(jsonStr) =>
        decode[SuccessRsp](jsonStr)
      case Left(error) =>
        log.debug(s"createMeeting parse error: $error")
        Left(error)
    }

  }

  //加入会议
  def joinMeeting(roomId: Long, passWord: String): Future[Either[Throwable, SuccessRsp]] = {
    val methodName = "joinMeeting"
    val url = Routes.joinMeeting
    val data = JoinMeeting(roomId, passWord).asJson.noSpaces
    log.debug(s"createMeeting post data: $data")
    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
      case Right(jsonStr) =>
        decode[SuccessRsp](jsonStr)
      case Left(error) =>
        log.debug(s"joinMeeting parse error: $error")
        Left(error)
    }
  }


//  def getTemporaryUser: Future[Either[Throwable, GetTemporaryUserRsp]] = {
//
//    val methodName = "getTemporaryUser"
//    val url = Routes.temporaryUser
//
//    getRequestSend(methodName, url, Nil, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[GetTemporaryUserRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"getTemporaryUser error: $error")
//        Left(error)
//    }
//
//  }
//
//  def getRoomInfo(userId: Long, token: String): Future[Either[Throwable, RoomInfoRsp]] = {
//
//    val methodName = "getRoomInfo"
//    val url = Routes.getRoomInfo
//
//    val data = GetRoomInfoReq(userId, token).asJson.noSpaces
//    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[RoomInfoRsp](jsonStr)
//      case Left(error) =>
//        log.error(s"user-$userId getRoomInfo error: $error")
//        Left(error)
//    }
//  }
//
//
//  def getRoomList: Future[Either[Throwable, RoomListRsp]] = {
//    val methodName = "getRoomList"
//    val url = Routes.getRoomList
//
//    getRequestSend(methodName, url, Nil, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[RoomListRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"getRoomList error.")
//        Left(error)
//
//    }
//
//  }
//
//  //获取录像列表及地址
//  def getRecordList(sortBy: String, pageNum: Int, pageSize: Int): Future[Either[Throwable, GetRecordListRsp]] = {
//    val methodName = "getRecordList"
//    val url = Routes.getRecordList(sortBy, pageNum, pageSize)
//
//    getRequestSend(methodName, url, Nil, needLogRsp = false).map {
//      case Right(jsonStr) =>
////        Future{
////          decode[GetRecordListRsp](jsonStr)
////        }.onComplete{
////          case Success(value) => log.debug(s"decode[GetRecordListRsp] success")
////          case Failure(exception) => log.debug(s"decode[GetRecordListRsp] failed: $exception")
////        }
//        decode[GetRecordListRsp](jsonStr)
//
//      case Left(error) =>
//        log.debug(s"getRecordList error.")
//        Left(error)
//
//    }
//
//  }
//
//  def searchRecord(roomId: Long, startTime: Long, userId: Option[Long]) : Future[Either[Throwable, SearchRecordRsp]] = {
//    val methodName = "searchRecord"
//    val url = Routes.searchRecord
//
//    val data = SearchRecord(roomId, startTime, System.currentTimeMillis(), userId).asJson.noSpaces
//
//    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[SearchRecordRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"searchRecord error: $error")
//        Left(error)
//    }
//
//  }
//
//  //主播页面获取录像列表 删除录像
//  def getRecordList2(roomId:Long): Future[Either[Throwable, GetAuthorRecordListRsp]] = {
//    val methodName = "getRecordList2"
//    val url = Routes.getAuthorRecordList(roomId)
//
//    getRequestSend(methodName, url, Nil, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[GetAuthorRecordListRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"getRecordList2 in host page error.")
//        Left(error)
//    }
//  }
//
//  def deleteRecord(recordId:Long): Future[Either[Throwable, CommonRsp]] = {
//    val methodName = "deleteRecord"
//    val url = Routes.deleteRecord
//    val data = AuthorDeleteRecordReq(recordId).asJson.noSpaces
//
//    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
//      case Right(rsp) =>
//        decode[CommonRsp](rsp)
//      case Left(error) =>
//        log.debug(s"deleteRecord error: $error")
//        Left(error)
//    }
//  }
//
//  //修改昵称
//  def changeUserName(userId: Long, newName: String): Future[Either[Throwable, CommonRsp]] = {
//    val methodName = "changeUserName"
//    val url = Routes.changeUserNameUrl(userId, newName)
//
//    getRequestSend(methodName, url, Nil, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[CommonRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"changeUserName error.")
//        Left(error)
//
//    }
//
//  }
//
//
//  def searchRoom(userId: Option[Long] = None, roomId: Long): Future[Either[Throwable, SearchRoomRsp]] = {
//
//    val methodName = "searchRoom"
//    val url = Routes.searchRoom
//
//    val data = SearchRoomReq(userId, roomId).asJson.noSpaces
//
//    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[SearchRoomRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"searchRoom error: $error")
//        Left(error)
//    }
//  }
//
//  //上传图片
//  def uploadImg(file: File, userId: Long, imgType: Int): Future[Either[Throwable, ImgChangeRsp]] = {
//    val methodName = "uploadImage"
//    val url = Routes.uploadImgUrl(imgType, userId)
//
//    val data = file
//    val fileName = file.getName
//    log.debug(s"uploadImg in RMClient, file:$file, fileName:$fileName, userId:$userId, imgType:$imgType")
//    postFileRequestSend(methodName, url, Nil, data, fileName).map {
//      case Right(jsonStr) =>
//        decode[ImgChangeRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"uploadImg error: $error")
//        Left(error)
//    }
//
//  }
//
//  //获取评论列表
//  def getRecCommentList(roomId: Long, recordTime: Long): Future[Either[Throwable, GetRecordCommentListRsp]] = {
//
//    val methodName = "getRecCommentList"
//    val url = Routes.getCommentList
//
//    val data = GetRecordCommentListReq(roomId, recordTime).asJson.noSpaces
//
//    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[GetRecordCommentListRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"getCommentList error: $error")
//        Left(error)
//    }
//  }
//
//  //增加评论
//  def addRecComment(
//    roomId:Long,          //录像的房间id
//    recordTime:Long,      //录像的时间戳
//    comment:String,       //评论内容
//    commentTime:Long,     //评论的时间
//    relativeTime: Long,   //相对视频的时间
//    commentUid:Long,      //评论的用户id
//    authorUidOpt:Option[Long] = None    //被评论的用户id，None--回复主播
//  ): Future[Either[Throwable, CommonRsp]] = {
//
//    val methodName = "addRecComment"
//    val url = Routes.sendComment
//
//    val data = AddRecordCommentReq(roomId, recordTime, comment, commentTime, relativeTime, commentUid, authorUidOpt).asJson.noSpaces
//
//    postJsonRequestSend(methodName, url, Nil, data, needLogRsp = false).map {
//      case Right(jsonStr) =>
//        decode[CommonRsp](jsonStr)
//      case Left(error) =>
//        log.debug(s"addRecComment error: $error")
//        Left(error)
//    }
//  }


}
