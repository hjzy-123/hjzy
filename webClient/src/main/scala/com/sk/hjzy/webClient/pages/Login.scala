package com.sk.hjzy.webClient.pages

import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sk.hjzy.protocol.ptcl.webClientManager._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{HTMLElement, HTMLTextAreaElement}

import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * User: XuSiRan
  * Date: 2019/3/26
  * Time: 17:40
  */
object Login extends Index{

  override def app: Node =
    <div class="login-header">
      <a class="mini-login" href="/hjzy#/login">
        <img src="/hjzy/static/img/akari.jpg"></img>
        <div>登录</div>
      </a>
      <a class="mini-register" href="/hjzy/register">注册</a>
    </div>
}
