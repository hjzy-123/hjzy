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
import com.sk.hjzy.roomManager.models.dao.{RecordDao, UserInfoDao}
import com.sk.hjzy.roomManager.utils.{CirceSupport, FileUtil, SecureUtil}
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import akka.stream.Materializer
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.{GetRecordsRsp, Record}

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
                    val rsp = rst1._2.map{ t =>
                      Record(
                        t.id,
                        t.coverImg,
                        t.recordname,
                        t.recordAddr,
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


  val webRecordsRoute = pathPrefix("webRecords"){
    getMyRecords
  }
}
