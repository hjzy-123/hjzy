package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.{LoginByEmailReq, LoginReq}
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sk.hjzy.protocol.ptcl.webClientManager._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Button, Input}
import org.scalajs.dom.raw.{HTMLElement, HTMLTextAreaElement}

import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global


object Login extends Index{

  private val loginOption = Var(0)

  def loginByAccount(): Unit ={
    val account = dom.document.getElementById("account").asInstanceOf[Input].value
    val password = dom.document.getElementById("password").asInstanceOf[Input].value
    if(account.isEmpty || password.isEmpty){
      JsFunc.alert("输入信息不完整")
    }else{
      val content = LoginReq(account, password).asJson.noSpaces
      Http.postJsonAndParse[SuccessRsp](Routes.User.login, content).map{
        case Right(rst) =>
          if(rst.errCode != 0){
            JsFunc.alert(rst.msg)
          }else{
            dom.window.location.href = "/hjzy/webClient#/homePage"
          }
        case Left(err) =>
          JsFunc.alert("service unavailable")
      }
    }
  }

  def loginByEmail(): Unit ={
    val email = dom.document.getElementById("email").asInstanceOf[Input].value
    val verifyCode = dom.document.getElementById("verifyCode").asInstanceOf[Input].value
    val content = LoginByEmailReq(email, verifyCode).asJson.noSpaces
    Http.postJsonAndParse[SuccessRsp](Routes.User.loginByEmail, content).map{
      case Right(rst) =>
        if(rst.errCode != 0){
          JsFunc.alert(rst.msg)
        }else{
          dom.window.location.href = "/hjzy/webClient#/homePage"
        }
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }

  def genVerifyCode(e: Event): Unit ={
    val email = dom.document.getElementById("email").asInstanceOf[Input].value
    val btn = e.target.asInstanceOf[Button]
    btn.disabled = true
    btn.style.backgroundColor = "#dddddd"
    Http.getAndParse[SuccessRsp](Routes.User.genLoginVerifyCode(email)).map{
      case Right(rst) =>
        btn.disabled = false
        btn.style.backgroundColor = "#00a1d6"
        if(rst.errCode == 0){
          JsFunc.alert("发送验证码成功")
        }else{
          JsFunc.alert(rst.msg)
        }
      case Left(err) =>
        btn.disabled = false
        btn.style.backgroundColor = "#00a1d6"
        JsFunc.alert("service unavailable")
    }
  }

  val loginMode =
    loginOption.map{ mode =>
      if(mode == 0){
        <div>
          <div>
            <input type="text" placeholder="你的账号" class="account" id="account"></input>
            <input type="password" class="account" placeholder="密码" id="password"></input>
          </div>
          <div style="margin-top:20px;display:flex;align-items:center;justify-content: flex-start;height:40px">
            <div class="loginBtn" onclick={() => {loginByAccount()}}>登录</div>
            <div class="registerBtn" onclick={()=> {dom.window.location.href ="/hjzy/webClient#/register"}}>注册</div>
          </div>
        </div>
      }else{
        <div>
          <div>
            <input type="text" placeholder="填写邮箱" class="account" id="email"></input>
            <div class="verify">
              <input type="text" class="verifyCode" placeholder="输入验证码" id="verifyCode"></input>
              <Button onclick = {(e: Event) => {genVerifyCode(e)}}>获取验证码</Button>
            </div>
          </div>
          <div style="margin-top:50px;display:flex;align-items:center;justify-content: flex-start;height:40px">
            <div class="loginBtn" onclick={() => {loginByEmail()}}>登录</div>
            <div class="registerBtn" onclick={()=> {dom.window.location.href ="/hjzy/webClient#/register"}}>注册</div>
          </div>
        </div>
      }
  }

  override def app: Node =
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy/webClient#/login">
          <img src="/hjzy/roomManager/static/img/akari.jpg"></img>
          <div>登录</div>
        </a>
        <a class="mini-register" href="/hjzy/webClient#/register">注册</a>
      </div>
      <div class="header-banner">
        <img src="/hjzy/roomManager/static/img/header.png"></img>
      </div>
      <div class="login-title">
        <span>登录</span>
        <div class="line"></div>
      </div>
      <div style="width:490px;padding-left:45px;margin-top:10px;margin-left:auto;margin-right:auto">
        {
          loginOption.map{ mode =>
            val (pass, verify) =
              if(mode == 0) ("color:#02a7de", "")
              else ("", "color:#02a7de")
            <div class="selectMode">
              <span style={pass} onclick={() => {loginOption := 0}}>密码登录</span>
              <span style={verify} onclick={() => {loginOption := 1}}>验证码登录</span>
            </div>
          }
        }
        {loginMode}
      </div>
    </div>


}
