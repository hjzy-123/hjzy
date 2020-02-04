package com.sk.hjzy.roomManager.models.dao

import com.sk.hjzy.roomManager.models.SlickTables
import slick.jdbc.MySQLProfile.api._
import com.sk.hjzy.roomManager.Boot.executor
import com.sk.hjzy.roomManager.utils.DBUtil._

import scala.concurrent.Future
/**
  * created by benyafang on 2019/9/23 16:20
  * */
object RecordCommentDAO {
  def addRecordComment(r:SlickTables.rRecordComment):Future[Long] = {
    db.run(SlickTables.tRecordComment.returning(SlickTables.tRecordComment.map(_.commentid)) += r)
  }

  def getRecordComment(roomId:Long,recordTime:Long): Future[scala.Seq[SlickTables.tRecordComment#TableElementType]] = {
    db.run(SlickTables.tRecordComment.filter(r => r.roomId === roomId && r.recordTime === recordTime).result)
  }

}
