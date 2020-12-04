package ui

import javafx.application.Application
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import zio.blocking._
import zio.console._
import zio.{ExitCode, Schedule, UIO, URIO, ZIO, ZManaged}
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import media.Webcam
import ui.conrollers.Controllers
import zio.clock.Clock
import zio.duration._

object Main extends zio.App {
  var controllers: Controllers = _
  var initialized: Boolean = false

  def waitTillStart: ZManaged[Clock, Throwable, Unit] =
    ZIO().repeat(Schedule.fixed(100.millis).untilInputM(_ => UIO(initialized))).toManaged_.unit

  def background: ZManaged[zio.ZEnv, Throwable, Unit] =
    for {
      webcam <- Webcam.managed()
      _ <- waitTillStart
      _ <- controllers.roomController.start(webcam.stream, zio.stream.Stream.empty).toManaged_
    } yield ()

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      controllers <- Controllers.managed(this)
      _ <- ZIO.effect(this.controllers = controllers).toManaged_
      bgFiber <- background.fork
      _ <- effectBlocking(Application.launch(classOf[Main], args: _*)).toManaged_
      _ <- bgFiber.interrupt.toManaged_
    } yield ExitCode.success).useNow.catchAllCause(cause => putStrLn(cause.untraced.prettyPrint).as(ExitCode.failure))
}

class Main extends Application {
  def start(primaryStage: Stage): Unit = {
    val loader = new FXMLLoader
    loader.setController(Main.controllers.roomController)
    val xmlUrl = getClass.getResource("/scenes/room.fxml")
    loader.setLocation(xmlUrl)
    val root: BorderPane = loader.load
    primaryStage.setScene(new Scene(root))
    primaryStage.setWidth(600)
    primaryStage.setHeight(400)
    primaryStage.show()
    Main.initialized = true
  }
}