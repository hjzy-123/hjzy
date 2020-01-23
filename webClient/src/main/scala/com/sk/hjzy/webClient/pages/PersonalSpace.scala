package com.sk.hjzy.webClient.pages

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.{GetRecordsRsp, Record}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.{GetUserInfoRsp, UpdateNameReq}
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import mhtml.{Var, emptyHTML, mount}
import org.scalajs.dom.html.{Button, Image, Input}
import org.scalajs.dom.raw.{Event, FileReader, FormData, HTMLElement, UIEvent}

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
  val pageSize = 8
  val totalNum = Var(0)
  val currentPage = Var(1)
  var page = 1

  case class UserInfo(
    userName: String,
    userImg: String = "/hjzy/roomManager/static/img/akari.jpg"
  )

  case class Records(
    total: Int,
    recordList: List[Record]
  )

  val userInfo = Var(UserInfo(""))
  val recordsInfo = Var(Records(0, List.empty[Record]))

  val leftBar = index.map{ i =>
    val myInfoStyle =
      if(i == 0) "background-color:#00a1d7!important;color:#fff"
      else ""
    val myVideo =
      if(i == 0) ""
      else "background-color:#00a1d7!important;color:#fff"
    <div>
      <div style={myInfoStyle} class="leftBar-item" onclick={() => {
        getPersonalInfo()
      }
      }>
        我的信息
      </div>
      <div style={myVideo} class="leftBar-item" onclick={() => {
        getVideoInfo()
      }}>
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

  def turnToPage(total:Int) = {
    val num =
      try{
        dom.window.document.getElementById("page").asInstanceOf[Input].value.toInt
      }catch{case e :Exception=> -1}
    if(num < 1 || num > total){
      JsFunc.alert("输入有误，请输入有效页码")
    }
    else{
      page = num
      currentPage := num
      getVideoInfo(page)
      dom.window.document.getElementById("page").asInstanceOf[Input].value = ""
    }
  }


  //页码
  val pageMod =
  {totalNum.zip(currentPage).map{rst =>
    val apps = rst._1
    val currPage = rst._2 //当前页码
    <div class="page">
      {if(apps == 0)
      <div></div>
    else
      <div style="display: flex;margin-left: 4px;">
        <div style="margin-right: 4px;">共{apps}条记录</div>
        <div class="select" onclick={()=>
          if(page == 1){JsFunc.alert("不能再往前翻了")}
          else page = page -1;currentPage:=page;getVideoInfo(page)}><img src="/hjzy/roomManager/static/img/left.png"></img></div>
        <div style="display:flex;">
          {
          val maxPage = Math.ceil(apps.toDouble/pageSize.toDouble).toInt
          val before: List[xml.Node] = if (currPage < 5) {
            (1 to currPage).map { tips =>
              <div class={currentPage.map(page => if (page == tips) "select-active" else "select")} onclick={() => page = tips; currentPage := tips; getVideoInfo(page)}>
                {tips}
              </div>
            }.toList
          } else {
            val first = <div class="select" onclick={() => page = 1; currentPage := 1; getVideoInfo(page)}>1</div>
            val omit = <div>...</div>
            val last: List[xml.Node] = (currPage - 2 to currPage).map {p =>
              <div class={currentPage.map(page =>if(page == p)"select-active" else "select")} onclick={()=>page =p;currentPage:=p;getVideoInfo(page)}>{p}</div>
            }.toList
            first :: omit :: last
          }
          val after: List[xml.Node] = if (maxPage - currPage > 3) {
            val first: List[xml.Node] = (currPage + 1 to currPage + 2).map{p =>
              <div class="select" onclick={() => page = p; currentPage := p; getVideoInfo(page)}>{p}</div>
            }.toList
            val omit = <div>...</div>
            val last = <div class="select" onclick={() => page = maxPage; currentPage := maxPage; getVideoInfo(page)}>{maxPage}</div>
            first :+ omit :+ last
          } else if (maxPage == currPage){
            List.empty[Node]
          } else {
            (currPage + 1 to maxPage).map{p =>
              <div class="select" onclick={() => page = p; currentPage := p; getVideoInfo(page)}>{p}</div>}.toList
          }
          before ++ after
          }
        </div>
        <div class="select" onclick={()=>if(page == Math.ceil(apps.toDouble/pageSize.toDouble).toInt) {JsFunc.alert("不能再往后翻了")} else page = page + 1;currentPage:=page;getVideoInfo(page)}><img src="/hjzy/roomManager/static/img/right.png"></img></div>
        <span>跳至</span>
        <input id="page"></input>
        <span>页</span>
        <div class="confirm" onclick={()=>turnToPage(Math.ceil(apps.toDouble/pageSize.toDouble).toInt)}>确认</div>
      </div>
      }
    </div>
  }}

  def hide():Unit ={
    dom.document.body.removeChild(dom.document.getElementById("modledom"))
  }

  def saveRecordChange(): Unit ={

  }

  def showRecordModel(record: Record): Unit ={
    val allowPeoples = Var(record.allowUser.split("!=!"))
    val elem =
      <div id="modledom" class="model" >
        {
          allowPeoples.map{ p =>
            <div class="modelbox" style={s"margin:12.25% 33.33%"}>
              <div style="background-color:#F2F5FA;height:30px;border-radius:8px 8px 0 0;">
                <span style="font-size: 16px;color: #1D2341;letter-spacing: 0.19px;line-height:30px;float:left;margin-left:18px;">录像管理</span>
              </div>
              <div style="padding:18px">
                <div style="height:20px;line-height:20px;font-size:14px;weight:700"><span style="color:color: #00a1d6;">*</span>可观看录像用户：</div>
                <div style="padding:10px;height:150px;overflow-y:scroll">
                  {
                  allowPeoples.map{peoples =>
                    peoples.zipWithIndex.map{peopleAndIndex =>
                      val people = peopleAndIndex._1
                      val index = peopleAndIndex._2
                      <div style="height:30px;display:flex;justify-content: space-between;border-bottom:1px solid #ddd;align-items: center;">
                        <div style="height:30px;line-height:30px;font-size:14px;width:250px;margin-right:15px;">{people}</div>
                        <img src="/hjzy/roomManager/static/img/close.png" style="height:13px;width:13px" onclick={() => {
                          allowPeoples.update{peoples =>
                            peoples.zipWithIndex.filter(_._2 != index).map(_._1)
                          }
                        }}></img>
                      </div>
                    }.toList
                  }
                  }
                </div>
              </div>
              <div class="modelbottom" style ="text-align: center; position: relative; border-top: 1px solid #D9DFEB;">
                <button class="modelbutton" style="color: #FFFFFF;border: 0px ;background-color:#4D78FB;" onclick={() => {saveRecordChange()} } ><pre>保存</pre></button>
                <button class="modelbutton"
                        style="color: #4D78FB;border: 1px solid #4D78FB;background-color:#FFFFFF;"
                        onclick={()=>{hide()} }>取 消</button>
              </div>
            </div>
          }

        }

      </div>
    mount(dom.document.body,elem)
  }

  val myInfo = userInfo.map{ info =>
    val hover = Var(false)
    <div style="height:100%;width:100%;" id="myInfo01">
      <div class="container-header">
        <div class="haeder-icon"></div>
        <div class="header-text">我的信息</div>
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

  val myRecords = recordsInfo.map{ records =>
    val total = records.total
    val recordList = records.recordList
    <div style="height:100%;width:100%;display:none" id="myRecords">
      <div class="container-header">
        <div class="haeder-icon"></div>
        <div class="header-text">我的录像</div>
      </div>
      <div style="padding:20px;height:100%">
        <div class="totalRecord">当前共有<span>{total}</span>个录像</div>
        <div class="recordsList">
          {
          recordList.map{ record =>
            <div class="recordContainer">
              <div style="width:100px;margin:0 auto;height:120px;cursor:pointer" onclick={() => showRecordModel(record)}>
                <img style="height:100px;width:100px;" src={record.cover_img}></img>
                <div style="width:100px;margin-top:5px;height:15px;line-height:15px;font-size:14px;text-align:center">
                  {if(record.record_name.length < 5) record.record_name else {record.record_name.substring(0,5)+"..."}}
                </div>
              </div>
            </div>
          }
          }

        </div>
        {pageMod}
      </div>

    </div>
  }



  def updateInfo(e: Event): Unit ={
    val btn = e.target.asInstanceOf[Button]
    btn.disabled = true
    btn.style.backgroundColor = "#dddddd"
    val files = dom.document.getElementById("imgInput").asInstanceOf[Input].files
    val name = dom.document.getElementById("nickName").asInstanceOf[Input].value
    if(name.trim.isEmpty){
      JsFunc.alert("昵称不能为空")
    }else{
      if(files.length > 0){
        val file = files(0)
        val form = new FormData()
        form.append("fileUpload", file)
        Http.postFormAndParse[SuccessRsp](Routes.User.updateHeadImg, form).map{
          case Right(rst) =>
            if(rst.errCode == 0){
              val content = UpdateNameReq(name).asJson.noSpaces
              Http.postJsonAndParse[SuccessRsp](Routes.User.updateName, content).map{
                case Right(rst1)  =>
                  btn.disabled = false
                  btn.style.backgroundColor = "#00a1d6"
                  if(rst1.errCode == 0){
                    getPersonalInfo()
                    JsFunc.alert("保存成功")
                  }else{
                    if(rst1.msg == "no session"){
                      dom.window.location.href = "/hjzy/webClient#/login"
                    }else JsFunc.alert(rst1.msg)
                  }

                case Left(err) =>
                  btn.disabled = false
                  btn.style.backgroundColor = "#00a1d6"
                  JsFunc.alert("service unavailable")
              }
            }else{
              btn.disabled = false
              btn.style.backgroundColor = "#00a1d6"
              if(rst.msg == "no session"){
                dom.window.location.href = "/hjzy/webClient#/login"
              }else JsFunc.alert(rst.msg)
            }
          case Left(err) =>
            btn.disabled = false
            btn.style.backgroundColor = "#00a1d6"
            JsFunc.alert("service unavailable")
        }
      }else{
        val content = UpdateNameReq(name).asJson.noSpaces
        Http.postJsonAndParse[SuccessRsp](Routes.User.updateName, content).map{
          case Right(rst1)  =>
            btn.disabled = false
            btn.style.backgroundColor = "#00a1d6"
            if(rst1.errCode == 0){
              getPersonalInfo()
              JsFunc.alert("保存成功")
            }else{
              if(rst1.msg == "no session"){
                dom.window.location.href = "/hjzy/webClient#/login"
              }else JsFunc.alert(rst1.msg)
            }
          case Left(err) =>
            btn.disabled = false
            btn.style.backgroundColor = "#00a1d6"
            JsFunc.alert("service unavailable")
        }
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
        if(rst.errCode == 0){
          index := 0
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
                  recordsInfo := Records(rst.total, rst.records)
                  totalNum := rst.total
                  currentPage := pageNum
                  dom.document.getElementById("myInfo01").asInstanceOf[HTMLElement].style.display = "none"
                  dom.document.getElementById("myRecords").asInstanceOf[HTMLElement].style.display = "block"
                }else JsFunc.alert(rst.msg)
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
          {myInfo}
          {myRecords}
        </div>
      </div>
    </div>
  }
}
