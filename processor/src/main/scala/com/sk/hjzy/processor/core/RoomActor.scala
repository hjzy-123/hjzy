package com.sk.hjzy.processor.core

import java.io.{File, InputStream, OutputStream}
import java.nio.channels.Channels
import java.nio.channels.Pipe.{SinkChannel, SourceChannel}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.sk.hjzy.processor.common.AppSettings.{debugPath, isDebug}
import com.sk.hjzy.processor.stream.PipeStream
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import com.sk.hjzy.processor.Boot.{streamPullActor, streamPushActor}

import scala.collection.mutable

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午2:28
  *
  * actor由RoomManager创建
  * 连线房间
  * 管理多路grabber和一路recorder
  */
object RoomActor {

  private  val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class NewRoom(roomId: Long, liveIdList: List[String], num: Int, speaker: String, pushLiveId:String, pushLiveCode:String, startTime:Long) extends Command

  case class UpdateRoomInfo(roomId: Long, liveIdList: List[(String, Int)], num: Int, speaker: String) extends Command

  case class Recorder(roomId: Long, recorderRef: ActorRef[RecorderActor.Command]) extends Command

  case class CloseRoom(roomId: Long) extends Command

  case class ChildDead4Grabber(roomId: Long, childName: String, value: ActorRef[GrabberActor.Command]) extends Command// fixme liveID

  case class ChildDead4Recorder(roomId: Long, childName: String, value: ActorRef[RecorderActor.Command]) extends Command

  case class ChildDead4PushPipe(liveId: String, childName: String, value: ActorRef[StreamPushPipe.Command]) extends Command

  case class ChildDead4PullPipe(liveId: String, childName: String, value: ActorRef[StreamPullPipe.Command]) extends Command

  case class ClosePipe(liveId: String) extends Command

  case object Timer4Stop

  case object Stop extends Command

  case class Timer4PipeClose(liveId: String)

  val pipeMap: mutable.Map[String, PipeStream] = mutable.Map[String, PipeStream]()

  val pullPipeMap: mutable.Map[String, ActorRef[StreamPullPipe.Command]] = mutable.Map[String, ActorRef[StreamPullPipe.Command]]()
  val pushPipeMap: mutable.Map[String, ActorRef[StreamPushPipe.Command]] = mutable.Map[String, ActorRef[StreamPushPipe.Command]]()

  def create(roomId: Long, LiveIdList: List[String], num: Int, speaker: String, pushLiveId:String, pushLiveCode:String, startTime:Long): Behavior[Command]= {
    Behaviors.setup[Command]{ ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"grabberManager start----")
          work(LiveIdList,mutable.Map[Long, List[ActorRef[GrabberActor.Command]]](), mutable.Map[Long,ActorRef[RecorderActor.Command]](), mutable.Map[Long, List[String]]())
      }
    }
  }

  def work(
            LiveIdList: List[String],
    grabberMap: mutable.Map[Long, List[ActorRef[GrabberActor.Command]]],
    recorderMap: mutable.Map[Long,ActorRef[RecorderActor.Command]],
    roomLiveMap: mutable.Map[Long, List[String]]
  )(implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command]):Behavior[Command] = {
    Behaviors.receive[Command]{(ctx, msg) =>
      msg match {

        case msg:NewRoom =>
          log.info(s"${ctx.self} receive a msg $msg")

          //todo  这里的作用是什么？
          if (isDebug) {
            val file = new File(debugPath + msg.roomId)
            if (!file.exists()) {
              file.mkdir()
            }
          }
          val pushPipe = new PipeStream
          val pushSink = pushPipe.getSink
          val pushSource= pushPipe.getSource
          val pushOut = Channels.newOutputStream(pushSink)

          pipeMap.put(msg.pushLiveId, pushPipe)

          val liveIdList = msg.liveIdList

          val recorderActor = getRecorderActor(ctx, msg.roomId, msg.liveIdList ,msg.num, msg.speaker, msg.pushLiveId, msg.pushLiveCode,  pushOut)

          val pushPipe4recorder = getPushPipe(ctx, msg.roomId, msg.pushLiveId, msg.pushLiveCode, pushSource,msg.startTime)
          pushPipeMap.put(msg.pushLiveId, pushPipe4recorder)
          recorderMap.put(msg.roomId, recorderActor)

          liveIdList.foreach{ liveId =>
            val pullPipe4Live = new PipeStream
            val pullSink4Live = pullPipe4Live.getSink
            val pullSource4Live= pullPipe4Live.getSource
            val pullInput4Live = Channels.newInputStream(pullSource4Live)
            val pullOut4Live = Channels.newOutputStream(pullSink4Live)

            pipeMap.put(liveId, pullPipe4Live)

            val grabber4Live = getGrabberActor(ctx, msg.roomId, liveId, pullInput4Live, recorderActor)

            grabberMap.get(msg.roomId).map{ grabberList =>
              if(grabberList.nonEmpty)
                grabberMap.put(msg.roomId, grabberMap(msg.roomId) ::: List(grabber4Live))
              else
                grabberMap.put(msg.roomId, List(grabber4Live))
            }

            val pullPipe4live = getPullPipe(ctx, msg.roomId,liveId, pullOut4Live)
            pullPipeMap.put(liveId, pullPipe4live)
          }

          roomLiveMap.put(msg.roomId,List(msg.pushLiveId) ::: liveIdList)
          streamPushActor ! StreamPushActor.NewLive(msg.pushLiveId, msg.pushLiveCode)
          Behaviors.same

        case UpdateRoomInfo(roomId, liveIdList, num, speaker) =>

          if(recorderMap.get(roomId).nonEmpty) {
            recorderMap.get(roomId).foreach(_ ! RecorderActor.UpdateRoomInfo(roomId,liveIdList, num, speaker ))
          } else {
            log.info(s"${roomId} recorder not exist")
          }

          //todo 增加grabber  或  删掉 grabber,  对应的存储的东西改变


          Behaviors.same

        case msg:Recorder =>
          log.info(s"${ctx.self} receive a msg $msg")
          val grabberActor = grabberMap.get(msg.roomId)
          if(grabberActor.isDefined){
            grabberActor.get.foreach(_ ! GrabberActor.Recorder(msg.recorderRef))
          } else {
            log.info(s"${msg.roomId} grabbers not exist")
          }
          Behaviors.same

        case CloseRoom(roomId) =>
          log.info(s"${ctx.self} receive a msg $msg")
          if(grabberMap.get(roomId).nonEmpty){
            grabberMap.get(roomId).foreach{g => g.foreach(_ ! GrabberActor.StopGrabber)}
            grabberMap.remove(roomId)
          } else {
            log.info(s"${roomId}  grabbers not exist when closeRoom")
          }
          if(recorderMap.get(roomId).nonEmpty) {
            recorderMap.get(roomId).foreach(_ ! RecorderActor.StopRecorder)
            recorderMap.remove(roomId)
          } else{
            log.info(s"${roomId}  recorder not exist when closeRoom")
          }
          if(roomLiveMap.get(roomId).nonEmpty){
            streamPullActor ! StreamPullActor.RoomClose(roomLiveMap(roomId))
            roomLiveMap.get(roomId).foreach{live =>
              live.foreach{l =>
                pullPipeMap.get(l).foreach( a => a ! StreamPullPipe.ClosePipe)
                timer.startSingleTimer(Timer4PipeClose(l), ClosePipe(l),1000.milli)
              }
            }
            roomLiveMap.remove(roomId)
          } else {
            log.info(s"${roomId}  pipe not exist when closeRoom")
          }
          timer.startSingleTimer(Timer4Stop, Stop, 1500.milli)
          Behaviors.same

        case ClosePipe(liveId) =>
          pushPipeMap.get(liveId).foreach( a => a ! StreamPushPipe.ClosePipe)
          pullPipeMap.remove(liveId)
          pushPipeMap.remove(liveId)
          pipeMap.remove(liveId)
          Behaviors.same

        case Stop =>
          log.info(s"${ctx.self} stopped ------")
          Behaviors.stopped

        case ChildDead4Grabber(roomId, childName, value) =>
          log.info(s"${childName} is dead ")
          grabberMap.remove(roomId)
          Behaviors.same

        case ChildDead4Recorder(roomId, childName, value) =>
          log.info(s"${childName} is dead ")
          recorderMap.remove(roomId)
          Behaviors.same

        case ChildDead4PullPipe(liveId, childName, value) =>
          log.info(s"${childName} is dead ")
          pullPipeMap.remove(liveId)
          Behaviors.same

        case ChildDead4PushPipe(liveId, childName, value) =>
          log.info(s"${childName} is dead ")
          pushPipeMap.remove(liveId)
          Behaviors.same
      }

    }
  }

  def getGrabberActor(ctx: ActorContext[Command], roomId: Long, liveId: String, source: InputStream, recorderRef: ActorRef[RecorderActor.Command]) = {
    val childName = s"grabberActor_$liveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(GrabberActor.create(roomId, liveId, source, recorderRef), childName)
      ctx.watchWith(actor,ChildDead4Grabber(roomId, childName, actor))
      actor
    }.unsafeUpcast[GrabberActor.Command]
  }

  def getRecorderActor(ctx: ActorContext[Command], roomId: Long, liveIdList: List[String], num: Int, speaker: String, pushLiveId:String, pushLiveCode:String,  out: OutputStream) = {
    val childName = s"recorderActor_$pushLiveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(RecorderActor.create(roomId, liveIdList, num, speaker, out), childName)
      ctx.watchWith(actor,ChildDead4Recorder(roomId, childName, actor))
      actor
    }.unsafeUpcast[RecorderActor.Command]
  }

  def getPullPipe(ctx: ActorContext[Command], roomId: Long, liveId: String, out: OutputStream) = {
    val childName = s"pullPipeActor_$liveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(StreamPullPipe.create(roomId: Long, liveId: String, out), childName)
      ctx.watchWith(actor, ChildDead4PullPipe(liveId, childName, actor))
      actor
    }.unsafeUpcast[StreamPullPipe.Command]
  }

  def getPushPipe(ctx: ActorContext[Command], roomId: Long, pushLiveId: String, pushLiveCode: String, source: SourceChannel, startTime:Long) = {
    val childName = s"pushPipeActor_$pushLiveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(StreamPushPipe.create(roomId, pushLiveId, pushLiveCode, source,startTime), childName)
      ctx.watchWith(actor, ChildDead4PushPipe(pushLiveId, childName, actor) )
      actor
    }.unsafeUpcast[StreamPushPipe.Command]
  }





}
