package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.Record
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import mhtml.Var
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.syntax._
import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Author: wqf
  * Date: 2020/1/25
  * Time: 23:45
  */
object RecordPage extends Index{

  case class UserInfo(
    userName: String,
    userImg: String = "/hjzy/roomManager/static/img/akari.jpg"
  )

  case class Records(
    total: Int,
    recordList: List[Record]
  )

  val userInfo = Var(UserInfo(""))

  def logout(): Unit = {
    Http.getAndParse[SuccessRsp](Routes.User.logout).map{
      case Right(rst) =>
        dom.window.location.href = "/hjzy/webClient#/login"
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }


  override def app: Node = {
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy/webClient#/homePage" style="display:block;display:flex;width:65px;">
          {
          userInfo.map{info =>
            <img src={info.userImg}></img>
          }
          }
          <div style="width:30px">主页</div>
        </a>
        <a class="mini-register" href="/hjzy/webClient#/register" style="width:60px" onclick={() => {logout()}}>退出登录</a>
      </div>
    </div>
  }
}
