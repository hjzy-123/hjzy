package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.{ComRsp, SuccessRsp}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.RegisterReq
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sk.hjzy.protocol.ptcl.webClientManager._
import com.sk.hjzy.webClient.component.Header
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.{Button, Input}
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

  var pattern: Regex = """@""".r

  val emailPattern: Regex = """^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$""".r

  def checkWord(input: Input) = {
    val s = pattern.replaceAllIn(input.value, "")
    input.value = s
    input.setSelectionRange(s.length, s.length)
  }

  def genVerifyCode(e: Event): Unit = {
    val emailOpt = dom.document.getElementById("email")
    if(emailOpt == null){
      JsFunc.alert("no such element")
    }else{
      val verfyCodeBtn = e.target.asInstanceOf[Button]

      val email = emailOpt.asInstanceOf[Input].value
      if(emailPattern.findAllIn(email).mkString("") == email){
        verfyCodeBtn.disabled = true
        verfyCodeBtn.style.backgroundColor = "#dddddd"
        Http.getAndParse[ComRsp](Routes.User.genVerifyCode(email)).map{
          case Right(rst) =>
            verfyCodeBtn.disabled = false
            verfyCodeBtn.style.backgroundColor = "#00a1d6"
            if(rst.errCode != 0){
              JsFunc.alert(rst.msg)
            }else{
              JsFunc.alert("验证码发送成功")
            }
          case Left(err) =>
            verfyCodeBtn.disabled = false
            verfyCodeBtn.style.backgroundColor = "#00a1d6"
            JsFunc.alert("service unavailable")
        }
      }else{
        JsFunc.alert("邮箱格式不正确，请重新输入")
      }

    }
  }

  def register(): Unit ={
    val url = Routes.User.register
    val email = dom.document.getElementById("email").asInstanceOf[Input].value
    val userName = dom.document.getElementById("account").asInstanceOf[Input].value
    val password = dom.document.getElementById("password").asInstanceOf[Input].value
    val verifyCode = dom.document.getElementById("verifyCode").asInstanceOf[Input].value
    if(email.isEmpty || userName.isEmpty || password.isEmpty || verifyCode.isEmpty){
      JsFunc.alert("信息填写不完整")
    }else{
      if(emailPattern.findAllIn(email).mkString("") == email){
        val content = RegisterReq(userName, password, verifyCode, email).asJson.noSpaces
        Http.postJsonAndParse[SuccessRsp](url, content).map{
          case Right(rst) =>
            if(rst.errCode == 0){
              JsFunc.alert("注册成功")
              dom.window.location.href = "/hjzy/webClient#/login"
            }else{
              JsFunc.alert(rst.msg)
            }

          case Left(value) =>
            JsFunc.alert("service unavailable")
        }
      }else{
        JsFunc.alert("邮箱格式不正确，请重新输入")
      }

    }
  }



  override def app: Node =
    <div>
      {Header.app}
      <div class="login-title">
        <span>注册</span>
        <div class="line"></div>
      </div>
      <div class="registerForm">
        <input type="text" placeholder="输入昵称" id="account" oninput={(e: Event) => {checkWord(e.target.asInstanceOf[Input])}}></input>
        <input type="password" placeholder="密码(区分大小写)" id="password"></input>
        <input type="text" placeholder="输入常用邮箱" id="email"></input>
        <div class="verify" style="height:42px;width:420px;margin-bottom:30px">
          <input type="text" class="verifyCode" placeholder="请输入邮箱验证码" id="verifyCode" style="width:290px;height:40px"></input>
          <Button style="height:40px;width:120px;border:none;" onclick={(e: Event) => {genVerifyCode(e)}}>点击获取</Button>
        </div>
        <div class="registerBtn1" id="registerBtn" onclick={() => {register()}}>注册</div>
      </div>
    </div>
}
