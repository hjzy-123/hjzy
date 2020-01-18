package com.sk.hjzy.roomManager.utils

import java.util.{Date, Properties}

import com.sk.hjzy.roomManager.common.AppSettings.mailConf
import javax.mail.Message.RecipientType
import javax.mail.{Address, Authenticator, PasswordAuthentication, Session, Transport}
import org.slf4j.LoggerFactory
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}

object EmailUtil {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val props = getProperties

  def getProperties: Properties = {
    val p = new Properties
    p.put("mail.smtp.host", mailConf.SMTPHOST)
    p.put("mail.smtp.port", mailConf.SMTPPORT)
    p.put("mail.transport.protocol", "smtp")
    p.put("mail.smtp.auth", "true")
    p
  }

  private val session = Session.getInstance(props,
    new MyAuthenticator(mailConf.EMAIL_ADDRESS, mailConf.EMAIL_PASSWORD))

  def send(subject: String, content: String, to: List[String], cc: List[String] = Nil, bcc: List[String] = Nil, contentType: String = "text/html; charset=utf-8"): Unit = {
    log.debug(s"EmailUtil.sendString subject: $subject, content: $content, to: $to, cc: $cc, bcc: $bcc")

    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(mailConf.EMAIL_ADDRESS))

    val toAddresses = to.map { email =>
      new InternetAddress(email).asInstanceOf[Address]
    }
    val ccAddresses = cc.map { email =>
      new InternetAddress(email).asInstanceOf[Address]
    }
    val bccAddresses = bcc.map { email =>
      new InternetAddress(email).asInstanceOf[Address]
    }

    message.setRecipients(RecipientType.TO, toAddresses.toArray)
    message.setRecipients(RecipientType.CC, ccAddresses.toArray)
    message.setRecipients(RecipientType.BCC, bccAddresses.toArray)

    message.setSubject(subject)
    message.setSentDate(new Date)

    //set content
    val mimeMultipart = new MimeMultipart
    val mimeBodyPart = new MimeBodyPart
    mimeBodyPart.setContent(content, contentType)
    mimeMultipart.addBodyPart(mimeBodyPart)
    message.setContent(mimeMultipart)

    Transport.send(message)

  }

  /*def sendFile(subject: String, file: File, to: List[String], cc: List[String] = Nil, bcc: List[String] = Nil): Unit = {
    log.debug(s"EmailUtil.sendString subject: $subject, , to: $to, cc: $cc, bcc: $bcc")
    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(AppSettings.EMAIL_ADDRESS))

    val toAddresses = to.map { email =>
      new InternetAddress(email).asInstanceOf[Address]
    }
    val ccAddresses = cc.map { email =>
      new InternetAddress(email).asInstanceOf[Address]
    }
    val bccAddresses = bcc.map { email =>
      new InternetAddress(email).asInstanceOf[Address]
    }

    message.setRecipients(RecipientType.TO, toAddresses.toArray)
    message.setRecipients(RecipientType.CC, ccAddresses.toArray)
    message.setRecipients(RecipientType.BCC, bccAddresses.toArray)

    message.setSubject(subject)
    message.setSentDate(new Date)

    //set content
    val mimeMultipart = new MimeMultipart
    val mimeBodyPart = new MimeBodyPart
    mimeBodyPart.attachFile(file)
    mimeMultipart.addBodyPart(mimeBodyPart)
    message.setContent(mimeMultipart)

    Transport.send(message)
  }*/

  def main(args: Array[String]): Unit = {
    send("注册验证码", "你的验证码：1024562", List("284660487@qq.com"), List())
  }

}


/**
  * User: Huangshanqi
  * Date: 2015/2/6
  * Time: 18:42
  */
case class MyAuthenticator(userName: String, password: String) extends Authenticator {

  override def getPasswordAuthentication: PasswordAuthentication = {
    new PasswordAuthentication(userName, password)
  }
}