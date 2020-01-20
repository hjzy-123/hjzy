package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.ComRsp
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sk.hjzy.protocol.ptcl.webClientManager._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{Event, HTMLElement, HTMLTextAreaElement}

import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

/**
  * Author: wqf
  * Date: 2020/1/18
  * Time: 21:14
  */
object Register extends Index{

  var pattern: Regex = """[\\{}<>"'/&\n\r|$%@‘’“”]|[^\s\u4e00-\u9fa5_a-zA-Z0-9`~!@#$%^&*()_+\[\]\\;',./{}|:"<>?～！¥…（）—【】、；，。「」：《》？]""".r

  val emailPattern: Regex = """^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$""".r

  def checkWord(input: Input) = {
    val s = pattern.replaceAllIn(input.value, "")
    input.value = s
    input.setSelectionRange(s.length, s.length)
  }

  def genVerifyCode(): Unit = {
    val emailOpt = dom.document.getElementById("email")
    if(emailOpt == null){
      JsFunc.alert("no such element")
    }else{
      val email = emailOpt.asInstanceOf[Input].value
      if(emailPattern.findAllIn(email).mkString("") == email){
        Http.getAndParse[ComRsp](Routes.User.genVerifyCode(email)).map{
          case Right(rst) =>
            if(rst.errCode != 0){
              JsFunc.alert(rst.msg)
            }else{
              JsFunc.alert("验证码发送成功")
            }
          case Left(err) =>
            JsFunc.alert("service unavailable")
        }
      }else{
        JsFunc.alert("邮箱格式不正确，请重新输入")
      }

    }
  }



  override def app: Node =
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy#/login">
          <img src="/hjzy/static/img/akari.jpg"></img>
          <div>登录</div>
        </a>
        <a class="mini-register" href="/hjzy#/register">注册</a>
      </div>
      <div class="header-banner">
        <img src="/hjzy/static/img/header.png"></img>
      </div>
      <div class="login-title">
        <span>注册</span>
        <div class="line"></div>
      </div>
      <div class="registerForm">
        <input type="text" placeholder="账号" id="account" oninput={(e: Event) => {checkWord(e.target.asInstanceOf[Input])}}></input>
        <input type="password" placeholder="密码(区分大小写)" id="password"></input>
        <input type="text" placeholder="输入常用邮箱" id="email"></input>
        <div class="verify" style="height:42px;width:420px;margin-bottom:30px">
          <input type="text" class="verifyCode" placeholder="请输入邮箱验证码" id="verifyCode" style="width:290px;height:40px"></input>
          <div style="height:40px;width:120px" onclick={() => {genVerifyCode()}}>点击获取</div>
        </div>
        <div class="registerBtn1" id="registerBtn">注册</div>
      </div>
    </div>
}
