package com.sk.hjzy.roomManager.common

import com.sk.hjzy.roomManager.common.AppSettings._

object Common {
  object Role{
    val host = 0
    val audience = 1
  }

  object Source{
    val pc = "PC"
    val web = "WEB"
  }

  object DefaultImg{
    val coverImg = "http://pic.neoap.com/hestia/files/image/roomManager/1c6af4509f95701ffeae9999059d66d9.png"//默认封面图
    val headImg =  "/hjzy/roomManager/static/img/akari.jpg"//默认头像
    val videoImg = "http://pic.neoap.com/hestia/files/image/roomManager/973c741b77c9607243ada13d4c40b4af.jpg"//默認的視頻封面
  }

  object Subscriber{
    val join = 1
    val left = 0
    val change = 2
  }

  object Like{
    val up = 1
    val down = 0
  }

  def getMpdPath(roomId:Long) = {
      s"/theia/distributor/getFile/${
        if(roomId == TestConfig.TEST_ROOM_ID)"test" else roomId
      }/index.mpd"
  }


  object TestConfig{
    val TEST_USER_ID = 100029L
    val TEST_ROOM_ID = 1000029L
  }


}
