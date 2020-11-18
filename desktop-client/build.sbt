name := "zumba"

version := "0.1"

scalaVersion := "2.13.3"
scalacOptions += "-Ymacro-annotations"

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full
)

libraryDependencies ++= Seq(
  "ru.tinkoff" %% "tofu" % "0.8.0",
  "dev.zio" %% "zio" % "1.0.3",
  "dev.zio" %% "zio-streams" % "1.0.3",
  "org.manatki" %% "derevo-circe" % "0.11.5",
  "com.github.sarxos" % "webcam-capture" % "0.3.10"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % "0.14.0-M1")
