package com.sk.hjzy.webClient.pages

import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sk.hjzy.protocol.ptcl.webClientManager._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{HTMLElement, HTMLTextAreaElement}

import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global


object Login extends Index{

  private val loginOption = Var(0)

  val loginMode =
    loginOption.map{ mode =>
      if(mode == 0){
        <div>
          <div>
            <input type="text" placeholder="你的账号/邮箱" class="account" id="account"></input>
            <input type="password" class="account" placeholder="密码" id="password"></input>
          </div>
          <div style="margin-top:20px;display:flex;align-items:center;justify-content: flex-start;height:40px">
            <div class="loginBtn">登录</div>
            <div class="registerBtn">注册</div>
          </div>
        </div>
      }else{
        <div>
          <div>
            <input type="text" placeholder="填写邮箱" class="account" id="account"></input>
            <div class="verify">
              <input type="text" class="verifyCode" placeholder="输入验证码" id="password"></input>
              <div>获取验证码</div>
            </div>
          </div>
          <div style="margin-top:50px;display:flex;align-items:center;justify-content: flex-start;height:40px">
            <div class="loginBtn">登录</div>
            <div class="registerBtn" onclick={()=> {dom.window.location.href ="/hjzy#/register"}}>注册</div>
          </div>
        </div>
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
