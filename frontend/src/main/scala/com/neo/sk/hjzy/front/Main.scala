package com.neo.sk.hjzy.front

import cats.Show
import com.neo.sk.hjzy.front.pages.Login
import mhtml.mount
import org.scalajs.dom
import com.neo.sk.hjzy.front.utils.{Http, JsFunc, PageSwitcher}
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
      case _ => Login.app
    }

  }

  def show(): Cancelable = {
    switchPageByHash()
    val page =
      <div>

        {currentPage}

      </div>
    mount(dom.document.body, page)
  }


  def main(args: Array[String]): Unit ={
    show()
  }
}
