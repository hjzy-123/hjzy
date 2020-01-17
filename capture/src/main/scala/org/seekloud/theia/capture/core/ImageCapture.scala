package org.seekloud.hjzy.capture.core

import java.util.concurrent.LinkedBlockingDeque

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.bytedeco.javacv.{FFmpegFrameGrabber, OpenCVFrameGrabber}
import org.seekloud.hjzy.capture.core.MontageActor.CameraImage
import org.seekloud.hjzy.capture.protocol.Messages.LatestFrame
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * User: TangYaruo
  * Date: 2019/8/28
  * Time: 17:47
  */
object ImageCapture {

  private val log = LoggerFactory.getLogger(this.getClass)
  var debug: Boolean = true

  def debug(msg: String): Unit = {
    if (debug) log.debug(msg)
  }

  sealed trait Command

  final case object StartCamera extends Command

  final case object GrabFrame extends Command

  final case object SuspendCamera extends Command

  final case object StopCamera extends Command


  def create(grabber: OpenCVFrameGrabber, latestFrame: LinkedBlockingDeque[LatestFrame], frameRate: Int, isDebug: Boolean, montageActor: ActorRef[MontageActor.Command]): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"ImageCapture is staring...")
      debug = isDebug
      ctx.self ! StartCamera
      working(grabber, latestFrame, montageActor, frameRate)
    }


  private def working(
    grabber: OpenCVFrameGrabber,
    latestFrame: LinkedBlockingDeque[LatestFrame],
    montageActor: ActorRef[MontageActor.Command],
    frameRate: Int,
    state: Boolean = true
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StartCamera =>
          log.info(s"Media camera started.")
          ctx.self ! GrabFrame

          working(grabber, latestFrame, montageActor, frameRate, true)

        case SuspendCamera =>
          log.info(s"Media camera suspend.")
          working(grabber, latestFrame, montageActor, frameRate, false)

        case GrabFrame =>
          if(state) {
            Try(grabber.grab()) match {
              case Success(frame) =>
                if (frame != null) {
                  if (frame.image != null) {
                    ctx.self ! GrabFrame
                    latestFrame.clear()
                    latestFrame.offer(LatestFrame(frame.clone(), System.currentTimeMillis()))
                 //   montageActor ! CameraImage(frame)
                    //                  debug(s"add image cost: ${System.currentTimeMillis() - st} ms")
                  }
                }

              case Failure(ex) =>
                log.error(s"grab error: $ex")
            }
          }
          Behaviors.same

        case StopCamera =>
          log.info(s"Media camera stopped.")
          try {
            grabber.release()
          } catch {
            case ex: Exception =>
              log.warn(s"release camera resources failed: $ex")
          }
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in working: $x")
          Behaviors.unhandled
      }
    }

}
