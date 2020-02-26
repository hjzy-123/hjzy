package com.sk.hjzy.processor.http

import java.io.{BufferedReader, File, FileInputStream, FileOutputStream, InputStreamReader}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.sk.hjzy.processor.protocol.SharedProtocol._
import com.sk.hjzy.processor.protocol.CommonErrorCode.{fileNotExistError, parseJsonError, updateRoomError}
import com.sk.hjzy.processor.utils.ServiceUtils
import com.sk.hjzy.processor.Boot.{executor, roomManager, scheduler, showStreamLog, timeout}
import io.circe.Error
import io.circe.generic.auto._
import com.sk.hjzy.processor.core.RoomManager
import com.sk.hjzy.processor.common.AppSettings.debugPath
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.headers.HttpOriginRange
import cats.instances.duration
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import com.sk.hjzy.protocol.ptcl.processer2Manager.ProcessorProtocol.{CloseRoom, CloseRoomRsp, NewConnect, NewConnectRsp, RecordInfoRsp, SeekRecord, UpdateRoomInfo, UpdateRsp}
import org.bytedeco.javacpp.Loader
import org.slf4j.LoggerFactory

import scala.concurrent.Future

trait ProcessorService extends ServiceUtils {

  private val settings = CorsSettings.defaultSettings.withAllowedOrigins(
    HttpOriginMatcher.*
  )

  case class RecordInfo(fileExist:Boolean,duration:String)

  private val log = LoggerFactory.getLogger(this.getClass)

  private def newConnect: Route = (path("newConnect") & post) {
    entity(as[Either[Error, NewConnect]]) {
      case Right(req) =>
        log.info(s"post method $NewConnect")
        val startTime = System.currentTimeMillis()
        roomManager ! RoomManager.NewConnection(req.roomId, req.liveIdList, req.num, req.speaker,req.pushLiveId, req.pushLiveCode, startTime)
        complete(NewConnectRsp(startTime))
      case Left(e) =>
        complete(parseJsonError)
    }
  }

  private def closeRoom: Route = (path("closeRoom") & post) {
    entity(as[Either[Error, CloseRoom]]) {
      case Right(req) =>
        log.info(s"post method closeRoom ${req.roomId}.")
        roomManager ! RoomManager.CloseRoom(req.roomId)
        complete(CloseRoomRsp())

      case Left(e) =>
        complete(parseJsonError)
    }
  }

  private def updateRoomInfo: Route = (path("updateRoomInfo") & post) {
    entity(as[Either[Error, UpdateRoomInfo]]) {
      case Right(req) =>
        log.info(s"post method updateRoomInfo.")
        roomManager ! RoomManager.UpdateRoomInfo(req.roomId, req.liveIdList, req.num, req.speaker)
        complete(UpdateRsp())
      case Left(e) =>
        complete(parseJsonError)
    }
  }

  def tempDestination(fileInfo: FileInfo): File =
    File.createTempFile(fileInfo.fileName, ".tmp")

  def createNewFile(file:File, name:String): Boolean = {
    val fis =new FileInputStream(file)
    val picFile = new File("D:\\image\\"+ name)
    picFile.createNewFile()
    val fos = new FileOutputStream(picFile)
    var byteRead = 0
    val bytes = new Array[Byte](1024)
    byteRead = fis.read(bytes, 0, bytes.length)
    while(byteRead != -1){
      fos.write(bytes, 0, byteRead)
      byteRead = fis.read(bytes, 0, bytes.length)
    }
    fos.flush()
    fos.close()
    fis.close()
    file.delete()
  }

  private val upLoadImg = (path("upLoadImg") & post) {
    storeUploadedFile("imgFile", tempDestination) {
      case (metadata, file) =>
        createNewFile(file, metadata.fileName)
        complete(UploadSuccessRsp(metadata.fileName))
    }
  }

  private val streamLog  = (path("streamLog") & get){
    showStreamLog = !showStreamLog
    complete(showStreamLog)
  }

  val getRecord: Route = (path("getRecord" / Segments(3)) & get & pathEndOrSingleSlash & cors(settings)){
    case roomId :: startTime :: file :: Nil =>
      println(s"getRecord req for $roomId/$startTime/$file.")
      val f = new File(s"$debugPath$roomId/$startTime/$file").getAbsoluteFile
      getFromFile(f,ContentTypes.`application/octet-stream`)

    case x =>
      log.error(s"errs in getRecord: $x")
      complete(fileNotExistError)
  }

  val seekRecord: Route = (path("seekRecord") & post) {
    entity(as[Either[Error, SeekRecord]]) {
      case Right(req) =>
        log.info("seekRecord.")
        val file = new File(s"$debugPath${req.roomId}/${req.startTime}/record.mp4")
        if(file.exists()){
          val d = getVideoDuration(req.roomId,req.roomId)
          log.info(s"duration:$d")
          complete(RecordInfoRsp(duration = d))
        }else{
          log.info(s"no record for roomId:${req.roomId} and startTime:${req.startTime}")
          complete(RecordInfoRsp(1000100,"record file not exist.",""))
        }

      case Left(e) =>
        log.info(s"err in seekRecord. error: ${e.getMessage}")
        complete(RecordInfoRsp(1000103,"parse json error",""))
    }
  }

  private def getVideoDuration(roomId:Long,startTime:Long) ={
    val ffprobe = Loader.load(classOf[org.bytedeco.ffmpeg.ffprobe])
    //容器时长（container duration）
    val pb = new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration", "-of","csv=\"p=0\"","-i", s"$debugPath$roomId/$startTime/record.mp4")
    val processor = pb.start()
    val br = new BufferedReader(new InputStreamReader(processor.getInputStream))
    val s = br.readLine()
    var duration = 0
    if(s!= null){
      duration = (s.toDouble * 1000).toInt
    }
    br.close()
    //    if(processor != null){
    //      processor.destroyForcibly()
    //    }
    millis2HHMMSS(duration)
  }

  def millis2HHMMSS(sec: Double): String = {
    val hours = (sec / 3600000).toInt
    val h =  if (hours >= 10) hours.toString else "0" + hours
    val minutes = ((sec % 3600000) / 60000).toInt
    val m = if (minutes >= 10) minutes.toString else "0" + minutes
    val seconds = ((sec % 60000) / 1000).toInt
    val s = if (seconds >= 10) seconds.toString else "0" + seconds
    val dec = ((sec % 1000) / 10).toInt
    val d = if (dec >= 10) dec.toString else "0" + dec
    s"$h:$m:$s.$d"
  }

  val processorRoute:Route = pathPrefix("processor") {
   newConnect  ~ closeRoom ~ updateRoomInfo  ~ upLoadImg ~ streamLog ~ getRecord ~ seekRecord
  }
}
