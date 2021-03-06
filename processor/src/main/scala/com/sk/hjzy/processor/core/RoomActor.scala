package com.sk.hjzy.processor.core

import java.io.{File, InputStream, OutputStream}
import java.net.ServerSocket
import java.nio.channels.Channels
import java.nio.channels.Pipe.{SinkChannel, SourceChannel}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.sk.hjzy.processor.common.AppSettings.{debugPath, isDebug}
import com.sk.hjzy.processor.stream.PipeStream
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import com.sk.hjzy.processor.Boot.streamPullActor
import org.bytedeco.javacpp.Loader

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
          val port = getFreePort
          val fFmpeg = new CreateFFmpeg(roomId, port, startTime)
          work(port,fFmpeg ,LiveIdList,mutable.Map[Long, List[(String,ActorRef[GrabberActor.Command])]](), mutable.Map[Long,ActorRef[RecorderActor.Command]](), mutable.Map[Long, List[String]]())
      }
    }
  }

  def work(port:Int,fFmpeg:CreateFFmpeg,
            LiveIdList: List[String],
    grabberMap: mutable.Map[Long, List[(String, ActorRef[GrabberActor.Command])]],
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

//          fFmpeg.start()

          liveIdList.foreach{ liveId =>
            val pullPipe4Live = new PipeStream
            val pullSink4Live = pullPipe4Live.getSink
            val pullSource4Live= pullPipe4Live.getSource
            val pullInput4Live = Channels.newInputStream(pullSource4Live)
            val pullOut4Live = Channels.newOutputStream(pullSink4Live)

            pipeMap.put(liveId, pullPipe4Live)

            val grabber4Live = getGrabberActor(ctx, msg.roomId, liveId, pullInput4Live, recorderActor)

            if(grabberMap.get(msg.roomId).nonEmpty)
              grabberMap(msg.roomId) = grabberMap(msg.roomId) ::: List((liveId, grabber4Live))
            else
              grabberMap.put(msg.roomId, List((liveId,grabber4Live)))

            val pullPipe4live = getPullPipe(ctx, msg.roomId,liveId, pullOut4Live)
            pullPipeMap.put(liveId, pullPipe4live)
          }

          val pushPipe4recorder = getPushPipe(ctx, msg.roomId, msg.pushLiveId, msg.pushLiveCode, pushSource,msg.startTime, port)
          pushPipeMap.put(msg.pushLiveId, pushPipe4recorder)
          recorderMap.put(msg.roomId, recorderActor)

          roomLiveMap.put(msg.roomId,List(msg.pushLiveId) ::: liveIdList)
//          streamPushActor ! StreamPushActor.NewLive(msg.pushLiveId, msg.pushLiveCode)
          Behaviors.same

        case UpdateRoomInfo(roomId, liveIdList, num, speaker) =>
          log.info(s"${ctx.self} receive a msg $msg")
          if(recorderMap.get(roomId).nonEmpty) {
            recorderMap.get(roomId).foreach(_ ! RecorderActor.UpdateRoomInfo(roomId,liveIdList, num, speaker ))
          } else {
            log.info(s"${roomId} recorder not exist")
          }

          liveIdList.foreach{ id =>
            if(id._2 == 1){
              val pullPipe4Live = new PipeStream
              val pullSink4Live = pullPipe4Live.getSink
              val pullSource4Live= pullPipe4Live.getSource
              val pullInput4Live = Channels.newInputStream(pullSource4Live)
              val pullOut4Live = Channels.newOutputStream(pullSink4Live)
              pipeMap.put(id._1, pullPipe4Live)

              val grabber4Live = getGrabberActor(ctx,roomId, id._1, pullInput4Live, recorderMap(roomId))

              if(grabberMap.get(roomId).nonEmpty)
                grabberMap(roomId) = grabberMap(roomId) ::: List((id._1, grabber4Live))
              else
                grabberMap.put(roomId, List((id._1,grabber4Live)))
              val pullPipe4live = getPullPipe(ctx, roomId,id._1, pullOut4Live)
              pullPipeMap.put(id._1, pullPipe4live)
              //fixme 能否开始拉流
              grabber4Live ! GrabberActor.Recorder(recorderMap(roomId))
            } else if(id._2 == -1){

              if(grabberMap.get(roomId).nonEmpty){
                grabberMap(roomId).filter( _._1 == id._1).foreach(_._2 ! GrabberActor.StopGrabber)
                grabberMap(roomId) = grabberMap(roomId).filter( _._1 != id._1)
              } else {
                log.info(s"${roomId}  grabbers not exist when closeRoom")
              }
              streamPullActor ! StreamPullActor.CleanStream(id._1)
              pullPipeMap.get(id._1).foreach( a => a ! StreamPullPipe.ClosePipe)
              pullPipeMap.remove(id._1)
              pipeMap.remove(id._1)

              if(roomLiveMap(roomId).nonEmpty)
                roomLiveMap(roomId) = roomLiveMap(roomId).filter(_ != id._1)
            }
          }

          Behaviors.same

        case msg:Recorder =>
          log.info(s"${ctx.self} receive a msg $msg")
          val grabberActor = grabberMap.get(msg.roomId)
          if(grabberActor.isDefined){
            grabberActor.get.foreach(_._2 ! GrabberActor.Recorder(msg.recorderRef))
          } else {
            log.info(s"${msg.roomId} grabbers not exist")
          }
          Behaviors.same

        case CloseRoom(roomId) =>
          log.info(s"${ctx.self} receive a msg $msg")
          if(grabberMap.get(roomId).nonEmpty){
            grabberMap.get(roomId).foreach{g => g.foreach(_._2 ! GrabberActor.StopGrabber)}
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
//          fFmpeg.close()
          fFmpeg.start()
          //todo
          timer.startSingleTimer(Timer4Stop, Stop, 3.minutes)
          Behaviors.same

        case ClosePipe(liveId) =>
          pushPipeMap.get(liveId).foreach( a => a ! StreamPushPipe.ClosePipe)
          pullPipeMap.remove(liveId)
          pushPipeMap.remove(liveId)
          pipeMap.remove(liveId)
          Behaviors.same

        case Stop =>
          log.info(s"${ctx.self} stopped ------")
          fFmpeg.close()
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

  def getGrabberActor(ctx: ActorContext[Command], roomId: Long, liveId: String, source: InputStream, recorderRef: ActorRef[RecorderActor.Command]): ActorRef[GrabberActor.Command] = {
    val childName = s"grabberActor_$liveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(GrabberActor.create(roomId, liveId, source, recorderRef), childName)
      ctx.watchWith(actor,ChildDead4Grabber(roomId, childName, actor))
      actor
    }.unsafeUpcast[GrabberActor.Command]
  }

  def getRecorderActor(ctx: ActorContext[Command], roomId: Long, liveIdList: List[String], num: Int, speaker: String, pushLiveId:String, pushLiveCode:String,  out: OutputStream): ActorRef[RecorderActor.Command] = {
    val childName = s"recorderActor_$pushLiveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(RecorderActor.create(roomId, liveIdList, num, speaker, out), childName)
      ctx.watchWith(actor,ChildDead4Recorder(roomId, childName, actor))
      actor
    }.unsafeUpcast[RecorderActor.Command]
  }

  def getPullPipe(ctx: ActorContext[Command], roomId: Long, liveId: String, out: OutputStream): ActorRef[StreamPullPipe.Command] = {
    val childName = s"pullPipeActor_$liveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(StreamPullPipe.create(roomId: Long, liveId: String, out), childName)
      ctx.watchWith(actor, ChildDead4PullPipe(liveId, childName, actor))
      actor
    }.unsafeUpcast[StreamPullPipe.Command]
  }

  def getPushPipe(ctx: ActorContext[Command], roomId: Long, pushLiveId: String, pushLiveCode: String, source: SourceChannel, startTime:Long, port:Int): ActorRef[StreamPushPipe.Command] = {
    val childName = s"pushPipeActor_$pushLiveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(StreamPushPipe.create(roomId, pushLiveId, pushLiveCode, source,startTime, port), childName)
      ctx.watchWith(actor, ChildDead4PushPipe(pushLiveId, childName, actor) )
      actor
    }.unsafeUpcast[StreamPushPipe.Command]
  }

  private def getFreePort: Int = {
    val serverSocket =  new ServerSocket(0) //读取空闲的可用端口
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

  class CreateFFmpeg(roomId: Long, port: Int, startTime:Long){
    private var process: Process = _

    def start(): Unit = {
      val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])

      //todo   转码命令
//      val pb = new ProcessBuilder(ffmpeg,"-f","mpegts", "-i",s"udp://127.0.0.1:$port","-c:v","libx264",s"$debugPath$roomId/${startTime}_record.mp4")
      val pb = new ProcessBuilder(ffmpeg,"-i",s"$debugPath$roomId/${startTime}_testRecord.mp4","-c:v","libx264",s"$debugPath$roomId/${startTime}_record.mp4")
      val process = pb.inheritIO().start()
      this.process = process
    }

    def close(): Unit ={
      if(this.process != null){
        this.process.destroyForcibly()
      }
      log.info(s"ffmpeg close successfully---")
    }
  }



}
