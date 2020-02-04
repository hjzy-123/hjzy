package com.sk.hjzy.protocol.ptcl

object CommonProtocol {

  case class RoomInfo(
                       roomId: Long,
                       roomName: String,
                       roomDes: String,
                       userId: Long,  //房主id
                       userName:String,
                       headImgUrl:String,
                       coverImgUrl:String,
                       var rtmp: Option[String] = None
                     )

  case class UserInfo(
                       userId: Long,
                       userName: String,
                       headImgUrl:String,
                       token: String,
                       tokenExistTime: Long, //token有效时长
                       seal:Boolean = false
                     )

  case class RecordInfo(
                         recordId:Long,//数据库中的录像id，用于删除录像
                         roomId:Long,
                         recordName:String,
                         recordDes:String,
                         userId:Long,
                         userName:String,
                         startTime:Long,
                         headImg:String,
                         coverImg:String,
                         observeNum:Int, //浏览量
                         likeNum:Int,
                         duration:String = ""
                       )

}
