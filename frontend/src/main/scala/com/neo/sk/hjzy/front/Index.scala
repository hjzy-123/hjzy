package com.neo.sk.hjzy.front

import scala.xml.Node

/**
  * Created by haoshuhan on 2018/6/4.
  */
trait Index {
  def app: Node
  def cancel: Unit = ()
  val pageName = this.getClass.getSimpleName
  val url = "#" + pageName

}
