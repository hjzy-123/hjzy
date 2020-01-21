package com.sk.hjzy.webClient.component

/**
  * Author: wqf
  * Date: 2020/1/21
  * Time: 16:51
  */
object Header {

  def app: xml.Node =
    <div>
      <div class="login-header">
        <a class="mini-login" href="/hjzy/webClient#/login">
          <img src="/hjzy/roomManager/static/img/akari.jpg"></img>
          <div>登录</div>
        </a>
        <a class="mini-register" href="/hjzy/webClient#/register">注册</a>
      </div>
      <div class="header-banner">
        <img src="/hjzy/roomManager/static/img/header.png"></img>
      </div>
    </div>
}
