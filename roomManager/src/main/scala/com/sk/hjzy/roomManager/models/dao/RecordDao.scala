package com.sk.hjzy.roomManager.models.dao

import java.util

import com.sk.hjzy.protocol.ptcl.CommonProtocol.{UserInfo,RecordInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.http.CommonProtocol.GetRecordListRsp
//import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.RecordData
import com.sk.hjzy.protocol.ptcl.distributor2Manager.DistributorProtocol.RecordData
import com.sk.hjzy.roomManager.utils.DBUtil._
import com.sk.hjzy.roomManager.models.SlickTables._
import slick.jdbc.MySQLProfile.api._
import com.sk.hjzy.roomManager.Boot.executor

import scala.collection.mutable
import scala.concurrent.Future

object RecordDao {

  def addRecord(roomId:Long, recordName:String, recordDes:String, startTime:Long, coverImg:String, viewNum:Int, likeNum:Int,duration:String, recordAddr: String, allowUser:String) = {
    db.run(tRecord += rRecord(1, roomId, startTime, coverImg, recordName, recordDes, viewNum, likeNum,duration, recordAddr,allowUser))
  }

  def searchRecord(roomId:Long, startTime:Long):Future[Option[RecordInfo]] = {
    val record = db.run(tRecord.filter(i => i.roomid === roomId && i.starttime === startTime).result.headOption)
    record.flatMap{resOpt =>
      if (resOpt.isEmpty)
        Future(None)
      else{
        val r = resOpt.get
        val res = UserInfoDao.searchByRoomId(r.roomid).map{w =>
          if(w.nonEmpty){
            Some(RecordInfo(r.id,r.roomid,"","",w.get.uid,w.get.userName,r.starttime,
              UserInfoDao.getHeadImg(w.get.headImg),UserInfoDao.getCoverImg(r.coverImg),r.viewNum,r.likeNum,r.duration))
          }else{
            log.debug("获取主播信息失败，主播不存在")
            Some(RecordInfo(r.id,r.roomid,r.recordname,r.recorddes,-1l,"",r.starttime,
              UserInfoDao.getHeadImg(""),UserInfoDao.getCoverImg(r.coverImg),r.viewNum,r.likeNum,r.duration))
          }
        }
        res
      }
    }
  }

  def deleteRecord(recordId:Long) = {
    db.run(tRecord.filter(_.id === recordId).delete)
  }

  def searchRecordById(recordId:Long) ={
    db.run(tRecord.filter(_.id === recordId).result.headOption)
  }


  def searchRecordById(recordIdList:List[Long]) ={
    Future.sequence(recordIdList.map{id =>
      db.run(tRecord.filter(_.id === id).result.headOption)
    }).map{r => r.filter(_.nonEmpty).map(_.get).map(r => RecordData(r.roomid,r.starttime))}
  }

  def deleteRecordById(recordIdList:List[Long]) ={
    val query = tRecord.filter{r =>
      recordIdList.map{r.id === _}.reduceLeft(_ || _)
    }
    db.run(query.delete)
  }



  def getRecordAll(sortBy:String,pageNum:Int,pageSize:Int) :Future[List[RecordInfo]]= {
    val records = if (sortBy == "time") db.run(tRecord.sortBy(_.starttime.reverse).drop((pageNum - 1) * pageSize).take(pageSize).result)
    else if (sortBy == "view") db.run(tRecord.sortBy(_.viewNum.reverse).drop((pageNum - 1) * pageSize).take(pageSize).result)
    else db.run(tRecord.sortBy(_.likeNum.reverse).drop((pageNum - 1) * pageSize).take(pageSize).result)
    records.flatMap{ls =>
      val res = ls.map{r =>
        UserInfoDao.searchByRoomId(r.roomid).map{w =>
          if(w.nonEmpty){
            RecordInfo(r.id,r.roomid,r.recordname,r.recorddes,w.get.uid,w.get.userName,r.starttime,
              UserInfoDao.getHeadImg(w.get.headImg),UserInfoDao.getCoverImg(r.coverImg),r.viewNum,r.likeNum,r.duration)
          }else{
            log.debug("获取主播信息失败，主播不存在")
            RecordInfo(r.id,r.roomid,r.recordname,r.recorddes,-1l,"",r.starttime,
              UserInfoDao.getHeadImg(""),UserInfoDao.getCoverImg(r.coverImg),r.viewNum,r.likeNum,r.duration)
          }
        }
      }.toList
      Future.sequence(res)
    }
  }

  def getRecordByRoomId(roomid: Long, pageNum:Int, pageSize:Int) = {
    val action =
      for(
        total <- tRecord.filter(_.roomid === roomid).result;
        recorders <- tRecord.filter(_.roomid === roomid).drop((pageNum - 1) * pageSize).take(pageSize).result
      )yield (total.length, recorders)
    db.run(action)
  }

  def getAllRecord() = {
    db.run(tRecord.result)
  }

  def updateAllowUser(id: Long, allowPeople: String) ={
    db.run(tRecord.filter(_.id === id).map(_.allowUser).update(allowPeople))
  }


  def getTotalNum = {
    db.run(tRecord.length.result)
  }

  def updateViewNum(roomId:Long, startTime:Long, num:Int) = {
    db.run(tRecord.filter(i => i.roomid === roomId && i.starttime === startTime).map(_.viewNum).update(num))

  }

  def getAuthorRecordList(roomId: Long): Future[List[RecordInfo]] = {
    val resList = UserInfoDao.searchByRoomId(roomId).flatMap{
      case Some(author) =>
        val records = db.run(tRecord.filter(_.roomid === roomId).sortBy(_.starttime.reverse).result)
        records.map{ls =>
          val res = ls.map{r =>
            RecordInfo(r.id,r.roomid,r.recordname,r.recorddes,author.uid,author.userName,r.starttime,
              UserInfoDao.getHeadImg(author.headImg),UserInfoDao.getCoverImg(r.coverImg),r.viewNum,r.likeNum,r.duration)
          }.toList
          res
        }
      case None =>
        log.debug("获取主播信息失败，主播不存在")
        Future{List.empty[RecordInfo]}
    }

    resList
  }

  def getAuthorRecordTotalNum(roomId: Long): Future[Int] = {
    db.run(tRecord.filter(_.roomid === roomId).length.result)
  }

  def deleteAuthorRecord(recordId: Long) = {
    db.run(tRecord.filter(_.id === recordId).delete)
  }

  def addRecordAddr(recordId: Long, recordAddr: String): Future[Int] = {
    db.run(tRecord.filter(_.id === recordId).map(_.recordAddr).update(recordAddr))
  }


  def main(args: Array[String]): Unit = {
    def update() = {
      db.run(tRecord.filter(_.roomid =!= 5l).result.headOption).flatMap{valueOpt =>
        if(valueOpt.nonEmpty){
          db.run(tRecord.filter(_.roomid === 5l).map(_.likeNum).update(valueOpt.get.likeNum + 1))
        }else{
          Future(-1)
        }
      }
    }
    val a = List(2l,3l,4l).map{roomid =>
      db.run(tRecord.filter(_.roomid === roomid).result)
    }
    val b = Future.sequence(a).map(_.flatten)

//    db.run(tRecord.forceInsert(rRecord(-1l,4l,4554l)))//强制插入不过滤自增项
//    db.run(tRecord ++= List(rRecord(-1l,4l,4554l)))//批量插入，过滤自增项
//    db.run(tRecord.forceInsertAll(Seq(rRecord(-1l,4l,4554l),rRecord(5l,65l,6356l))))
//    val a = List(4)
//    val b = mutable.LinkedList(3)
//    val c = a ++: b
//    println(c)
  }
}
