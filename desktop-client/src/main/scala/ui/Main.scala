package ui

import javafx.application.Application
import javafx.scene.layout.{BorderPane, GridPane, HBox}
import javafx.stage.Stage
import zio.blocking._
import zio.console._
import zio.{ExitCode, Schedule, UIO, URIO, ZIO, ZManaged}
import javafx.fxml.FXMLLoader
import javafx.scene.{Node, Scene}
import media.Webcam
import ui.conrollers.{Mediator, SceneType}
import zio.clock.Clock
import zio.duration._

object Main extends zio.App {
  var initialized: Boolean = false
  var primaryStage: Stage = _

  def waitTillStart: ZManaged[Clock, Throwable, Unit] =
    ZIO().repeat(Schedule.fixed(100.millis).untilInputM(_ => UIO(initialized))).toManaged_.unit

  def background: ZManaged[zio.ZEnv, Throwable, Unit] =
    for {
//      webcam <- Webcam.managed()
      _ <- waitTillStart
      mediator = new Mediator(Main.primaryStage)
      _ <- mediator.switchScene(SceneType.Menu)(this).toManaged_
//      _ <- controllers.roomController.start(webcam.stream, zio.stream.Stream.empty).toManaged_
    } yield ()

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      bgFiber <- background.fork
      _ <- effectBlocking(Application.launch(classOf[Main], args: _*)).toManaged_
      _ <- bgFiber.interrupt.toManaged_
    } yield ExitCode.success).useNow.catchAllCause(cause => putStrLn(cause.untraced.prettyPrint).as(ExitCode.failure))
}

class Main extends Application {
  def start(primaryStage: Stage): Unit = {
    Main.primaryStage = primaryStage
    Main.initialized = true
  }
}