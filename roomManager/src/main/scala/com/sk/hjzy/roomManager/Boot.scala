package com.sk.hjzy.roomManager

import akka.actor.typed.ActorRef
import akka.actor.{ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.adapter._
import akka.dispatch.MessageDispatcher
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.sk.hjzy.roomManager.common.AppSettings
import com.sk.hjzy.roomManager.core.webClient.EmailManager
import com.sk.hjzy.roomManager.core.{RoomManager, UserManager}
import com.sk.hjzy.roomManager.http.HttpService

import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Author: Tao Zhang
  * Date: 4/29/2019
  * Time: 11:28 PM
  */
object Boot extends HttpService {

  import concurrent.duration._

  override implicit val system: ActorSystem = ActorSystem("theia", AppSettings.config)

  override implicit val materializer: Materializer = ActorMaterializer()

  override implicit val scheduler: Scheduler = system.scheduler

  override implicit val timeout: Timeout = Timeout(20 seconds)

  val log: LoggingAdapter = Logging(system, getClass)

  override implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")

  val userManager: ActorRef[UserManager.Command] = system.spawn(UserManager.create(), "userManager")

  val roomManager: ActorRef[RoomManager.Command] = system.spawn(RoomManager.create(), "roomManager")

  val emailManager4Web: ActorRef[EmailManager.Command] = system.spawn(EmailManager.create(), "emailManager")

  def main(args: Array[String]): Unit = {

    val httpsBinding = Http().bindAndHandle(Routes, AppSettings.httpInterface, AppSettings.httpPort)

    httpsBinding.onComplete {
      case Success(b) ⇒
        val localAddress = b.localAddress
        println(s"Server is listening on https://${localAddress.getHostName}:${localAddress.getPort}")
      case Failure(e) ⇒
        println(s"httpsBinding failed with ${e.getMessage}")
        system.terminate()
        System.exit(-1)
    }

  }
}
