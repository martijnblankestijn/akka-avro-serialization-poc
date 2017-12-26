organization := "Martijn Blankestijn"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.12.4"
name := "akka-avro-serialization-poc"

// Header plugin information (together with organization)
startYear := Some(2017)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

val scalaTestVersion = "3.0.4"
val akkaVersion      = "2.5.8"
val akkaHttpVersion  = "10.0.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"             % akkaVersion,
  "com.typesafe.akka"         %% "akka-slf4j"             % akkaVersion, // needed for logging filter akka.event.slf4j.Slf4jLoggingFilter
  "com.typesafe.akka"         %% "akka-persistence"       % akkaVersion,
  "com.typesafe.akka"         %% "akka-persistence-query" % akkaVersion, // query-side of cqrs
  "com.typesafe.akka"         %% "akka-http"              % akkaHttpVersion,
  "com.typesafe.akka"         %% "akka-http-spray-json"   % akkaHttpVersion,
  "ch.qos.logback"            % "logback-classic"         % "1.2.3",
  "org.fusesource.leveldbjni" % "leveldbjni-all"          % "1.8",
  "com.sksamuel.avro4s"       %% "avro4s-core"            % "1.8.0",
  "com.typesafe.akka"         %% "akka-testkit"           % akkaVersion % "test",
  "org.scalatest"             %% "scalatest"              % scalaTestVersion % "test"
)

// run scalafmt automatically before compiling for all projects
scalafmtOnCompile in ThisBuild := true
mainClass in (Compile, packageBin) := Some("nl.codestar.api.Server")
