package com.sk.hjzy.protocol.ptcl.webClientManager

import com.sk.hjzy.protocol.ptcl.CommonProtocol.RecordInfo
import com.sk.hjzy.protocol.ptcl.{Request, Response}
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

  case class Comment(
    id: Long,
    content: String,
    replyTo: String,
    rtype: Int,
    belongTo: Long,
    recordId: Long,
    createTime: String,
    author: String,
    authorImg: String
  )

  case class GetCommentsRsp(
    comments: List[Comment],
    errCode: Int,
    msg: String
  ) extends CommonRsp

  case class SendCommentReq(
    content: String,
    replyTo: String,
    rType: Int,
    belongTo: Long,
    recordId: Long
  )

  case class SearchRecord(
                           roomId: Long,
                           startTime: Long,
                           inTime:Long,//用户开始观看视频的时间
                           userIdOpt:Option[Long] = None
                         ) extends Request

  case class SearchRecordRsp(
                              url: String = "",
                              recordInfo: RecordInfo,
                              errCode: Int = 0,
                              msg: String = "ok"
                            ) extends CommonRsp

}
