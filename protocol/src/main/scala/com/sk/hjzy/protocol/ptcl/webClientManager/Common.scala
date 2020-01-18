package com.sk.hjzy.protocol.ptcl.webClientManager

object Common {

  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  class ComRsp(
    val errCode: Int = 0,
    val msg: String = "ok"
  ) extends CommonRsp

  final case class ErrorRsp(
    override val errCode: Int,
    override val msg: String
  ) extends ComRsp

  final case class SuccessRsp(
    override val errCode: Int = 0,
    override val msg: String = "ok"
  ) extends ComRsp
}
