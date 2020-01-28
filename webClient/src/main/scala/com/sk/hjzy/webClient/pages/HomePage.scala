package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.{GetRecordsRsp, Record}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.GetUserInfoRsp
import com.sk.hjzy.webClient.pages.PersonalSpace.Records
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import com.sun.nio.zipfs.JarFileSystemProvider
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.syntax._
import mhtml.Var
import org.scalajs.dom.raw.HTMLElement
import io.circe.parser.decode
import org.scalajs.dom.html.Input

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Node

/**
  * Author: wqf
  * Date: 2020/1/20
  * Time: 23:41
  */
object HomePage extends Index{

  case class UserInfo(
    userName: String,
    userImg: String = "/hjzy/roomManager/static/img/akari.jpg"
  )

  val userInfo = Var(UserInfo(""))

  val pageSize = 8
  val myReordTotalNum = Var(0)
  val myRecordCurrentPage = Var(1)
  var myRecordPage = 1

  val otherRecordTotalNum = Var(0)
  val otherRecordCurrentPage = Var(1)
  var otherRecordPage = 1
  val myRecordsInfo = Var(Records(0, List.empty[Record]))
  val otherRecordsInfo = Var(Records(0, List.empty[Record]))


  def getPersonalInfo(): Unit = {
    Http.getAndParse[GetUserInfoRsp](Routes.User.getUserInfo).map{
      case Right(rst) =>
        if(rst.errCode == 0){
          userInfo := UserInfo(rst.userName, rst.headImg)
          dom.document.getElementById("myInfo01").asInstanceOf[HTMLElement].style.display = "block"
          dom.document.getElementById("myRecords").asInstanceOf[HTMLElement].style.display = "none"
        }else{
          JsFunc.alert(rst.msg)
        }

      case Left(err) =>
        dom.window.location.href = "/hjzy/webClient#/login"

    }
  }

  def getVideoInfo(pageNum: Int = 1): Unit = {
    Http.get(Routes.Record.getRecords(pageNum, pageSize)).map{ s =>
      decode[SuccessRsp](s) match {
        case Right(rst1) =>
          if(rst1.errCode == 0){
            decode[GetRecordsRsp](s) match {
              case Right(rst) =>
                if(rst.errCode == 0){
                  //                  recordsInfo := Records(rst.total, rst.records)
                  myRecordsInfo := Records(rst.total, rst.records)
                  myReordTotalNum := rst.total
                  myRecordCurrentPage := pageNum
                }else {
                  if(rst1.msg == "no session"){
                    dom.window.location.href = "/hjzy/webClient#/login"
                  }else JsFunc.alert(rst.msg)
                }
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
  }

  def getOtherRecord(pageNum: Int = 1): Unit ={
    Http.get(Routes.Record.getOtherRecords(pageNum, pageSize)).map{ s =>
      decode[SuccessRsp](s) match {
        case Right(rst1) =>
          if(rst1.errCode == 0){
            decode[GetRecordsRsp](s) match {
              case Right(rst) =>
                if(rst.errCode == 0){
                  //                  recordsInfo := Records(rst.total, rst.records)
                  otherRecordsInfo := Records(rst.total, rst.records)
                  otherRecordTotalNum := rst.total
                  otherRecordCurrentPage := pageNum
                }else {
                  if(rst1.msg == "no session"){
                    dom.window.location.href = "/hjzy/webClient#/login"
                  }else JsFunc.alert(rst.msg)
                }
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
  }

  def logout(): Unit = {
    Http.getAndParse[SuccessRsp](Routes.User.logout).map{
      case Right(rst) =>
        dom.window.location.href = "/hjzy/webClient#/login"
      case Left(err) =>
        JsFunc.alert("service unavailable")
    }
  }

  def turnToPage(total:Int, myRecord: Boolean = true) = {
    val num =
      try{
        if(myRecord) dom.window.document.getElementById("page").asInstanceOf[Input].value.toInt
        else dom.window.document.getElementById("page1").asInstanceOf[Input].value.toInt
      }catch{case e :Exception=> -1}
    if(num < 1 || num > total){
      JsFunc.alert("输入有误，请输入有效页码")
    }
    else{
      if(myRecord){
        myRecordPage = num
        myRecordCurrentPage := num
        getVideoInfo(myRecordPage)
        dom.window.document.getElementById("page").asInstanceOf[Input].value = ""
      }else{
        otherRecordPage = num
        otherRecordCurrentPage := num
        getOtherRecord(otherRecordPage)
        dom.window.document.getElementById("page1").asInstanceOf[Input].value = ""
      }

    }
  }

  //页码
  def pageMod(totalNum: Var[Int], currentPage: Var[Int], myRecord: Boolean = true) =
  {totalNum.zip(currentPage).map{rst =>
    val apps = rst._1
    val currPage = rst._2 //当前页码
    <div class="page" style="margin-top:0;margin-right:50px">
      {if(apps == 0)
      <div></div>
    else
      <div style="display: flex;margin-left: 4px;">
        <div style="margin-right: 4px;">共{apps}条记录</div>
        <div class="select" onclick={()=>
          if(myRecord){
            if(myRecordPage == 1){JsFunc.alert("不能再往前翻了")}
            else myRecordPage = myRecordPage -1;currentPage:=myRecordPage;getVideoInfo(myRecordPage)
          }else{
            if(otherRecordPage == 1){JsFunc.alert("不能再往前翻了")}
            else otherRecordPage = otherRecordPage -1;currentPage:=otherRecordPage;getVideoInfo(otherRecordPage)
          }

        }><img src="/hjzy/roomManager/static/img/left.png"></img></div>
        <div style="display:flex;">
          {
          val maxPage = Math.ceil(apps.toDouble/pageSize.toDouble).toInt
          val before: List[xml.Node] = if (currPage < 5) {
            (1 to currPage).map { tips =>
              <div class={currentPage.map(page => if (page == tips) "select-active" else "select")} onclick={() => {
                currentPage := tips
                if(myRecord){
                  myRecordPage = tips
                  getVideoInfo(myRecordPage)
                }else{
                  otherRecordPage = tips
                  getOtherRecord(otherRecordPage)
                }
              }
              }>
                {tips}
              </div>
            }.toList
          } else {
            val first = <div class="select" onclick={() =>{
              currentPage := 1
              if(myRecord){
                myRecordPage = 1
                getVideoInfo(myRecordPage)
              }else{
                otherRecordPage = 1
                getOtherRecord(otherRecordPage)
              }
            }

            }>1</div>
            val omit = <div>...</div>
            val last: List[xml.Node] = (currPage - 2 to currPage).map {p =>
              <div class={currentPage.map(page =>if(page == p)"select-active" else "select")} onclick={()=>{
                currentPage:=p
                if(myRecord){
                  myRecordPage = p
                  getVideoInfo(myRecordPage)
                }else{
                  otherRecordPage = p
                  getOtherRecord(otherRecordPage)
                }
              }
              }>{p}</div>
            }.toList
            first :: omit :: last
          }
          val after: List[xml.Node] = if (maxPage - currPage > 3) {
            val first: List[xml.Node] = (currPage + 1 to currPage + 2).map{p =>
              <div class="select" onclick={() =>{
                currentPage := p
                if(myRecord){
                  myRecordPage = p
                  getVideoInfo(myRecordPage)
                }else{
                  otherRecordPage = p
                  getOtherRecord(otherRecordPage)
                }
              }
              }>{p}</div>
            }.toList
            val omit = <div>...</div>
            val last = <div class="select" onclick={() => {
              currentPage := maxPage
              if(myRecord){
                myRecordPage = maxPage
                getVideoInfo(myRecordPage)
              }else{
                otherRecordPage = maxPage
                getOtherRecord(otherRecordPage)
              }
            }
            }>{maxPage}</div>
            first :+ omit :+ last
          } else if (maxPage == currPage){
            List.empty[Node]
          } else {
            (currPage + 1 to maxPage).map{p =>
              <div class="select" onclick={() =>
                currentPage := p
                if(myRecord){
                  myRecordPage = p
                  getVideoInfo(myRecordPage)
                }else{
                  otherRecordPage = p
                  getOtherRecord(otherRecordPage)
                }
              }>{p}</div>}.toList
          }
          before ++ after
          }
        </div>
        <div class="select" onclick={()=> {
          if(myRecord){
            if(myRecordPage == Math.ceil(apps.toDouble/pageSize.toDouble).toInt) {JsFunc.alert("不能再往后翻了")} else myRecordPage = myRecordPage + 1;currentPage:=myRecordPage;getVideoInfo(myRecordPage)
          }else{
            if(otherRecordPage == Math.ceil(apps.toDouble/pageSize.toDouble).toInt) {JsFunc.alert("不能再往后翻了")} else otherRecordPage = otherRecordPage + 1;currentPage:=otherRecordPage;getOtherRecord(otherRecordPage)
          }
        }
        }><img src="/hjzy/roomManager/static/img/right.png"></img></div>
        <span>跳至</span>
        {
        Var(myRecord).map{ ifMy =>
          if(ifMy){
            <input id="page"></input>
          }else{
            <input id="page1"></input>
          }
        }
        }

        <span>页</span>
        <div class="confirm" onclick={()=>turnToPage(Math.ceil(apps.toDouble/pageSize.toDouble).toInt, myRecord)}>确认</div>
      </div>
      }
    </div>
  }}

  val myRecord = myRecordsInfo.map{ recordsInfo =>
    val total = recordsInfo.total
    val recordList = recordsInfo.recordList
    <div>
      <div class="recordHeader">
        <div class="recordTitle">
          <div>我的录像</div>
          <div class="totalRecord">当前共有<span>{total}</span>个录像</div>
        </div>
        <div class="record-head-refresh">
          <img class="img-refresh" src="/hjzy/roomManager/static/img/refresh.png" style="float: right;" onclick={()=> {getVideoInfo(myRecordPage)}}></img>
        </div>
      </div>
      <div style="box-shadow: 0 2px 4px rgba(0,0,0,.14);margin-top:20px;height:400px">
        {
          Var(recordList).map{list =>
            if(list.isEmpty){
              <div style="height:100%;line-height:400px;font-size:50px;text-align:center;weight:700">暂无内容</div>
            }else{
              <div style="height:350px" class="recordsList">
                {
                recordList.map{ record =>
                  <div class="recordContainer" style="margin-top:20px;">
                    <div style="width:25%;margin:0 auto;height:120px;cursor:pointer" onclick={() => {dom.window.location.href = s"/hjzy/webClient#/myRecord/${record.id}"}}>
                      <img style="height:100px;width:100px;" src={record.cover_img}></img>
                      <div style="width:100px;margin-top:5px;height:15px;line-height:15px;font-size:14px;text-align:center">
                        {if(record.record_name.length < 10) record.record_name else {record.record_name.substring(0,10)+"..."}}
                      </div>
                    </div>
                  </div>
                }
                }
              </div>
            }
          }
        }
        {pageMod(myReordTotalNum, myRecordCurrentPage)}
      </div>
    </div>
  }

  val otherRecord = otherRecordsInfo.map{ recordsInfo =>
    val total = recordsInfo.total
    val recordList = recordsInfo.recordList
    <div>
      <div class="recordHeader">
        <div class="recordTitle">
          <div>可观看录像</div>
          <div class="totalRecord">当前共有<span>{total}</span>个录像</div>
        </div>
        <div class="record-head-refresh">
          <img class="img-refresh" src="/hjzy/roomManager/static/img/refresh.png" style="float: right;" onclick={()=> {getOtherRecord(otherRecordPage)}}></img>
        </div>
      </div>
      <div style="box-shadow: 0 2px 4px rgba(0,0,0,.14);margin-top:20px;height:400px">
        {
        Var(recordList).map{ records =>
          if(records.isEmpty){
            <div style="height:100%;line-height:400px;font-size:50px;text-align:center;weight:700">暂无内容</div>
          }else{
            <div style="height:350px" class="recordsList">
              {
              recordList.map{ record =>
                <div class="recordContainer" style="margin-top:20px;">
                  <div style="width:25%;margin:0 auto;height:120px;cursor:pointer" onclick={() => {dom.window.location.href = s"/hjzy/webClient#/otherRecord/${record.id}"}}>
                    <img style="height:100px;width:100px;" src={record.cover_img}></img>
                    <div style="width:100px;margin-top:5px;height:15px;line-height:15px;font-size:14px;text-align:center">
                      {if(record.record_name.length < 10) record.record_name else {record.record_name.substring(0,10)+"..."}}
                    </div>
                  </div>
                </div>
              }
              }
            </div>
          }
        }
        }
        {pageMod(otherRecordTotalNum, otherRecordCurrentPage)}
      </div>
    </div>
  }

  override def app: Node ={
    getPersonalInfo()
    getVideoInfo()
    getOtherRecord()
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy/webClient#/personalSpace" style="display:block;display:flex;width:100px;">
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
      <div style="width:980px;margin:20px auto;">
        {myRecord}
        <div style="height:30px"></div>
        {otherRecord}
      </div>
    </div>
  }

}
