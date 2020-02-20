
package com.sk.hjzy.processor

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, DispatcherSelector}
import akka.actor.{ActorSystem, Scheduler}
import akka.dispatch.MessageDispatcher
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.sk.hjzy.processor.core.{RoomManager, StreamPullActor, StreamPushActor}
import com.sk.hjzy.processor.http.HttpService
import com.sk.hjzy.rtpClient.Protocol.Command

import scala.language.postfixOps


/**
  * User: yuwei
  * Date: 7/15/2019
  */
object Boot extends HttpService {

  import concurrent.duration._
  import com.sk.hjzy.processor.common.AppSettings._

  override implicit val system: ActorSystem = ActorSystem("processor", config)
  // the executor should not be the default dispatcher.
  override implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  override implicit val scheduler: Scheduler = system.scheduler

  override implicit val timeout: Timeout = Timeout(20 seconds) // for actor asks

  val blockingDispatcher: DispatcherSelector = DispatcherSelector.fromConfig("akka.actor.my-blocking-dispatcher")

  val log: LoggingAdapter = Logging(system, getClass)

  val roomManager:ActorRef[RoomManager.Command] = system.spawn(RoomManager.create(),"roomManager")

  val streamPushActor:ActorRef[Command]=system.spawn(StreamPushActor.create(),"streamPushActor")

  val streamPullActor:ActorRef[Command] = system.spawn(StreamPullActor.create(), "streamPullActor")

  //fixme 此处用以判断流是否存在
  var showStreamLog = true

	def main(args: Array[String]) {


    Http().bindAndHandle(routes, httpInterface, httpPort)
    log.info(s"Listen to the $httpInterface:$httpPort")
    log.info("Done.")

  }






}
