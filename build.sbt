val scalaV = "2.12.8"

val projectName = "hjzy"
val projectVersion = "2019.3.23"

resolvers += Resolver.sonatypeRepo("snapshots")

val projectMainClass = "com.neo.sk.hjzy.Boot"

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

lazy val shared =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(commonSettings: _*)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

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
      "io.circe" %%% "circe-core" % "0.11.1" withSources(),
      "io.circe" %%% "circe-generic" % "0.11.1",
      "io.circe" %%% "circe-parser" % "0.11.1",
      "org.scala-js" %%% "scalajs-dom" % "0.9.7",
      "io.suzaku" %%% "diode" % "1.1.4",
      "com.lihaoyi" %%% "scalatags" % "0.6.7" withSources(),
      "com.github.japgolly.scalacss" %%% "core" % "0.5.5" withSources(),
      "in.nvilla" %%% "monadic-html" % "0.4.0-RC1" withSources(),

    )
  )
  .dependsOn(sharedJs)


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
  .dependsOn(sharedJvm)



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
  .dependsOn(sharedJvm)

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
    packJvmOpts := Map("pcClient" -> Seq("-Xmx4096m", "-Xms128m")),
    packExtraClasspath := Map("pcClient" -> Seq("."))
  )
  .settings(
    //    libraryDependencies ++= Dependencies.backendDependencies,
    libraryDependencies ++= Dependencies.bytedecoLibs,
    libraryDependencies ++= Dependencies4PcClient.pcClientDependencies,
  )
  .dependsOn(capture, player)

// Akka Http based backend
lazy val roomManager = (project in file("roomManager")).enablePlugins(PackPlugin)
  .settings(commonSettings: _*)
  .settings(
    mainClass in reStart := Some(projectMainClass),
    javaOptions in reStart += "-Xmx2g"
  )
  .settings(name := "roomManager")
  .settings(
    //pack
    // If you need to specify main classes manually, use packSettings and packMain
    //packSettings,
    // [Optional] Creating `hello` command that calls org.mydomain.Hello#main(Array[String])
    packMain := Map("hjzy" -> projectMainClass),
    packJvmOpts := Map("hjzy" -> Seq("-Xmx64m", "-Xms32m")),
    packExtraClasspath := Map("hjzy" -> Seq("."))
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
  //  .settings(
  //    (resourceGenerators in Compile) += Def.task {
  //      val fullJsOut = (fullOptJS in Compile in frontend).value.data
  //      val fullJsSourceMap = fullJsOut.getParentFile / (fullJsOut.getName + ".map")
  //      Seq(
  //        fullJsOut,
  //        fullJsSourceMap
  //      )
  //    }.taskValue)
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
  .dependsOn(sharedJvm)

lazy val root = (project in file("."))
  .aggregate(webClient, roomManager)
  .settings(name := projectName)


