package ui.conrollers

import ui._
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.{BorderPane, GridPane}
import javafx.stage.Stage
import media.Webcam
import ui.conrollers.menu.MenuController
import ui.conrollers.room.RoomController
import zio.{Runtime, Task, UIO}
import zio.stream.Stream

class Mediator(primaryStage: Stage) {

  def switchScene(sceneType: SceneType)(implicit runtime: Runtime[Any]): Task[Unit] =
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
          webcam <- Webcam.managed()
          roomController <- RoomController.acquireRoomController.toManaged_
          scene <- Task {
            val xmlUrl = getClass.getResource("/scenes/room.fxml")
            val loader = new FXMLLoader
            loader.setController(roomController)
            loader.setLocation(xmlUrl)
            val root: BorderPane = loader.load
            new Scene(root)
          }.toManaged_
          _ <- runOnFxThread { () =>
            primaryStage.setScene(scene)
            primaryStage.show()
          }.toManaged_
          _ <- roomController.start(webcam.stream, Stream.empty).onError(x => UIO(println(x.prettyPrint))).toManaged_
        } yield ()).useForever.onError(x => UIO(println(x.prettyPrint)))
    }
}
