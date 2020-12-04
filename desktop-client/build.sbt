name := "zumba"

version := "0.1"
maintainer := "Peka"
packageSummary := "Videochat"
packageDescription := "Videochat"

scalaVersion := "2.13.3"
scalacOptions += "-Ymacro-annotations"

addCompilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full
)

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.3",
  "dev.zio" %% "zio-streams" % "1.0.3",
  "dev.zio" %% "zio-nio" % "1.0.0-RC10",
  "com.softwaremill.sttp.client3" %% "core" % "3.0.0-RC10",
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.0.0-RC10",
  "org.manatki" %% "derevo-circe" % "0.11.5",
  "com.github.sarxos" % "webcam-capture" % "0.3.10",
  "io.circe" %% "circe-core" % "0.14.0-M1"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % "0.14.0-M1")

mainClass in Compile := Some("ui.Main")
discoveredMainClasses in Compile := Seq()

enablePlugins(DebianPlugin)
linuxPackageMappings in Debian := linuxPackageMappings.value
