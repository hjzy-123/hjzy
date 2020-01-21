package com.sk.hjzy.webClient

import cats.Show
import com.sk.hjzy.webClient.pages.{FindPassword, HomePage, Login, Register}
import mhtml.mount
import org.scalajs.dom
import com.sk.hjzy.webClient.utils.{Http, JsFunc, PageSwitcher}
import mhtml._
import org.scalajs.dom
import io.circe.syntax._
import io.circe.generic.auto._
/**
  * Created by haoshuhan on 2018/6/4.
  * changed by Xu Si-ran on 2019/3/21.
  */
object Main extends PageSwitcher {
  val currentPage = currentHashVar.map { ls =>
    println(s"currentPage change to ${ls.mkString(",")}")
    ls match {
      case "login" :: Nil => Login.app
      case "register" :: Nil => Register.app
      case "homePage" :: Nil => HomePage.app
      case "findPassword" :: Nil =>FindPassword.app
      case _ => Login.app
    }

  }

  def show(): Cancelable = {
    switchPageByHash()
    val page =
      <div style="background-color: #fff;">

        {currentPage}

      </div>
    mount(dom.document.body, page)
  }


  def main(args: Array[String]): Unit ={
    show()
  }
}
