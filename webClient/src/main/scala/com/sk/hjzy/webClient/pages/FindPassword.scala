package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.ResetPassword
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.component.Header
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.html.{Button, Input}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalajs.dom.raw.Event

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.Node

/**
  * Author: wqf
  * Date: 2020/1/21
  * Time: 16:42
  */
object FindPassword extends Index{

  val step = Var(1)
  var email1 = ""

  def confirmEmail(): Unit = {
    val email = dom.document.getElementById("email").asInstanceOf[Input].value
    Http.getAndParse[SuccessRsp](Routes.User.checkEmail(email)).map{
      case Right(res) =>
        email1 = email
        step := 1
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }

  def resetPassword(): Unit = {
    val password = dom.document.getElementById("password").asInstanceOf[Input].value
    val confirmPassword = dom.document.getElementById("confirmPassword").asInstanceOf[Input].value
    val verifyCode = dom.document.getElementById("verifyCode").asInstanceOf[Input].value
    if(password.isEmpty || confirmPassword.isEmpty || verifyCode.isEmpty){
      JsFunc.alert("输入信息不完整")
    }else{
      if(password != confirmPassword) JsFunc.alert("两次密码不一致，请重新输入")
      else{
        val content = ResetPassword(email1, password, verifyCode).asJson.noSpaces
        Http.postJsonAndParse[SuccessRsp](Routes.User.resetPassword, content).map{
          case Right(res) =>
            if(res.errCode == 0){
              email1 = ""
              step := 2
            }else JsFunc.alert(res.msg)
          case Left(err) =>
            JsFunc.alert("service unavailable")
        }
      }
    }
  }

  def genVerifyCode(e: Event): Unit ={
    val btn = e.target.asInstanceOf[Button]
    btn.disabled = true
    btn.style.backgroundColor = "#dddddd"
    Http.getAndParse[SuccessRsp](Routes.User.genPasswordVerifyCode(email1)).map{
      case Right(res) =>
        btn.disabled = false
        btn.style.backgroundColor = "#00a1d6"
        if(res.errCode == 0) JsFunc.alert("验证码发送成功")
        else JsFunc.alert(res.msg)
      case Left(err) =>
        btn.disabled = false
        btn.style.backgroundColor = "#00a1d6"
        JsFunc.alert("service unavailable")
    }
  }

  val steps = step.map{ i =>
    val step1Style =
      if(i == 0) "color: #f25d8e;border-color:#f25d8e"
      else "color:#00a1d6;border-color:#00a1d6"

    val step2Style =
      if(i == 1) "color: #f25d8e;border-color:#f25d8e"
      else if(i == 2) "color:#00a1d6;border-color:#00a1d6"
      else ""

    val step3Style =
      if(i == 2) "color: #f25d8e;border-color:#f25d8e"
      else ""

    <div style="width:980px;margin:10px auto;">
      <div class="steps">
        <div class="step">
          <div style={step1Style}>1</div>
          <div style={step1Style}>确认账号</div>
        </div>
        <div class="line"></div>
        <div class="step">
          <div style={step2Style}>2</div>
          <div style={step2Style}>重置密码</div>
        </div>
        <div class="line"></div>
        <div class="step">
          <div style={step3Style}>3</div>
          <div style={step3Style}>重置成功</div>
        </div>
      </div>
      {
        step.map {
          case 0 =>
            <div class="changeStep">
              <input style="margin:0 auto 30px;" type="text" placeholder="请输入绑定的邮箱" id="email"></input>
              <div class="step1Confirm" onclick={() => {confirmEmail()}}>确认</div>
            </div>
          case 1 =>
            <div class="changeStep changeStep2">
              <div>
                <div>新密码：</div>
                <input type="text" placeholder="新密码" id="password"></input>
              </div>
              <div>
                <div>确认密码：</div>
                <input type="text" placeholder="请输入确认密码" id="confirmPassword"></input>
              </div>
              <div>
                <div>邮箱：</div>
                <div style="width:350px;height:40px;line-height:40px;display:flex;align-item:center;justify-content:space-between;">
                  <div style="font-size:14px;color:#222;height:40px;line-height:40px;width:310px">{email1}</div>
                  <div style="font-size:12px;color:#00A1D6;text-align:right;width:30px;height:40px;line-height:40px;cursor:pointer;" onclick={() => {step := 0} }>修改</div>
                </div>
              </div>
              <div>
                <div>验证：</div>
                <div style="display:flex;align-items:center;justify-content: space-between;height:40px;width:350px">
                  <input type="text" placeholder="请输入邮件验证码" style="width:250px" id="verifyCode"></input>
                  <button style="border:none;height:38px;border-radius:4px;line-height:38px;color:#fff;font-size:14px;background-color:#409eff;cursor:pointer;width:90px" onclick={(e: Event) => {genVerifyCode(e)} } >获取验证码</button>
                </div>
              </div>
              <div style="justify-content: flex-end;">
                <button style="height:40px;width:350px;border:none;color:#fff;line-height:40px;background-color:#00a1d6;border-radius:4px;cursor:pointer" onclick={() => {resetPassword()}}>确认修改</button>
              </div>
            </div>
          case 2 =>
            <div style="width:980px;margin:20px auto">
              <h3 style="margin:0 auto;text-align:center;width:500px">重置密码成功！</h3>
              <img src="/hjzy/roomManager/static/img/cele.png" style="display:block;width:200px;height:200px;margin:20px auto;"></img>
              <a style="display:block;border-radius:4px;width:150px;height:40px;line-height:40px;text-align:center;color:#fff;font-size:14px;margin:10px auto;background-color:#00a1d6" href="/hjzy/webClient#/login">前往登录页</a>
            </div>
        }
      }
    </div>
  }

  override def app: Node ={
    step := 0
    email1 = ""
    <div class="forgetPassword">
      {Header.app}
      <div class="f-header"><a href="/hjzy/webClient#/login">登录</a> > 忘记密码</div>
      {steps}
    </div>
  }
}
