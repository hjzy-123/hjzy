package com.sk.hjzy.roomManager

import akka.actor.ActorSystem
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

  override implicit val scheduler = system.scheduler

  override implicit val timeout: Timeout = Timeout(20 seconds)

  val log: LoggingAdapter = Logging(system, getClass)

  override implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")

  val userManager = system.spawn(UserManager.create(), "userManager")

  val roomManager = system.spawn(RoomManager.create(), "roomManager")

  val emailManager4Web = system.spawn(EmailManager.create(), "emailManager")

  def main(args: Array[String]): Unit = {
    //    log.info("Starting.")

    //    val password: Array[Char] = "1qaz@WSX".toCharArray // do not store passwords in code, read them from somewhere safe!
    //
    //    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    //    val keystore = new FileInputStream("./src/main/resources/tomatocc.p12")
    //    require(keystore != null, "Keystore required!")
    //    ks.load(keystore, password)
    //    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    //    keyManagerFactory.init(ks, password)
    //
    //    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    //    tmf.init(ks)
    //    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    //    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom())
    //    val https: HttpsConnectionContext = ConnectionContext.https(sslContext)

    //    val httpsBinding = Http().bindAndHandle(Routes, AppSettings.httpInterface, AppSettings.httpPort, connectionContext = https)
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
