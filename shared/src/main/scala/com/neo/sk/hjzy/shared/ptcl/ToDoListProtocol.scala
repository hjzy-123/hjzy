package com.neo.sk.hjzy.shared.ptcl

/**
  * User: sky
  * Date: 2018/6/1
  * Time: 15:45
  *
  * update by zhangtao: 2019-3-23
  *
  */
object ToDoListProtocol {


  //添加记录
  case class AddRecordReq(content: String, label: String)

  case class DelRecordReq(content: String, label: String)

  //获得列表
  case class TaskRecord(
    id: Int,
    content: String,
    time: Long,
    label: String
  )
  case class GetListRsp(
    list: Option[List[TaskRecord]],
    errCode: Int = 0,
    msg: String = "Ok"
  ) extends CommonRsp

}
