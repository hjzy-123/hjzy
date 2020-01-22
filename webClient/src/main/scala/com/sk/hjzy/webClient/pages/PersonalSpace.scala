package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.GetUserInfoRsp
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.syntax._
import mhtml.{Var, emptyHTML}
import org.scalajs.dom.html.{Button, Image, Input}
import org.scalajs.dom.raw.{Event, FileReader, FormData, UIEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex
import scala.xml.Node

/**
  * Author: wqf
  * Date: 2020/1/22
  * Time: 0:52
  */
object PersonalSpace extends Index{

  val index = Var(0)

  case class UserInfo(
    userName: String,
    userImg: String = "/hjzy/roomManager/static/img/akari.jpg"
  )

  val userInfo = Var(UserInfo(""))

  val leftBar = index.map{ i =>
    val myInfoStyle =
      if(i == 0) "background-color:#00a1d7!important;color:#fff"
      else ""
    val myVideo =
      if(i == 0) ""
      else "background-color:#00a1d7!important;color:#fff"
    <div>
      <div style={myInfoStyle} class="leftBar-item" onclick={() => {getPersonalInfo()}}>
        我的信息
      </div>
      <div style={myVideo} class="leftBar-item" onclick={() => {index := 1;getVideoInfo()}}>
        我的录像
      </div>
    </div>
  }

  def preview(e: Event): Unit = {
    val input = e.target.asInstanceOf[Input]
    val imgFile = input.files(0)
    val img = dom.document.getElementById("headImg").asInstanceOf[Image]
    val attachName = input.value.split("\\\\").last
    if(!attachName.contains("png") && !attachName.contains("jpg") && !attachName.contains("svg")){
      JsFunc.alert("格式必须为png/jpg/svg")
      input.value = ""
    }else{
      val reader = new FileReader
      reader.readAsDataURL(imgFile)
      reader.onload = {(e : UIEvent) =>
        val data = reader.result
        img.setAttribute("src", data.toString)
      }
    }
  }

  val mainContainer = userInfo.zip(index).map{ infoAndIndex =>
    val info = infoAndIndex._1
    val index = infoAndIndex._2
    val hover = Var(false)
    <div style="height:100%;width:100%">
      <div class="container-header">
        <div class="haeder-icon"></div>
        <div class="header-text">{if(index == 0) "我的信息" else "我的录像"}</div>
      </div>
      <div class="container-main">
        <div>
          <div class="main-item">头像：</div>
          <div class="imgContainer" onmouseover={() => hover := true} onmouseleave={() => hover := false}>
            <img src={info.userImg} class="headImg" id="headImg"></img>
            <div class={hover.map{h => if(h) "reload" else "none"}} >重新上传</div>
            <input class="hiddenIcon" title=" " type="file" id="imgInput" onchange={(e: Event) => {preview(e)}}></input>
          </div>
        </div>
        <div>
          <div class="main-item">昵称：</div>
          <input type="text" id="nickName" class="nickInput" placeholder="你的昵称" value={info.userName} ></input>
        </div>
        <div class="line"></div>
        <button onclick={(e: Event) => {updateInfo(e)}}>保存</button>
      </div>
    </div>
  }

  def updateInfo(e: Event): Unit ={
    val btn = e.target.asInstanceOf[Button]
    btn.disabled = true
    btn.style.backgroundColor = "#dddddd"
    val file = dom.document.getElementById("imgInput").asInstanceOf[Input].files(0)
    val name = dom.document.getElementById("nickName").asInstanceOf[Input].value
    if(name.trim.isEmpty){
      JsFunc.alert("昵称不能为空")
    }else{
      val form = new FormData()
      form.append("fileUpload", file)
      Http.postFormAndParse[SuccessRsp](Routes.User.updateInfo(name), form).map{
        case Right(rst) =>
          if(rst.errCode == 0){
            getPersonalInfo()
            JsFunc.alert("保存成功")
          }else{
            JsFunc.alert(rst.msg)
          }

        case Left(err) =>
          btn.disabled = true
          btn.style.backgroundColor = "#dddddd"
          JsFunc.alert("service unavailable")
      }
    }
  }

  def logout(): Unit = {
    Http.getAndParse[SuccessRsp](Routes.User.logout).map{
      case Right(rst) =>
        dom.window.location.href = "/hjzy/webClient#/login"
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }

  def getPersonalInfo(): Unit = {
    Http.getAndParse[GetUserInfoRsp](Routes.User.getUserInfo).map{
      case Right(rst) =>
        index := 0
        userInfo := UserInfo(rst.userName, rst.headImg)
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }

  def getVideoInfo(): Unit = {
    index := 1
  }

  override def app: Node = {
    getPersonalInfo()
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy/webClient#/login" style="display:block;display:flex;width:100px;">
          {
          userInfo.map{info =>
            <img src={info.userImg}></img>
          }
          }
          <div style="width:60px">个人中心</div>
        </a>
        <a class="mini-register" href="/hjzy/webClient#/register" style="width:60px" onclick={() => {logout()}}>退出登录</a>
      </div>
      <div class="header-banner">
        <img src="/hjzy/roomManager/static/img/header.png"></img>
      </div>
      <div class="personal-space">
        <div class="left-bar">
          <div style="width:149px;font-size:16px;height:50px;line-height:50px;color:#99a2aa;text-align:center;border-bottom:1px solid #e1e2e5;">个人中心</div>
          {leftBar}
        </div>
        <div class="main-container">
          {mainContainer}
        </div>
      </div>
    </div>
  }
}
