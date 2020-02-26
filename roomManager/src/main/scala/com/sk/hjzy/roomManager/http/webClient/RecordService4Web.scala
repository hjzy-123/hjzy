package com.sk.hjzy.roomManager.http.webClient

import com.sk.hjzy.roomManager.http.{ServiceUtils, SessionBase}
import com.sk.hjzy.roomManager.utils.CirceSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.sk.hjzy.roomManager.Boot.{emailManager4Web, executor, roomManager, scheduler, timeout, userManager}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ErrorRsp, SuccessRsp}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.{GetUserInfoRsp, LoginByEmailReq, LoginReq, RegisterReq, ResetPassword, UpdateNameReq}
import com.sk.hjzy.roomManager.common.AppSettings
import com.sk.hjzy.roomManager.core.webClient.EmailManager
import com.sk.hjzy.roomManager.http.{ServiceUtils, SessionBase}
import com.sk.hjzy.roomManager.http.SessionBase.UserSession
import com.sk.hjzy.roomManager.models.dao.{CommentsDao, RecordDao, StatisticDao, UserInfoDao}
import com.sk.hjzy.roomManager.utils.{CirceSupport, FileUtil, SecureUtil}
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import akka.stream.Materializer
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.{Comment, GetCommentsRsp, GetRecordInfoRsp, GetRecordsRsp, Record,SearchRecord, SearchRecordRsp, SendCommentReq, UpdateAllowUserReq}
import java.text.SimpleDateFormat
import java.util.Date

import com.sk.hjzy.protocol.ptcl.CommonRsp

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Author: wqf
  * Date: 2020/1/23
  * Time: 16:35
  */
trait RecordService4Web extends CirceSupport with ServiceUtils with SessionBase{

  implicit val materializer: Materializer

  private val getMyRecords = (path("getMyRecords") & get){
    parameters('pageNum.as[Int], 'pageSize.as[Int]){
      (pageNum, pageSize) =>
        authUser{ user =>
          dealFutureResult{
            UserInfoDao.searchByName(user.playerName).map{ rst =>
              if(rst.isDefined){
                val roomId = rst.get.roomid
                dealFutureResult{
                  RecordDao.getRecordByRoomId(roomId, pageNum, pageSize).map{ rst1 =>
                    val total = rst1._1
                    val rsp = rst1._2.sortBy(_.starttime).map{ t =>
                      val url = s"http://${AppSettings.processorDomain}/hjzy/processor/getRecord/${roomId}/${t.starttime}/record.mp4"
                      Record(
                        t.id,
                        t.coverImg,
                        t.recordname,
                        url,
                        t.allowUser
                      )
                    }.toList
                    complete(GetRecordsRsp(total, rsp, 0, "ok"))
                  }
                }
              }else{
                complete(ErrorRsp(100001, "不存在该用户"))
              }
            }
          }
        }
    }
  }

  private val getOtherRecords = (path("getOtherRecords") & get){
    parameters('pageNum.as[Int], 'pageSize.as[Int]){
      (pageNum, pageSize) =>
        authUser{ user =>
          dealFutureResult{
            RecordDao.getAllRecord().map{ rst =>
              val des = rst.filter(record => record.allowUser.split("@").contains(user.playerName))

              val total = des.length
              val rsp = des.drop((pageNum - 1) * pageSize).take(pageSize).map{ t =>
                val url = s"http://${AppSettings.processorDomain}/hjzy/processor/getRecord/${t.roomid}/${t.starttime}/record.mp4"
                Record(
                  t.id,
                  t.coverImg,
                  t.recordname,
                  url,
                  t.allowUser
                )
              }.toList
              complete(GetRecordsRsp(total, rsp, 0, "ok"))
            }
          }
        }
    }
  }

  private val updateAllowUser = (path("updateAllowUser") & post){
    authUser{ _ =>
      entity(as[Either[Error, UpdateAllowUserReq]]){
        case Right(req) =>
          dealFutureResult{
            RecordDao.updateAllowUser(req.id, req.allowUser).map{rst =>
              complete(SuccessRsp(0, "ok"))
            }
          }
        case Left(value) =>
          complete(ErrorRsp(100001, "无效参数"))
      }

    }
  }

  private val getRecordInfo = (path("getRecordInfo") & get){
    parameters('recordId.as[Long]){ recordId =>
      authUser{ user =>
        dealFutureResult{
          RecordDao.searchRecordById(recordId).map{ recordOpt =>
            if(recordOpt.isEmpty){
              complete(ErrorRsp(100001, "无录像"))
            }else{
              val record = recordOpt.get
              val record1 = Record(record.id, record.coverImg, record.recordname, record.recordAddr, record.allowUser)
              dealFutureResult{
                UserInfoDao.searchById(user.playerId.toLong).map{ userOpt =>
                  val owner =
                    if(userOpt.get.roomid == record.roomid) true
                    else false
                  dealFutureResult{
                    RecordDao.getAllRecord().map{ records =>
                      val otherRecord =
                        if(owner){
                          records.filterNot(_.id == recordId).filter(_.roomid == userOpt.get.roomid).map{record =>
                            val url = s"http://${AppSettings.processorDomain}/hjzy/processor/getRecord/${record.roomid}/${record.starttime}/record.mp4"
                            Record(record.id, record.coverImg, record.recordname, url, record.allowUser)
                          }.toList
                        }else{
                          records.filterNot(_.id == recordId).filter(record => record.allowUser.split("@").contains(user.playerName)).map{record =>
                            val url = s"http://${AppSettings.processorDomain}/hjzy/processor/getRecord/${record.roomid}/${record.starttime}/record.mp4"
                            Record(record.id, record.coverImg, record.recordname, url, record.allowUser)
                          }.toList
                        }
                      complete(GetRecordInfoRsp(owner, record1, otherRecord, 0, "ok"))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private val getComments = (path("getComments") & get){
    parameters('recordId.as[Long]){recordId =>
      authUser{ _ =>
        dealFutureResult{
          CommentsDao.getComments(recordId).map{rst =>
            val comments = rst.sortBy(_.createtime).reverse.map{ comment =>
              val date = new Date(comment.createtime)
              val sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
              val createTime = sdf.format(date)
              Comment(comment.id, comment.content, comment.replyto,
                comment.`type`, comment.belongto, comment.recordid, createTime, comment.author, comment.authorImg)
            }.toList
            complete(GetCommentsRsp(comments, 0, "ok"))
          }
        }
      }
    }
  }

  private val deleteComment = (path("deleteComment") & get){
    parameters('commentId.as[Long]){commentId =>
      authUser{ user =>
        dealFutureResult{
          CommentsDao.deleteComment(commentId).map{ rst =>
            complete(SuccessRsp(0, "ok"))
          }
        }
      }
    }
  }

  private val sendComment = (path("sendComment") & post){
    authUser{user =>
      entity(as[Either[Error, SendCommentReq]]){
        case Right(req) =>
          dealFutureResult{
            UserInfoDao.searchByName(user.playerName).map{rst =>
              if(rst.isDefined){
                val authorImg = rst.get.headImg
                val createTime = System.currentTimeMillis()
                dealFutureResult{
                  CommentsDao.addComment(req.content, req.replyTo, req.rType, req.belongTo, req.recordId, createTime, user.playerName, authorImg).map{ rst =>
                    complete(SuccessRsp(0, "ok"))
                  }
                }
              }else{
                complete(ErrorRsp(100001, "不存在该用户"))
              }
            }
          }
        case Left(err) =>
          complete(ErrorRsp(100001, "无效参数"))
      }
    }

  }

  private val searchRecord = (path("searchRecord") & post) {
    entity(as[Either[Error, SearchRecord]]) {
      case Right(req) =>
        dealFutureResult {
          RecordDao.searchRecord(req.roomId, req.startTime).map {
            case Some(recordInfo) =>
              dealFutureResult {
                  RecordDao.updateViewNum(req.roomId, req.startTime, recordInfo.observeNum + 1).map{ r =>
                    //todo  processorDomain
                    val url = s"http://${AppSettings.processorDomain}/hjzy/processor/getRecord/${req.roomId}/${req.startTime}/record.mp4"
                    complete(SearchRecordRsp(url, recordInfo))
                  }
              }
            case None =>
              complete(CommonRsp(100070, s"没有该录像"))
          }
        }
      case Left(e) =>
        complete(CommonRsp(100070, s"parse error:$e"))
    }
  }


  val webRecordsRoute = pathPrefix("webRecords"){
    getMyRecords ~ updateAllowUser ~ getOtherRecords ~ getRecordInfo ~ getComments ~ sendComment ~ deleteComment ~ searchRecord
  }
}
