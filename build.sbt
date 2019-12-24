lazy val commonSettings = Seq(
  scalaVersion := "2.13.1",
  organization := "hu.ksisu",
  scalafmtOnCompile := true,
  version := "0.1.0"
)

lazy val root = project
  .in(file("."))
  .aggregate(core)

lazy val core = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "api-worker-example",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-unchecked",
      "-feature",
      "-language:implicitConversions",
      "-Ywarn-dead-code",
      "-Xlint"
    ),
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"  %% "akka-http"                % "10.1.10",
        "com.typesafe.akka"  %% "akka-http-spray-json"     % "10.1.10",
        "com.typesafe.akka"  %% "akka-actor"               % "2.6.1",
        "com.typesafe.akka"  %% "akka-stream"              % "2.6.1",
        "com.typesafe.akka"  %% "akka-slf4j"               % "2.6.1",
        "ch.qos.logback"     % "logback-classic"           % "1.2.3",
        "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "1.1.2",
        "com.github.etaty"   %% "rediscala"                % "1.9.0",
        "org.scalatest"      %% "scalatest"                % "3.1.0" % "test"
      )
    }
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("testAll", "test")

cancelable in Global := true
