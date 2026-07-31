ThisBuild / organization := "com.goaltracker"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.4"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

val scala3 = "3.8.4"
val circeVersion = "0.14.14"
val http4sVersion = "0.23.30"
val laminarVersion = "17.2.1"
val munitVersion = "1.1.1"

lazy val root = (project in file("."))
  .aggregate(
    (shared.projectRefs ++ frontend.projectRefs ++ backend.projectRefs)*
  )
  .settings(
    name := "goal-tracker",
    publish / skip := true
  )

lazy val shared = (projectMatrix in file("shared"))
  .settings(
    name := "goal-tracker-shared",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))
  .jsPlatform(scalaVersions = Seq(scala3))

lazy val frontend = (projectMatrix in file("frontend"))
  .dependsOn(shared)
  .settings(
    name := "goal-tracker-frontend",
    scalaJSUseMainModuleInitializer := true,
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      (ThisBuild / baseDirectory).value / "web" / "js",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory :=
      (ThisBuild / baseDirectory).value / "web" / "js",
    libraryDependencies ++= Seq(
      "com.raquo" %% "laminar" % laminarVersion,
      "org.scala-js" %% "scalajs-dom" % "2.8.0"
    )
  )
  .jsPlatform(scalaVersions = Seq(scala3))

lazy val backend = (projectMatrix in file("backend"))
  .dependsOn(shared)
  .settings(
    name := "goal-tracker-backend",
    run / fork := true,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "org.scalameta" %% "munit" % munitVersion % Test
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3))
