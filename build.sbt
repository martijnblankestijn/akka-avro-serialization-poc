organization := "Martijn Blankestijn"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.12.4"
name := "akka-avro-serialization-poc"

// Header plugin information (together with organization)
startYear := Some(2017)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

// to test the path of
resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")

val scalaTestVersion                = "3.0.4"
val akkaVersion                     = "2.5.8"
val akkaHttpVersion                 = "10.0.11"
val akkaPersistenceCassandraVersion = "0.80"
val kamonVersion                    = "1.0.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                 % akkaVersion,
  "com.typesafe.akka"         %% "akka-slf4j"                 % akkaVersion, // needed for logging filter akka.event.slf4j.Slf4jLoggingFilter
  "com.typesafe.akka"         %% "akka-persistence"           % akkaVersion,
  "com.typesafe.akka"         %% "akka-persistence-query"     % akkaVersion, // query-side of cqrs
  "com.typesafe.akka"         %% "akka-http"                  % akkaHttpVersion,
  "com.typesafe.akka"         %% "akka-http-spray-json"       % akkaHttpVersion,
  "com.typesafe.akka"         %% "akka-cluster"               % akkaVersion,
  "com.typesafe.akka"         %% "akka-cluster-sharding"      % akkaVersion,
  "com.typesafe.akka"         %% "akka-cluster-tools"         % akkaVersion,
  "com.typesafe.akka"         %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion,
  "ch.qos.logback"            % "logback-classic"             % "1.2.3",
  "org.fusesource.leveldbjni" % "leveldbjni-all"              % "1.8",
  "com.sksamuel.avro4s"       %% "avro4s-core"                % "1.8.0",
  "io.kamon"                  %% "kamon-core"                 % kamonVersion,
  "io.kamon"                  %% "kamon-akka-2.5"             % "1.0.1-a044c45062347e28c193e37afbe8a318ac430f",
  "io.kamon"                  %% "kamon-akka-http-2.5"        % kamonVersion,
  "io.kamon"                  %% "kamon-akka-remote-2.5"      % kamonVersion,
  "io.kamon"                  %% "kamon-prometheus"           % kamonVersion,
  "io.kamon"                  %% "kamon-zipkin"               % kamonVersion,
  "io.kamon"                  %% "kamon-jaeger"               % kamonVersion,
  "org.aspectj"               % "aspectjweaver"               % "1.8.13", // only to force the download of it

//  "io.kamon"                  %% "kamon-datadog"              % "0.6.7", // AS AN EXAMPLE USE DATADOG
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion      % "test",
  "org.scalatest"     %% "scalatest"    % scalaTestVersion % "test"
)

// run scalafmt automatically before compiling for all projects
scalafmtOnCompile in ThisBuild := true
mainClass in (Compile, packageBin) := Some("nl.codestar.api.Main")
