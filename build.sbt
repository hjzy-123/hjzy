val scalaV = "2.12.8"

val projectName = "hjzy"
val projectVersion = "2019.3.24"

resolvers += Resolver.sonatypeRepo("snapshots")



def commonSettings = Seq(
  version := projectVersion,
  scalaVersion := scalaV,
  scalacOptions ++= Seq(
    //"-deprecation",
    "-feature"
  ),
  javacOptions ++= Seq("-encoding", "UTF-8")

)

// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val protocol = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings: _*)

lazy val protocolJvm = protocol.jvm
lazy val protocolJs = protocol.js

lazy val shared = (project in file("shared"))
  .settings(name := "shared")
  .settings(commonSettings: _*)

lazy val rtpClient = (project in file("rtpClient"))
  .settings(name := "rtpClient")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Dependencies.akkaSeq,
    libraryDependencies ++= Dependencies.backendDependencies)
  .dependsOn(shared)

val rtpServerMain = "com.sk.hjzy.rtpServer.Boot"

lazy val rtpServer = (project in file("rtpServer")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(rtpServerMain),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "rtpServer")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("rtpServer" -> rtpServerMain),
    packJvmOpts := Map("rtpServer" -> Seq("-Xmx512m", "-Xms32m")),
    packExtraClasspath := Map("rtpServer" -> Seq("."))
  )
  .settings(
    libraryDependencies ++= Dependencies.backendDependencies
  ).dependsOn(protocolJvm, shared, rtpClient)

// Scala-Js frontend
lazy val webClient = (project in file("webClient"))
  .enablePlugins(ScalaJSPlugin)
  .settings(name := "webClient")
  .settings(commonSettings: _*)
  .settings(
    inConfig(Compile)(
      Seq(
        fullOptJS,
        fastOptJS,
        scalaJSUseMainModuleInitializer,
        packageJSDependencies,
        packageMinifiedJSDependencies
      ).map(f => (crossTarget in f) ~= (_ / "sjsout"))
    ))
  .settings(skip in packageJSDependencies := false)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    //mainClass := Some("com.neo.sk.virgour.front.Main"),
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.8.0",
      "io.circe" %%% "circe-generic" % "0.8.0",
      "io.circe" %%% "circe-parser" % "0.8.0",
      "org.scala-js" %%% "scalajs-dom" % "0.9.2",
      "com.lihaoyi" %%% "scalatags" % "0.6.7" withSources(),
      "org.seekloud" %%% "byteobject" % "0.1.1",
      "in.nvilla" %%% "monadic-html" % "0.4.0-RC1" withSources()

    )
  )
  .dependsOn(protocolJs)


val captureMain = "org.seekloud.hjzy.capture.Boot"
lazy val capture = (project in file("capture")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(captureMain),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "capture")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("capture" -> pcClientMain),
    packJvmOpts := Map("capture" -> Seq("-Xmx256m", "-Xms64m")),
    packExtraClasspath := Map("capture" -> Seq("."))
  )
  .settings(
    //    libraryDependencies ++= Dependencies.backendDependencies,
    libraryDependencies ++= Dependencies.bytedecoLibs,
    libraryDependencies ++= Dependencies4Capture.captureDependencies,
  )
  .dependsOn(protocolJvm)



val playerMain = "org.seekloud.hjzy.player.Boot"
lazy val player = (project in file("player")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(playerMain),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "player")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("player" -> playerMain),
    packJvmOpts := Map("player" -> Seq("-Xmx256m", "-Xms64m")),
    packExtraClasspath := Map("player" -> Seq("."))
  )
  .settings(
    //    libraryDependencies ++= Dependencies.backendDependencies,
    libraryDependencies ++= Dependencies.bytedecoLibs,
    libraryDependencies ++= Dependencies4Player.playerDependencies,
  )
  .dependsOn(protocolJvm)

val pcClientMain = "org.seekloud.hjzy.pcClient.Boot"
lazy val pcClient = (project in file("pcClient")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(pcClientMain),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "pcClient")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("pcClient" -> pcClientMain),
    packJvmOpts := Map("pcClient" -> Seq("-Xmx4096m", "-Xms256m")),
    packExtraClasspath := Map("pcClient" -> Seq("."))
  )
  .settings(
    //    libraryDependencies ++= Dependencies.backendDependencies,
    libraryDependencies ++= Dependencies.bytedecoLibs,
    libraryDependencies ++= Dependencies4PcClient.pcClientDependencies,
  )
  .dependsOn(protocolJvm, capture, player, rtpClient)

// Akka Http based backend
val roomManagerMain = "com.sk.hjzy.roomManager.Boot"
lazy val roomManager = (project in file("roomManager")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(roomManagerMain),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "roomManager")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("roomManager" -> roomManagerMain),
    packJvmOpts := Map("roomManager" -> Seq("-Xmx64m", "-Xms32m")),
    packExtraClasspath := Map("roomManager" -> Seq("."))
  )
  .settings(
    libraryDependencies ++= Dependencies.backendDependencies
  )
  .settings {
    (resourceGenerators in Compile) += Def.task {
      val fastJsOut = (fastOptJS in Compile in webClient).value.data
      val fastJsSourceMap = fastJsOut.getParentFile / (fastJsOut.getName + ".map")
      Seq(
        fastJsOut,
        fastJsSourceMap
      )
    }.taskValue
  }
  .settings((resourceGenerators in Compile) += Def.task {
    Seq(
      (packageJSDependencies in Compile in webClient).value
      //(packageMinifiedJSDependencies in Compile in frontend).value
    )
  }.taskValue)
  .settings(
    (resourceDirectories in Compile) += (crossTarget in webClient).value,
    watchSources ++= (watchSources in webClient).value
  )
  .settings(scalaJSUseMainModuleInitializer := false)
  .dependsOn(protocolJvm)
  .settings(scalaJSUseMainModuleInitializer := false)
  .dependsOn(protocolJvm)


  .settings(scalaJSUseMainModuleInitializer := false)
  .dependsOn(protocolJvm)


val processorMain = "com.sk.hjzy.processor.Boot"

lazy val processor = (project in file("processor")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(processorMain),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "processor")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("processor" -> processorMain),
    packJvmOpts := Map("processor" -> Seq("-Xmx4g", "-Xms2g")),
    packExtraClasspath := Map("processor" -> Seq("."))
  )
  .settings(
    libraryDependencies ++= Dependencies.backendDependencies,
    libraryDependencies ++= Dependencies.bytedecoLibs
  ).dependsOn(protocolJvm, rtpClient)



