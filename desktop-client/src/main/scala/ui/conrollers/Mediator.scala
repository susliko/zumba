package ui.conrollers

import java.awt.image.BufferedImage

import ui._
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.{BorderPane, GridPane, VBox}
import javafx.stage.Stage
import media.{ImageSegment, Playback, Webcam}
import ui.conrollers.menu.MenuController
import ui.conrollers.room.RoomController
import zio.{Ref, Runtime, Task, TaskManaged, UIO}
import zio.stream.Stream

class Mediator(primaryStage: Stage, inputOptions: Ref[InputOptions], activeController: Ref[Controller])(implicit runtime: Runtime[Any]) {

  def getInputOptions: Task[InputOptions] = inputOptions.get

  def useWebcam(name: String): TaskManaged[Webcam] =
    activeController.get.toManaged_.flatMap {
      case Controller.Menu(menu) =>
        Webcam.managedByName(name)
      case Controller.Room(room) =>
        Webcam.managedByName(name)
    }

  def getActiveWebcam: TaskManaged[Webcam] =
    inputOptions.get.toManaged_.flatMap(options =>
      Webcam.managedByName(options.activeVideo)
    )

  def switchScene(sceneType: SceneType): Task[Unit] =
    sceneType match {
      case SceneType.Menu =>
        val xmlUrl = getClass.getResource("/scenes/menu.fxml")
        val loader = new FXMLLoader
        loader.setLocation(xmlUrl)
        val menuController = new MenuController(this)
        loader.setController(menuController)
        val root: GridPane = loader.load
        runOnFxThread(() => {
          primaryStage.setScene(new Scene(root))
          primaryStage.show()
          primaryStage.setWidth(600)
          primaryStage.setHeight(400)
        }).onError(x => UIO(println(x)))

      case SceneType.Room =>
        (for {
          roomController <- RoomController.acquireRoomController
          scene <- Task {
            val xmlUrl = getClass.getResource("/scenes/room.fxml")
            val loader = new FXMLLoader
            loader.setController(roomController)
            loader.setLocation(xmlUrl)
            val root: BorderPane = loader.load
            new Scene(root)
          }
          _ <- runOnFxThread { () =>
            primaryStage.setScene(scene)
            primaryStage.show()
          }
          selfVideoStream = Webcam.managed().map(_.stream)
          _ <- roomController.start(selfVideoStream, Stream.empty)
          _ <- getActiveWebcam
            .use(webcam =>
              webcam.stream.run(
                roomController.selfVideoSink
                  .zipPar(roomController.imageSegmentsSink.contramapChunks(chunk => chunk.flatMap(ImageSegment.fromImage(_, 1, 1))))
              )
            )
        } yield ()).onError(x => UIO(println(x.prettyPrint)))
    }
}

object Mediator {
  def apply(primaryStage: Stage)(implicit runtime: Runtime[Any]): Task[Mediator] =
    for {
      audioNames <- Playback.names()
      videoNames <- Webcam.names
      inputOptions <- Ref.make(InputOptions(audioNames.head, videoNames.head))
      activeController <- Ref.make[Controller](null)
    } yield new Mediator(primaryStage, inputOptions, activeController)
}
