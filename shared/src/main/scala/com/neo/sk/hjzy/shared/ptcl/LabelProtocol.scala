package com.neo.sk.hjzy.shared.ptcl

/**
  * Author: wqf
  * Date: 2019/3/29
  * Time: 20:04
  */
object LabelProtocol {

  case class addLabelReq(
    content : String
  )

  case class delLabelReq(
    content : String
  )

  case class LabelRecord(
    content : String
  )

  case class GetLabelsRsp(
    list : Option[List[LabelRecord]],
    errCode: Int = 0,
    msg: String = "Ok"
  ) extends CommonRsp
}
