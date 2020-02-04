package com.sk.hjzy.roomManager.protocol

import com.sk.hjzy.protocol.ptcl.CommonInfo
import com.sk.hjzy.protocol.ptcl.CommonInfo.RoomInfo

object CommonInfoProtocol {

  //fixme isJoinOpen,liveInfoMap字段移到这里
  final case class WholeRoomInfo(
                                var roomInfo:RoomInfo,
                                //var recordStartTime: Option[Long] = None,
                                var layout:Int = CommonInfo.ScreenLayout.EQUAL,
                                var aiMode:Int = 0
                                )

}
