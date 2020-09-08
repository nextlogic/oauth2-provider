name := """oauth2-test"""
organization := "pro.studentlogic.oauth"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "com.nulab-inc" %% "scala-oauth2-core" % "1.5.0",
  "com.nulab-inc" %% "play2-oauth2-provider" % "1.5.0",
  "org.postgresql" % "postgresql" % "42.2.5",

  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "net.nextlogic.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "net.nextlogic.binders._"
routesImport += "play.api.mvc.PathBindable.bindableUUID"


maintainer in Linux := "NextLogic Pte Ltd <peter@nextlogic.biz>"

packageSummary in Linux := "OAuth2 Provider Example"

packageDescription := "OAuth2 Provider Example"
