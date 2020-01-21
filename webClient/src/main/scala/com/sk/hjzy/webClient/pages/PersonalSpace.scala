package com.sk.hjzy.webClient.pages

import com.sk.hjzy.webClient.Index

import scala.xml.Node

/**
  * Author: wqf
  * Date: 2020/1/22
  * Time: 0:52
  */
object PersonalSpace extends Index{


  override def app: Node = {
    <div>
      <div class="personal-space">
        <div class="left-bar">
        </div>
        <div class="container"></div>
      </div>
    </div>
  }
}
