package com.neo.sk.hjzy.front.pages

import com.neo.sk.hjzy.front.{Index, Routes}
import com.neo.sk.hjzy.front.utils.{Http, JsFunc}
import com.neo.sk.hjzy.shared.ptcl.{CommonRsp, SuccessRsp}
import com.neo.sk.hjzy.shared.ptcl.LoginProtocol.{UserLoginReq, UserLoginRsp}
import com.neo.sk.hjzy.shared.ptcl.SignupProtocol.UserSignupReq
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{HTMLElement, HTMLTextAreaElement}

import scala.xml.Node
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * User: XuSiRan
  * Date: 2019/3/26
  * Time: 17:40
  */
object Login extends Index{

  override def app: Node =

    <div><h1>hello</h1></div>
}
