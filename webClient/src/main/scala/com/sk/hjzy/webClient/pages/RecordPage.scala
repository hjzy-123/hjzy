package com.sk.hjzy.webClient.pages

import java.util.regex.Pattern

import com.sk.hjzy.protocol.ptcl.webClientManager.Common.SuccessRsp
import com.sk.hjzy.protocol.ptcl.webClientManager.RecordProtocol.{Comment, GetCommentsRsp, GetRecordInfoRsp, Record, SendCommentReq}
import com.sk.hjzy.protocol.ptcl.webClientManager.UserProtocol.GetUserInfoRsp
import com.sk.hjzy.webClient.{Index, Routes}
import com.sk.hjzy.webClient.utils.{Http, JsFunc}
import mhtml.{Rx, Var}
import org.scalajs.dom
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalajs.dom.html.TextArea
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
  val commentsInfo = Var(List.empty[Comment])

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

  def getComment(): Unit ={
    val recordId = dom.window.location.hash.split("/").last
    val regEx = "^[0-9]*$"
    val pattern = Pattern.compile(regEx)
    val matcher = pattern.matcher(recordId)
    if(matcher.matches()){
      Http.get(Routes.Record.getComments(recordId.toLong)).map{ s =>
        decode[SuccessRsp](s) match{
          case Right(rst1) =>
            if(rst1.errCode == 0){
              decode[GetCommentsRsp](s) match {
                case Right(rst) =>
                  commentsInfo := rst.comments
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

  def sendComment(id: String, replyTo: String, rType: Int, belongTo: Long): Unit = {
    val recordId = dom.window.location.hash.split("/").last.toLong
    val content = dom.document.getElementById(id).asInstanceOf[TextArea].value
    if(content.isEmpty){
      JsFunc.alert("你还没有评论")
    }else{
      val content1 = SendCommentReq(content, replyTo, rType, belongTo, recordId).asJson.noSpaces
      Http.postJsonAndParse[SuccessRsp](Routes.Record.sendComment, content1).map{
        case Right(rst) =>
          if(rst.errCode == 0){
            getComment()
          }else{
            JsFunc.alert(rst.msg)
          }

        case Left(value) =>
          JsFunc.alert("service unavailable")
      }
    }
  }

  def deleteComment(id: Long): Unit = {
    Http.getAndParse[SuccessRsp](Routes.Record.deleteComment(id)).map{
      case Right(rst) =>
        if(rst.errCode == 0){
          getComment()
        }else{
          JsFunc.alert(rst.msg)
        }
      case Left(err) =>
        dom.window.location.href = "/hjzy/webClient#/login"

    }
  }


  override def app: Node = {
    getPersonalInfo()
    getRecordInfo()
    getComment()
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
      <div style="width:980px;margin:15px auto;">
        <div style="width:650px;float:left">
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

          {
            commentsInfo.map{ comments =>
              <div>
                <div style="border-bottom: 1px solid #ccc;height:1px;margin:20px 0px;"></div>
                <div style="color:#222222;font-size:18px">{comments.length}条评论</div>
                <div style="border-bottom: 1px solid #ccc;height:1px;margin:20px 0px;"></div>
                <div class="comment-send">
                  <img class="comment-userface" src="/hjzy/roomManager/static/img/noface.gif"></img>
                  <div class="textarea-container">
                    <textarea placeholder="请自觉遵守互联网相关的政策法规，严禁发布色情、暴力、反动的言论。" id="bigTextarea"></textarea>
                    <div class="comment-sender" onclick={() => {sendComment("bigTextarea", "", 1, 0)}}>发表评论</div>
                  </div>
                </div>
                <div style="height:1px;border-bottom:1px solid #ccc;margin-left:85px;margin-top:30px;margin-bottom:20px"></div>
                {
                  Rx(comments.filter(_.rtype == 1)).map{fComments =>
                    val myRecord = Var(if(dom.window.location.hash.split("/").reverse(1) == "myRecord") true else false)
                    fComments.zipWithIndex.map{ i =>
                      val fComment = i._1
                      val fIndex = i._2
                      <div style="margin-top:20px">
                        <div class="comment-send">
                          <img class="comment-userface" style="margin-top:0px" src={fComment.authorImg}></img>
                          <div style="width: 100%;padding-left: 85px;padding-top:10px">
                            <div class="commentName">{fComment.author}</div>
                            <div class="commentContent">{fComment.content}</div>
                            <div class="commentInfo">
                              <span class="time">{fComment.createTime}</span>
                              <span class="reply" onclick={() => {
                                val commentDiv = dom.document.getElementById(s"fComment-$fIndex").asInstanceOf[HTMLElement]
                                if(commentDiv.style.display == "none") commentDiv.style.display = "block"
                                else commentDiv.style.display = "none"
                              }}>回复</span>
                              {
                                myRecord.zip(userInfo).map{ r=>
                                  val myRecord = r._1
                                  val myComment =
                                    if(r._2.userName == fComment.author) true
                                    else false
                                  if(myRecord || myComment){
                                    <span class="reply" onclick={() => {deleteComment(fComment.id)}}>删除</span>
                                  }else{
                                    <span></span>
                                  }
                                }
                              }
                            </div>
                            <div id={s"fComment-$fIndex"} style="display:none;margin-top:15px">
                              <div class="comment-send">
                                <img class="comment-userface" src="/hjzy/roomManager/static/img/noface.gif"></img>
                                <div class="textarea-container">
                                  <textarea placeholder={s"回复：${fComment.author}"} style="width:400px;background-color:#fff" id={s"fReply-$fIndex"}></textarea>
                                  <div class="comment-sender" onclick={() => {sendComment(s"fReply-$fIndex", fComment.author, 2, fComment.id)}}>发表评论</div>
                                </div>
                              </div>
                            </div>
                            {
                              Rx(comments.filter{comment => comment.rtype == 2 && comment.belongTo == fComment.id}).map{sComments =>
                                sComments.zipWithIndex.map{i =>
                                  val sIndex = i._2
                                  val sComment = i._1
                                  <div style="margin-top:20px">
                                    <div class="comment-send">
                                      <img class="comment-userface" style="margin-top:0px;width:24px;height:24px" src={sComment.authorImg}></img>
                                      <div style="width: 100%;padding-top:10px;padding-left: 34px;">
                                        <div class="commentName">{sComment.author} <span style="font-size:14px;font-weight:400;color:#222"> 回复 </span> {sComment.replyTo}</div>
                                        <div class="commentContent">{sComment.content}</div>
                                        <div class="commentInfo">
                                          <span class="time">{sComment.createTime}</span>
                                          <span class="reply" onclick={() => {
                                            val commentDiv = dom.document.getElementById(s"fComment-$fIndex-sComment-$sIndex").asInstanceOf[HTMLElement]
                                            if(commentDiv.style.display == "none") commentDiv.style.display = "block"
                                            else commentDiv.style.display = "none"
                                          }}>回复</span>
                                          {
                                          myRecord.zip(userInfo).map{ r=>
                                            val myRecord = r._1
                                            val myComment =
                                              if(r._2.userName == fComment.author) true
                                              else false
                                            if(myRecord || myComment){
                                              <span class="reply" onclick={() => {deleteComment(fComment.id)}}>删除</span>
                                            }else{
                                              <span></span>
                                            }
                                          }
                                          }
                                        </div>
                                        <div id={s"fComment-$fIndex-sComment-$sIndex"} style="display:none;margin-top:15px">
                                          <div class="comment-send">
                                            <img class="comment-userface" src="/hjzy/roomManager/static/img/noface.gif"></img>
                                            <div class="textarea-container">
                                              <textarea placeholder={s"回复：${sComment.author}"} style="width:400px;background-color:#fff" id={s"fReply-$fIndex-sReply-$sIndex"}></textarea>
                                              <div class="comment-sender" onclick={() => {sendComment(s"fReply-$fIndex-sReply-$sIndex", sComment.author, 2, fComment.id)}}>发表评论</div>
                                            </div>
                                          </div>
                                        </div>
                                      </div>
                                    </div>
                                  </div>

                                }
                              }
                            }
                          </div>
                        </div>
                        <div style="height:1px;margin-top:20px;margin-bottom:10px;marging-left:85px;border-bottom:1px solid #ccc"></div>
                      </div>
                    }
                  }
                }
              </div>

            }
          }
        </div>
        <div style="width:300px;float:right;height:100%;margin-left:10px">
          <div style="height:30px;margin-top:30px;line-height:30px;color:#212121;font-size:20px;width:300px;">
            其它录像
          </div>
          {
            otherRecordInfo.map{ records =>
              records.map{ record =>
                <div style="height:150px;display:flex;justify-content:flex-start;align-items:flex-start;cursor:pointer" onclick={() => {
                  val re = dom.window.location.hash.split("/").reverse(1)
                  dom.window.location.href = s"/hjzy/webClient#/$re/${record.id}"}
                }>
                  <img src={record.cover_img} style="width:100px;height:100px;border-radius:4px;border:1px solid #ccc;margin-top:50px"></img>
                  <div style="font-size:14px;margin-left:15px;font-weight:500;color:#212121;word-break:break-all;margin-top:50px" class="recordTitle">{record.record_name}</div>
                </div>
              }

            }
          }
        </div>
        <div></div>
      </div>
    </div>
  }
}
