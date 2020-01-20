package com.sk.hjzy.roomManager.models.dao
import com.sk.hjzy.protocol.ptcl.CommonInfo.UserInfo
import com.sk.hjzy.roomManager.models.SlickTables
import slick.jdbc.MySQLProfile.api._
import com.sk.hjzy.roomManager.Boot.executor
import com.sk.hjzy.roomManager.utils.DBUtil._

import scala.concurrent.Future
import com.sk.hjzy.roomManager.models.SlickTables._
/**
  * created by benyafang on 2019/9/23 16:58
  * */
object AdminDAO {
  def sealUserInfo(userId:Long,sealUtilTime:Long) = {
    db.run(tUserInfo.filter(_.uid === userId).map(r => (r.`sealed`,r.sealedUtilTime)).update((true,sealUtilTime)))
  }

  def cancelSealUserInfo(userId:Long) = {
    db.run(tUserInfo.filter(_.uid === userId).map(r => (r.`sealed`,r.sealedUtilTime)).update((false,-1)))
  }

  def getUserList(pageNum:Int,pageSize:Int) ={
    for{
      len <- UserInfoDao.getUserLen
      ls <- db.run(tUserInfo.sortBy(_.uid).take(pageNum * pageSize).drop((pageNum - 1) * pageSize).result)
    }yield {
      (len,ls)
    }

  }

  def updateAllowAnchor(userId:Long,allow:Boolean) = {
    db.run(tUserInfo.filter(_.uid === userId).map(_.allowAnchor).update(allow))
  }

}
