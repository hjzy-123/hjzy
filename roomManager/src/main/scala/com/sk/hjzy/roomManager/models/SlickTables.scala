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
  lazy val schema: profile.SchemaDescription = tUserInfo.schema ++ tVideoInfo.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table tUserInfo
   *  @param id Database column id SqlType(BIGINT), AutoInc, PrimaryKey
   *  @param username Database column userName SqlType(VARCHAR), Length(255,true)
   *  @param email Database column email SqlType(VARCHAR), Length(255,true)
   *  @param password Database column password SqlType(VARCHAR), Length(255,true) */
  case class rUserInfo(id: Long, username: String, email: String, password: String)
  /** GetResult implicit for fetching rUserInfo objects using plain SQL queries */
  implicit def GetResultrUserInfo(implicit e0: GR[Long], e1: GR[String]): GR[rUserInfo] = GR{
    prs => import prs._
    rUserInfo.tupled((<<[Long], <<[String], <<[String], <<[String]))
  }
  /** Table description of table user_info. Objects of this class serve as prototypes for rows in queries. */
  class tUserInfo(_tableTag: Tag) extends profile.api.Table[rUserInfo](_tableTag, "user_info") {
    def * = (id, username, email, password) <> (rUserInfo.tupled, rUserInfo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(username), Rep.Some(email), Rep.Some(password))).shaped.<>({r=>import r._; _1.map(_=> rUserInfo.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column userName SqlType(VARCHAR), Length(255,true) */
    val username: Rep[String] = column[String]("userName", O.Length(255,varying=true))
    /** Database column email SqlType(VARCHAR), Length(255,true) */
    val email: Rep[String] = column[String]("email", O.Length(255,varying=true))
    /** Database column password SqlType(VARCHAR), Length(255,true) */
    val password: Rep[String] = column[String]("password", O.Length(255,varying=true))

    /** Uniqueness Index over (username) (database name user_info_userName_uindex) */
    val index1 = index("user_info_userName_uindex", username, unique=true)
  }
  /** Collection-like TableQuery object for table tUserInfo */
  lazy val tUserInfo = new TableQuery(tag => new tUserInfo(tag))

  /** Entity class storing rows of table tVideoInfo
   *  @param id Database column id SqlType(BIGINT), AutoInc, PrimaryKey
   *  @param userName Database column user_name SqlType(VARCHAR), Length(255,true)
   *  @param videoName Database column video_name SqlType(VARCHAR), Length(255,true)
   *  @param comments Database column comments SqlType(TEXT), Length(65535,true) */
  case class rVideoInfo(id: Long, userName: String, videoName: String, comments: String)
  /** GetResult implicit for fetching rVideoInfo objects using plain SQL queries */
  implicit def GetResultrVideoInfo(implicit e0: GR[Long], e1: GR[String]): GR[rVideoInfo] = GR{
    prs => import prs._
    rVideoInfo.tupled((<<[Long], <<[String], <<[String], <<[String]))
  }
  /** Table description of table video_info. Objects of this class serve as prototypes for rows in queries. */
  class tVideoInfo(_tableTag: Tag) extends profile.api.Table[rVideoInfo](_tableTag, "video_info") {
    def * = (id, userName, videoName, comments) <> (rVideoInfo.tupled, rVideoInfo.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(userName), Rep.Some(videoName), Rep.Some(comments))).shaped.<>({r=>import r._; _1.map(_=> rVideoInfo.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(BIGINT), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column user_name SqlType(VARCHAR), Length(255,true) */
    val userName: Rep[String] = column[String]("user_name", O.Length(255,varying=true))
    /** Database column video_name SqlType(VARCHAR), Length(255,true) */
    val videoName: Rep[String] = column[String]("video_name", O.Length(255,varying=true))
    /** Database column comments SqlType(TEXT), Length(65535,true) */
    val comments: Rep[String] = column[String]("comments", O.Length(65535,varying=true))

    /** Foreign key referencing tUserInfo (database name video_info_user_info_userName_fk) */
    lazy val tUserInfoFk = foreignKey("video_info_user_info_userName_fk", userName, tUserInfo)(r => r.username, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table tVideoInfo */
  lazy val tVideoInfo = new TableQuery(tag => new tVideoInfo(tag))
}
