package com.sk.hjzy.protocol.ptcl.processer2Manager

object ProcessorProtocol {

  sealed trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  /**  url:processor/newConnect
   *  post
   */
  case class NewConnect(
                         roomId: Long,
                         liveIdList: List[String],
                         num: Int,
                         speaker: String,
                         pushLiveId:String,
                         pushLiveCode:String
                       )

  case class NewConnectRsp(
                            startTime:Long,
                            errCode: Int = 0,
                            msg:String = "ok"
                          ) extends CommonRsp

  /**  url:processor/closeRoom
   *  post
   */
  case class CloseRoom(
                        roomId: Long
                      )

  case class CloseRoomRsp(
                           errCode: Int = 0,
                           msg:String = "ok"
                         ) extends CommonRsp


  /**  url:processor/update
   *  post
   */
  case class UpdateRoomInfo(
                             roomId: Long,
                             liveIdList: List[(String,Int)],
                             num: Int,
                             speaker: String,
                           )

  case class UpdateRsp(
                        errCode: Int = 0,
                        msg:String = "ok"
                      ) extends CommonRsp


  //录像
  case class SeekRecord(
                         roomId:Long,
                         startTime:Long
                       )

  case class RecordInfoRsp(
                            errCode:Int = 0,
                            msg:String = "ok",
                            duration:String
                          ) extends CommonRsp

}
