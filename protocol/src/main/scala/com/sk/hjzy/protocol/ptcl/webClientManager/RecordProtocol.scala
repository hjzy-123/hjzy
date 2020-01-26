package com.sk.hjzy.protocol.ptcl.webClientManager

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ComRsp, CommonRsp}

/**
  * Author: wqf
  * Date: 2020/1/23
  * Time: 17:42
  */
object RecordProtocol {

  case class Record(
    id: Long,
    cover_img: String,
    record_name: String,
    record_addr: String,
    allowUser: String
  )

  case class GetRecordsRsp(
    total: Int,
    records: List[Record],
    errCode: Int,
    msg: String
  ) extends CommonRsp

  case class GetRecordInfoRsp(
    owner: Boolean,
    record: Record,
    otherRecords: List[Record],
    errCode: Int,
    msg: String
  ) extends CommonRsp

  case class UpdateAllowUserReq(
    id: Long,
    allowUser: String
  )

}
