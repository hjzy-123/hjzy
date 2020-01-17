package com.neo.sk.hjzy

import akka.actor.ActorSystem
import akka.dispatch.MessageDispatcher
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.neo.sk.hjzy.service.HttpService
import com.neo.sk.hjzy.utils.DBUtil.db

import scala.language.postfixOps
import scala.util.{Failure, Success}
import org.slf4j.LoggerFactory

import scala.concurrent.Await






/**
  * User: Taoz
  * Date: 11/16/2016
  * Time: 1:00 AM
  */
object  extends HttpService{


  import concurrent.duration._
  import com.neo.sk.hjzy.common.AppSettings._
  import slick.jdbc.SQLiteProfile.api._

  override implicit val system = ActorSystem("appSystem", config)
  // the executor should not be the default dispatcher.
  override implicit val executor: MessageDispatcher =
    system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")

  override implicit val materializer = ActorMaterializer()

  override implicit val timeout = Timeout(20 seconds) // for actor asks

  override implicit val scheduler = system.scheduler

  val log: LoggingAdapter = Logging(system, getClass)


  def main(args: Array[String]) {
    log.info("Starting.")
    val binding = Http().bindAndHandle(routes, httpInterface, httpPort)
    binding.onComplete {
      case Success(b) ⇒
        val localAddress = b.localAddress
        println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
//        println(s"Server is listening on http://localhost:${localAddress.getPort}/todos2018/index")
      case Failure(e) ⇒
        println(s"Binding failed with ${e.getMessage}")
        system.terminate()
        System.exit(-1)
    }
  }



}
