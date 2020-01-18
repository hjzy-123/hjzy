package com.sk.hjzy.roomManager.service

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.server.{Directive, Directive1, RequestContext}
import com.sk.hjzy.protocol.ptcl.webClientManager.Common.ErrorRsp
import com.sk.hjzy.roomManager.common.AppSettings
import com.sk.hjzy.roomManager.utils.SessionSupport
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
/**
  * User: Taoz
  * Date: 12/4/2016
  * Time: 7:57 PM
  */

object SessionBase{
  private val log = LoggerFactory.getLogger(this.getClass)

  val SessionTypeKey = "STKey"

    object SessionKeys {
      val sessionType = "hjzy_session"
      val userId = "todos2018_userId"
      val account = "account"
      val timestamp = "timestamp"
    }

  case class UserBaseInfo(
    account: String
  )

    case class ToDoListSession(
                                userInfo:UserBaseInfo,
                                time: Long
                           ) {
      def toSessionMap: Map[String, String] = {
        Map(
          SessionTypeKey -> SessionKeys.sessionType,
          SessionKeys.account -> userInfo.account,
          SessionKeys.timestamp -> time.toString
        )
      }
    }
}

trait SessionBase extends SessionSupport with ServiceUtils{

  import SessionBase._

  override val sessionEncoder = SessionSupport.PlaySessionEncoder
  override val sessionConfig = AppSettings.sessionConfig
  private val timeout = AppSettings.sessionTimeOut * 60 * 60 * 1000
  implicit class SessionTransformer(sessionMap: Map[String, String]) {
    def toToDoListSession:Option[ToDoListSession] = {
      try {
        if (sessionMap.get(SessionTypeKey).exists(_.equals(SessionKeys.sessionType))) {
          if(sessionMap(SessionKeys.timestamp).toLong - System.currentTimeMillis() > timeout){
            None
          }else {
            Some(ToDoListSession(
              UserBaseInfo(sessionMap(SessionKeys.account)),
              sessionMap(SessionKeys.timestamp).toLong
            ))
          }
        } else {
          log.debug("no session type in the session")
          None
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          log.warn(s"toAdminSession: ${e.getMessage}")
          None
      }
    }
  }
  def loggingAction: Directive[Tuple1[RequestContext]] = extractRequestContext.map { ctx =>
//    log.info(s"Access uri: ${ctx.request.uri} from ip ${ctx.request.uri.authority.host.address}.")
    ctx
  }
  protected val optionalToDoSession: Directive1[Option[ToDoListSession]] = optionalSession.flatMap {
    case Right(sessionMap) => BasicDirectives.provide(sessionMap.toToDoListSession)
    case Left(error) =>
      log.debug(error)
      BasicDirectives.provide(None)
  }

  def noSessionError(message:String = "no session") = ErrorRsp(1000102,s"$message")

  //用户
  def userAuth(f: UserBaseInfo => server.Route) = loggingAction { ctx =>
    optionalToDoSession {
      case Some(session) =>
        f(session.userInfo)
      case None =>
        redirect("/hjzy#/login", StatusCodes.SeeOther)
    }
  }
}
