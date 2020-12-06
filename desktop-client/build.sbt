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
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.0.0-RC10",
  "com.softwaremill.sttp.client3" %% "circe" % "3.0.0-RC10",
  "org.manatki" %% "derevo-circe" % "0.11.5",
  "com.github.sarxos" % "webcam-capture" % "0.3.10",
  "io.circe" %% "circe-core" % "0.14.0-M1",
  "org.manatki" %% "derevo-cats" % "0.11.5"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-generic-extras",
  "io.circe" %% "circe-parser"
).map(_ % "0.13.0")

mainClass in Compile := Some("ui.Main")
discoveredMainClasses in Compile := Seq()

enablePlugins(UniversalPlugin)
enablePlugins(DebianPlugin)
linuxPackageMappings in Debian := linuxPackageMappings.value

val osName: SettingKey[String] = SettingKey[String]("osName")

osName := (System.getProperty("os.name") match {
  case name if name.startsWith("Linux")   => "linux"
  case name if name.startsWith("Mac")     => "mac"
  case name if name.startsWith("Windows") => "win"
  case _                                  => throw new Exception("Unknown platform!")
})

libraryDependencies += "org.openjfx" % "javafx-base" % "11-ea+25" classifier osName.value
libraryDependencies += "org.openjfx" % "javafx-controls" % "11-ea+25" classifier osName.value
libraryDependencies += "org.openjfx" % "javafx-fxml" % "11-ea+25" classifier osName.value
libraryDependencies += "org.openjfx" % "javafx-graphics" % "11-ea+25" classifier osName.value
libraryDependencies += "org.openjfx" % "javafx-swing" % "11-ea+25" classifier osName.value
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

mainClass in assembly := Some("media.MediaMain")
