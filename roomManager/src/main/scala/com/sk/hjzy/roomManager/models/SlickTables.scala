package com.sk.hjzy.roomManager.models

// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object SlickTables extends {
  val profile = slick.jdbc.MySQLProfile
} with SlickTables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait SlickTables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(tComments.schema, tLoginEvent.schema, tObserveEvent.schema, tRecord.schema, tRecordComment.schema, tUserInfo.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table tComments
    *  @param id Database column id SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param content Database column content SqlType(TEXT)
    *  @param replyto Database column replyTo SqlType(VARCHAR), Length(100,true)
    *  @param `type` Database column type SqlType(INT)
    *  @param belongto Database column belongTo SqlType(BIGINT)
    *  @param recordid Database column recordId SqlType(BIGINT)
    *  @param createtime Database column createTime SqlType(BIGINT)
    *  @param author Database column author SqlType(VARCHAR), Length(100,true)
    *  @param authorImg Database column author_img SqlType(VARCHAR), Length(100,true) */
  case class rComments(id: Long, content: String, replyto: String, `type`: Int, belongto: Long, recordid: Long, createtime: Long, author: String, authorImg: String)
  /** GetResult implicit for fetching rComments objects using plain SQL queries */
  implicit def GetResultrComments(implicit e0: GR[Long], e1: GR[String], e2: GR[Int]): GR[rComments] = GR{
    prs => import prs._
      rComments.tupled((<<[Long], <<[String], <<[String], <<[Int], <<[Long], <<[Long], <<[Long], <<[String], <<[String]))
  }
  /** Table description of table comments. Objects of this class serve as prototypes for rows in queries.
    *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class tComments(_tableTag: Tag) extends profile.api.Table[rComments](_tableTag, Some("hjzy"), "comments") {
    def * = (id, content, replyto, `type`, belongto, recordid, createtime, author, authorImg) <> (rComments.tupled, rComments.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(content), Rep.Some(replyto), Rep.Some(`type`), Rep.Some(belongto), Rep.Some(recordid), Rep.Some(createtime), Rep.Some(author), Rep.Some(authorImg))).shaped.<>({r=>import r._; _1.map(_=> rComments.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column content SqlType(TEXT) */
    val content: Rep[String] = column[String]("content")
    /** Database column replyTo SqlType(VARCHAR), Length(100,true) */
    val replyto: Rep[String] = column[String]("replyTo", O.Length(100,varying=true))
    /** Database column type SqlType(INT)
      *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Rep[Int] = column[Int]("type")
    /** Database column belongTo SqlType(BIGINT) */
    val belongto: Rep[Long] = column[Long]("belongTo")
    /** Database column recordId SqlType(BIGINT) */
    val recordid: Rep[Long] = column[Long]("recordId")
    /** Database column createTime SqlType(BIGINT) */
    val createtime: Rep[Long] = column[Long]("createTime")
    /** Database column author SqlType(VARCHAR), Length(100,true) */
    val author: Rep[String] = column[String]("author", O.Length(100,varying=true))
    /** Database column author_img SqlType(VARCHAR), Length(100,true) */
    val authorImg: Rep[String] = column[String]("author_img", O.Length(100,varying=true))
  }
  /** Collection-like TableQuery object for table tComments */
  lazy val tComments = new TableQuery(tag => new tComments(tag))

  /** Entity class storing rows of table tLoginEvent
   *  @param id Database column id SqlType(BIGINT), AutoInc, PrimaryKey
   *  @param uid Database column uid SqlType(BIGINT)
   *  @param loginTime Database column login_time SqlType(BIGINT), Default(0) */
  case class rLoginEvent(id: Long, uid: Long, loginTime: Long = 0L)
  /** GetResult implicit for fetching rLoginEvent objects using plain SQL queries */
  implicit def GetResultrLoginEvent(implicit e0: GR[Long]): GR[rLoginEvent] = GR{
    prs => import prs._
    rLoginEvent.tupled((<<[Long], <<[Long], <<[Long]))
  }
  /** Table description of table login_event. Objects of this class serve as prototypes for rows in queries. */
  class tLoginEvent(_tableTag: Tag) extends profile.api.Table[rLoginEvent](_tableTag, "login_event") {
    def * = (id, uid, loginTime) <> (rLoginEvent.tupled, rLoginEvent.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(uid), Rep.Some(loginTime)).shaped.<>({r=>import r._; _1.map(_=> rLoginEvent.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column uid SqlType(BIGINT) */
    val uid: Rep[Long] = column[Long]("uid")
    /** Database column login_time SqlType(BIGINT), Default(0) */
    val loginTime: Rep[Long] = column[Long]("login_time", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tLoginEvent */
  lazy val tLoginEvent = new TableQuery(tag => new tLoginEvent(tag))

  /** Entity class storing rows of table tObserveEvent
   *  @param id Database column id SqlType(BIGINT), AutoInc, PrimaryKey
   *  @param uid Database column uid SqlType(BIGINT)
   *  @param recordid Database column recordId SqlType(BIGINT)
   *  @param inAnchor Database column in_Anchor SqlType(BIT)
   *  @param temporary Database column temporary SqlType(BIT)
   *  @param inTime Database column in_time SqlType(BIGINT), Default(0)
   *  @param outTime Database column out_time SqlType(BIGINT), Default(0) */
  case class rObserveEvent(id: Long, uid: Long, recordid: Long, inAnchor: Boolean, temporary: Boolean, inTime: Long = 0L, outTime: Long = 0L)
  /** GetResult implicit for fetching rObserveEvent objects using plain SQL queries */
  implicit def GetResultrObserveEvent(implicit e0: GR[Long], e1: GR[Boolean]): GR[rObserveEvent] = GR{
    prs => import prs._
    rObserveEvent.tupled((<<[Long], <<[Long], <<[Long], <<[Boolean], <<[Boolean], <<[Long], <<[Long]))
  }
  /** Table description of table observe_event. Objects of this class serve as prototypes for rows in queries. */
  class tObserveEvent(_tableTag: Tag) extends profile.api.Table[rObserveEvent](_tableTag, "observe_event") {
    def * = (id, uid, recordid, inAnchor, temporary, inTime, outTime) <> (rObserveEvent.tupled, rObserveEvent.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(uid), Rep.Some(recordid), Rep.Some(inAnchor), Rep.Some(temporary), Rep.Some(inTime), Rep.Some(outTime)).shaped.<>({r=>import r._; _1.map(_=> rObserveEvent.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column uid SqlType(BIGINT) */
    val uid: Rep[Long] = column[Long]("uid")
    /** Database column recordId SqlType(BIGINT) */
    val recordid: Rep[Long] = column[Long]("recordId")
    /** Database column in_Anchor SqlType(BIT) */
    val inAnchor: Rep[Boolean] = column[Boolean]("in_Anchor")
    /** Database column temporary SqlType(BIT) */
    val temporary: Rep[Boolean] = column[Boolean]("temporary")
    /** Database column in_time SqlType(BIGINT), Default(0) */
    val inTime: Rep[Long] = column[Long]("in_time", O.Default(0L))
    /** Database column out_time SqlType(BIGINT), Default(0) */
    val outTime: Rep[Long] = column[Long]("out_time", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tObserveEvent */
  lazy val tObserveEvent = new TableQuery(tag => new tObserveEvent(tag))

  /** Entity class storing rows of table tRecord
    *  @param id Database column id SqlType(BIGINT), AutoInc, PrimaryKey
    *  @param roomid Database column roomId SqlType(BIGINT), Default(0)
    *  @param starttime Database column startTime SqlType(BIGINT), Default(0)
    *  @param coverImg Database column cover_img SqlType(VARCHAR), Length(256,true)
    *  @param recordname Database column recordName SqlType(VARCHAR), Length(255,true)
    *  @param recorddes Database column recordDes SqlType(VARCHAR), Length(255,true)
    *  @param viewNum Database column view_num SqlType(INT), Default(0)
    *  @param likeNum Database column like_num SqlType(INT), Default(0)
    *  @param duration Database column duration SqlType(VARCHAR), Length(100,true)
    *  @param recordAddr Database column record_addr SqlType(VARCHAR), Length(100,true)
    *  @param allowUser Database column allow_user SqlType(TEXT), Length(65535,true) */
  case class rRecord(id: Long, roomid: Long = 0L, starttime: Long = 0L, coverImg: String = "", recordname: String = "", recorddes: String = "", viewNum: Int = 0, likeNum: Int = 0, duration: String = "", recordAddr: String = "", allowUser: String = "")
  /** GetResult implicit for fetching rRecord objects using plain SQL queries */
  implicit def GetResultrRecord(implicit e0: GR[Long], e1: GR[String], e2: GR[Int]): GR[rRecord] = GR{
    prs => import prs._
      rRecord.tupled((<<[Long], <<[Long], <<[Long], <<[String], <<[String], <<[String], <<[Int], <<[Int], <<[String], <<[String], <<[String]))
  }
  /** Table description of table record. Objects of this class serve as prototypes for rows in queries. */
  class tRecord(_tableTag: Tag) extends profile.api.Table[rRecord](_tableTag, "record") {
    def * = (id, roomid, starttime, coverImg, recordname, recorddes, viewNum, likeNum, duration, recordAddr, allowUser) <> (rRecord.tupled, rRecord.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(roomid), Rep.Some(starttime), Rep.Some(coverImg), Rep.Some(recordname), Rep.Some(recorddes), Rep.Some(viewNum), Rep.Some(likeNum), Rep.Some(duration), Rep.Some(recordAddr), Rep.Some(allowUser))).shaped.<>({r=>import r._; _1.map(_=> rRecord.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column roomId SqlType(BIGINT), Default(0) */
    val roomid: Rep[Long] = column[Long]("roomId", O.Default(0L))
    /** Database column startTime SqlType(BIGINT), Default(0) */
    val starttime: Rep[Long] = column[Long]("startTime", O.Default(0L))
    /** Database column cover_img SqlType(VARCHAR), Length(256,true) */
    val coverImg: Rep[String] = column[String]("cover_img", O.Length(256,varying=true))
    /** Database column recordName SqlType(VARCHAR), Length(255,true) */
    val recordname: Rep[String] = column[String]("recordName", O.Length(255,varying=true))
    /** Database column recordDes SqlType(VARCHAR), Length(255,true) */
    val recorddes: Rep[String] = column[String]("recordDes", O.Length(255,varying=true))
    /** Database column view_num SqlType(INT), Default(0) */
    val viewNum: Rep[Int] = column[Int]("view_num", O.Default(0))
    /** Database column like_num SqlType(INT), Default(0) */
    val likeNum: Rep[Int] = column[Int]("like_num", O.Default(0))
    /** Database column duration SqlType(VARCHAR), Length(100,true) */
    val duration: Rep[String] = column[String]("duration", O.Length(100,varying=true))
    /** Database column record_addr SqlType(VARCHAR), Length(100,true) */
    val recordAddr: Rep[String] = column[String]("record_addr", O.Length(100,varying=true))
    /** Database column allow_user SqlType(TEXT), Length(65535,true) */
    val allowUser: Rep[String] = column[String]("allow_user", O.Length(65535,varying=true))
  }
  /** Collection-like TableQuery object for table tRecord */
  lazy val tRecord = new TableQuery(tag => new tRecord(tag))

  /** Entity class storing rows of table tRecordComment
   *  @param roomId Database column room_id SqlType(BIGINT)
   *  @param recordTime Database column record_time SqlType(BIGINT)
   *  @param comment Database column comment SqlType(VARCHAR), Length(255,true)
   *  @param commentTime Database column comment_time SqlType(BIGINT)
   *  @param commentUid Database column comment_uid SqlType(BIGINT)
   *  @param authorUid Database column author_uid SqlType(BIGINT), Default(None)
   *  @param commentid Database column commentId SqlType(BIGINT)
   *  @param relativetime Database column relativeTime SqlType(BIGINT), Default(0) */
  case class rRecordComment(roomId: Long, recordTime: Long, comment: String, commentTime: Long, commentUid: Long, authorUid: Option[Long] = None, commentid: Long, relativetime: Long = 0L)
  /** GetResult implicit for fetching rRecordComment objects using plain SQL queries */
  implicit def GetResultrRecordComment(implicit e0: GR[Long], e1: GR[String], e2: GR[Option[Long]]): GR[rRecordComment] = GR{
    prs => import prs._
    rRecordComment.tupled((<<[Long], <<[Long], <<[String], <<[Long], <<[Long], <<?[Long], <<[Long], <<[Long]))
  }
  /** Table description of table record_comment. Objects of this class serve as prototypes for rows in queries. */
  class tRecordComment(_tableTag: Tag) extends profile.api.Table[rRecordComment](_tableTag, "record_comment") {
    def * = (roomId, recordTime, comment, commentTime, commentUid, authorUid, commentid, relativetime) <> (rRecordComment.tupled, rRecordComment.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(roomId), Rep.Some(recordTime), Rep.Some(comment), Rep.Some(commentTime), Rep.Some(commentUid), authorUid, Rep.Some(commentid), Rep.Some(relativetime)).shaped.<>({r=>import r._; _1.map(_=> rRecordComment.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column room_id SqlType(BIGINT) */
    val roomId: Rep[Long] = column[Long]("room_id")
    /** Database column record_time SqlType(BIGINT) */
    val recordTime: Rep[Long] = column[Long]("record_time")
    /** Database column comment SqlType(VARCHAR), Length(255,true) */
    val comment: Rep[String] = column[String]("comment", O.Length(255,varying=true))
    /** Database column comment_time SqlType(BIGINT) */
    val commentTime: Rep[Long] = column[Long]("comment_time")
    /** Database column comment_uid SqlType(BIGINT) */
    val commentUid: Rep[Long] = column[Long]("comment_uid")
    /** Database column author_uid SqlType(BIGINT), Default(None) */
    val authorUid: Rep[Option[Long]] = column[Option[Long]]("author_uid", O.Default(None))
    /** Database column commentId SqlType(BIGINT) */
    val commentid: Rep[Long] = column[Long]("commentId")
    /** Database column relativeTime SqlType(BIGINT), Default(0) */
    val relativetime: Rep[Long] = column[Long]("relativeTime", O.Default(0L))
  }
  /** Collection-like TableQuery object for table tRecordComment */
  lazy val tRecordComment = new TableQuery(tag => new tRecordComment(tag))

  /** Entity class storing rows of table tUserInfo
   *  @param uid Database column uid SqlType(BIGINT), AutoInc, PrimaryKey
   *  @param userName Database column user_name SqlType(VARCHAR), Length(100,true)
   *  @param password Database column password SqlType(VARCHAR), Length(100,true)
   *  @param roomid Database column roomId SqlType(BIGINT)
   *  @param token Database column token SqlType(VARCHAR), Length(63,true)
   *  @param tokenCreateTime Database column token_create_time SqlType(BIGINT), Default(2592000)
   *  @param headImg Database column head_img SqlType(VARCHAR), Length(256,true)
   *  @param coverImg Database column cover_img SqlType(VARCHAR), Length(256,true)
   *  @param email Database column email SqlType(VARCHAR), Length(256,true)
   *  @param createTime Database column create_time SqlType(BIGINT), Default(0)
   *  @param rtmpToken Database column rtmp_token SqlType(VARCHAR), Length(256,true)
   *  @param `sealed` Database column sealed SqlType(BIT)
   *  @param sealedUtilTime Database column sealed_util_time SqlType(BIGINT), Default(-1)
   *  @param allowAnchor Database column allow_anchor SqlType(BIT) */
  case class rUserInfo(uid: Long, userName: String, password: String, roomid: Long, token: String = "", tokenCreateTime: Long = 2592000L, headImg: String = "", coverImg: String = "", email: String = "", createTime: Long = 0L, rtmpToken: String = "", `sealed`: Boolean = false, sealedUtilTime: Long = -1L, allowAnchor: Boolean = true)
  /** GetResult implicit for fetching rUserInfo objects using plain SQL queries */
  implicit def GetResultrUserInfo(implicit e0: GR[Long], e1: GR[String], e2: GR[Boolean]): GR[rUserInfo] = GR{
    prs => import prs._
    rUserInfo.tupled((<<[Long], <<[String], <<[String], <<[Long], <<[String], <<[Long], <<[String], <<[String], <<[String], <<[Long], <<[String], <<[Boolean], <<[Long], <<[Boolean]))
  }
  /** Table description of table user_info. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: sealed */
  class tUserInfo(_tableTag: Tag) extends profile.api.Table[rUserInfo](_tableTag, "user_info") {
    def * = (uid, userName, password, roomid, token, tokenCreateTime, headImg, coverImg, email, createTime, rtmpToken, `sealed`, sealedUtilTime, allowAnchor) <> (rUserInfo.tupled, rUserInfo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(uid), Rep.Some(userName), Rep.Some(password), Rep.Some(roomid), Rep.Some(token), Rep.Some(tokenCreateTime), Rep.Some(headImg), Rep.Some(coverImg), Rep.Some(email), Rep.Some(createTime), Rep.Some(rtmpToken), Rep.Some(`sealed`), Rep.Some(sealedUtilTime), Rep.Some(allowAnchor)).shaped.<>({r=>import r._; _1.map(_=> rUserInfo.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13.get, _14.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column uid SqlType(BIGINT), AutoInc, PrimaryKey */
    val uid: Rep[Long] = column[Long]("uid", O.AutoInc, O.PrimaryKey)
    /** Database column user_name SqlType(VARCHAR), Length(100,true) */
    val userName: Rep[String] = column[String]("user_name", O.Length(100,varying=true))
    /** Database column password SqlType(VARCHAR), Length(100,true) */
    val password: Rep[String] = column[String]("password", O.Length(100,varying=true))
    /** Database column roomId SqlType(BIGINT) */
    val roomid: Rep[Long] = column[Long]("roomId")
    /** Database column token SqlType(VARCHAR), Length(63,true) */
    val token: Rep[String] = column[String]("token", O.Length(63,varying=true))
    /** Database column token_create_time SqlType(BIGINT), Default(2592000) */
    val tokenCreateTime: Rep[Long] = column[Long]("token_create_time", O.Default(2592000L))
    /** Database column head_img SqlType(VARCHAR), Length(256,true) */
    val headImg: Rep[String] = column[String]("head_img", O.Length(256,varying=true))
    /** Database column cover_img SqlType(VARCHAR), Length(256,true) */
    val coverImg: Rep[String] = column[String]("cover_img", O.Length(256,varying=true))
    /** Database column email SqlType(VARCHAR), Length(256,true) */
    val email: Rep[String] = column[String]("email", O.Length(256,varying=true))
    /** Database column create_time SqlType(BIGINT), Default(0) */
    val createTime: Rep[Long] = column[Long]("create_time", O.Default(0L))
    /** Database column rtmp_token SqlType(VARCHAR), Length(256,true) */
    val rtmpToken: Rep[String] = column[String]("rtmp_token", O.Length(256,varying=true))
    /** Database column sealed SqlType(BIT)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `sealed`: Rep[Boolean] = column[Boolean]("sealed")
    /** Database column sealed_util_time SqlType(BIGINT), Default(-1) */
    val sealedUtilTime: Rep[Long] = column[Long]("sealed_util_time", O.Default(-1L))
    /** Database column allow_anchor SqlType(BIT) */
    val allowAnchor: Rep[Boolean] = column[Boolean]("allow_anchor")

    /** Uniqueness Index over (userName) (database name user_info_user_name_index) */
    val index1 = index("user_info_user_name_index", userName, unique=true)
  }
  /** Collection-like TableQuery object for table tUserInfo */
  lazy val tUserInfo = new TableQuery(tag => new tUserInfo(tag))
}
