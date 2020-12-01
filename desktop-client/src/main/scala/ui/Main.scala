package ui

import javafx.application.Application
import javafx.scene.layout.{AnchorPane, VBox}
import javafx.stage.Stage
import zio.blocking._
import zio.console._
import zio.{ExitCode, URIO, ZIO}
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import media.Webcam

object Main extends zio.App {
  var controllers: Controllers = _

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      webcam <- Webcam.managed()
      _ <- ZIO.effect{
        controllers = Controllers(new TestController(webcam.stream))
      }.toManaged_
      _ <- controllers.mainSceneController.kek.forkDaemon.toManaged_
      _ <- effectBlocking(Application.launch(classOf[Main], args: _*)).toManaged_
    } yield ExitCode.success).useNow.catchAllCause(cause => putStrLn(cause.untraced.prettyPrint).as(ExitCode.failure))
}

class Main extends Application {
  def start(primaryStage: Stage): Unit = {
    val loader = new FXMLLoader
    loader.setController(Main.controllers.mainSceneController)
    val xmlUrl = getClass.getResource("/scenes/test.fxml")
    loader.setLocation(xmlUrl)
    val root: AnchorPane = loader.load
    primaryStage.setScene(new Scene(root))
    primaryStage.setWidth(600)
    primaryStage.setHeight(400)
    primaryStage.show()
  }
}