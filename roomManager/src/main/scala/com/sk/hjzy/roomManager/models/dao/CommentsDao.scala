package com.sk.hjzy.roomManager.models.dao

import com.sk.hjzy.protocol.ptcl.CommonInfo.{RecordInfo, UserInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.CommonProtocol.GetRecordListRsp
//import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.RecordData
import com.sk.hjzy.protocol.ptcl.distributor2Manager.DistributorProtocol.RecordData
import com.sk.hjzy.roomManager.utils.DBUtil._
import com.sk.hjzy.roomManager.models.SlickTables._
import slick.jdbc.MySQLProfile.api._
import com.sk.hjzy.roomManager.Boot.executor

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Author: wqf
  * Date: 2020/1/28
  * Time: 17:42
  */
object CommentsDao {
  
  def getComments(recordId: Long) = {
    db.run(tComments.filter(_.recordid === recordId).result)
  }

  def addComment(content: String, replyTo: String, rType: Int, belongTo:Long, recordId: Long, createTime: Long, author: String, authorImg: String) = {
    db.run(tComments += rComments(-1l, content, replyTo, rType, belongTo, recordId, createTime, author, authorImg))
  }

  def deleteComment(id: Long) = {
    db.run(tComments.filter(_.id === id).delete)
  }
}
