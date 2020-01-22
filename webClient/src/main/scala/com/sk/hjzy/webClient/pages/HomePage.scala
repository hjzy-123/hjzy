package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sun.nio.zipfs.JarFileSystemProvider
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Node

/**
  * Author: wqf
  * Date: 2020/1/20
  * Time: 23:41
  */
object HomePage extends Index{


  def logout(): Unit = {
    Http.getAndParse[SuccessRsp](Routes.User.logout).map{
      case Right(rst) =>
        dom.window.location.href = "/hjzy/webClient#/login"
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }

  override def app: Node ={
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy/webClient#/personalSpace" style="display:block;display:flex;width:100px;">
          <img src="/hjzy/roomManager/static/img/akari.jpg"></img>
          <div style="width:60px">个人中心</div>
        </a>
        <a class="mini-register" href="/hjzy/webClient#/register" style="width:60px" onclick={() => {logout()}}>退出登录</a>
      </div>
      <div class="header-banner">
        <img src="/hjzy/roomManager/static/img/header.png"></img>
      </div>
    </div>
  }

}
