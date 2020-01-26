package com.sk.hjzy.webClient.pages

import java.util.regex.Pattern

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.{GetRecordInfoRsp, Record}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.GetUserInfoRsp
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import mhtml.Var
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalajs.dom.raw.HTMLElement

import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

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
  val recordInfo = Var(Record(0l, "", "", "", ""))
  val otherRecordInfo = Var(List.empty[Record])

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
        if(rst.errCode == 0){
          userInfo := UserInfo(rst.userName, rst.headImg)
        }else{
          JsFunc.alert(rst.msg)
        }
      case Left(err) =>
        dom.window.location.href = "/hjzy/webClient#/login"

    }
  }

  def getRecordInfo() = {
    val recordId = dom.window.location.hash.split("/").last
    val regEx = "^[0-9]*$"
    val pattern = Pattern.compile(regEx)
    val matcher = pattern.matcher(recordId)
    if(matcher.matches()){
      Http.get(Routes.Record.getRecordInfo(recordId.toLong)).map{ s =>
        decode[SuccessRsp](s) match{
          case Right(rst1) =>
            if(rst1.errCode == 0){
              decode[GetRecordInfoRsp](s) match {
                case Right(rst) =>
                  recordInfo := rst.record
                  otherRecordInfo := rst.otherRecords
                case Left(err) =>
                  JsFunc.alert("service unavailable")
              }
            }else{
              if(rst1.msg == "no session"){
                dom.window.location.href = "/hjzy/webClient#/login"
              }else JsFunc.alert(rst1.msg)
            }
          case Left(err) =>
            JsFunc.alert("service unavailable")
        }
      }
    }else{
      JsFunc.alert("service unavailable")
    }
  }


  override def app: Node = {
    getPersonalInfo()
    getRecordInfo()
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
      <div class="header-banner">
        <img src="/hjzy/roomManager/static/img/header.png"></img>
      </div>
      <div style="width:980px;height:1200px;margin:15px auto;display:flex;justify-content:space-between;align-items:flex-start">
        <div style="width:650px;flex-shrink:0;">
          {
            recordInfo.map{ record =>
              <div style="width:650px">
                <div style="color:#212121;font-size:18px;margin-bottom:10px;text-indent:25px">{record.record_name}</div>
                <video controls="controls" src={record.record_addr} style="display:block;width:600px;height:300px;object-fit: contain;background-color: #000;margin: 0 auto">
                  <source src={record.record_addr} type="video/mp4" ></source>
                </video>
              </div>
            }
          }
        </div>
        <div style="width:300px;flex-shrink:0;height:100%;overflow-y:scroll;overflow-x:hidden;margin-left:10px">
          <div style="height:30px;line-height:30px;color:#212121;font-size:20px;width:300px;">
            其它录像
          </div>
          {
            otherRecordInfo.map{ records =>
              records.map{ record =>
                <div style="height:200px;display:flex;justify-content:flex-start;align-items:flex-start;cursor:pointer" onclick={() => {dom.window.location.href = s"/hjzy/webClient#/record/${record.id}"} }>
                  <img src={record.cover_img} style="width:100px;height:100px;border-radius:4px;border:1px solid #ccc;margin-top:50px"></img>
                  <div style="font-size:14px;margin-left:15px;font-weight:500;color:#212121;word-break:break-all;margin-top:50px" class="recordTitle">{record.record_name}</div>
                </div>
              }

            }
          }
        </div>
        <div style="width:320px"></div>
      </div>
    </div>
  }
}
