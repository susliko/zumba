package ui

import javafx.application.Application
import javafx.scene.layout.{BorderPane, GridPane, HBox}
import javafx.stage.Stage
import zio.blocking._
import zio.console._
import zio.{ExitCode, RIO, Schedule, Task, UIO, URIO, ZIO, ZManaged}
import javafx.fxml.FXMLLoader
import javafx.scene.{Node, Scene}
import media.Webcam
import ui.conrollers.{Mediator, SceneType}
import zio.clock.Clock
import zio.duration._

object Main extends zio.App {
  var initialized: Boolean = false
  var primaryStage: Stage = _

  def waitTillStart: RIO[Clock, Unit] =
    ZIO().repeat(Schedule.fixed(100.millis).untilInputM(_ => UIO(initialized))).unit

  def background: RIO[Clock, Unit] =
    for {
      _ <- waitTillStart
      mediator <- Mediator.apply(Main.primaryStage)(this)
      _ <- mediator.switchScene(SceneType.Menu)
    } yield ()

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      bgFiber <- background.fork
      _ <- effectBlocking(Application.launch(classOf[Main], args: _*))
      _ <- bgFiber.interrupt
    } yield ExitCode.success).catchAllCause(cause => putStrLn(cause.untraced.prettyPrint).as(ExitCode.failure))
}

class Main extends Application {
  def start(primaryStage: Stage): Unit = {
    Main.primaryStage = primaryStage
    Main.initialized = true
  }
}