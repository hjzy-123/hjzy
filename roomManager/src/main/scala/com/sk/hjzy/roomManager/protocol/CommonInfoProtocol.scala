package com.sk.hjzy.roomManager.protocol

import com.sk.hjzy.protocol.ptcl.CommonInfo
import com.sk.hjzy.protocol.ptcl.CommonProtocol.{LiveInfo, RoomInfo}
import com.sk.hjzy.protocol.ptcl.client2Manager.websocket.WsProtocol.PartUserInfo

import scala.collection.mutable

object CommonInfoProtocol {

  //fixme isJoinOpen,liveInfoMap字段移到这里
  final case class WholeRoomInfo(
                                var roomInfo:RoomInfo,
                                //var recordStartTime: Option[Long] = None,
                                liveInfoMap: mutable.HashMap[Int, mutable.HashMap[Long, LiveInfo]], //"audience"/"anchor"->Map(userId->LiveInfo)
                                userInfoList:  Option[List[PartUserInfo]] = None
                                )

}
