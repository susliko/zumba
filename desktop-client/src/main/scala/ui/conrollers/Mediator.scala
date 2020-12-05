package ui.conrollers

import java.awt.image.BufferedImage

import ui._
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.{BorderPane, GridPane}
import javafx.stage.Stage
import media.{ImageSegment, Playback, Webcam}
import ui.conrollers.menu.MenuController
import ui.conrollers.room.RoomController
import zio.{Chunk, Fiber, Ref, Runtime, Task, TaskManaged, UIO}

import scala.util.Random

class Mediator(
                primaryStage: Stage,
                settings: Ref[Settings],
                activeController: Ref[Option[Controller]],
                selfVideoFiber: Ref[Option[Fiber[Throwable, Unit]]],
                videoSegmentsFiber: Ref[Option[Fiber[Throwable, Unit]]]
              )(implicit runtime: Runtime[Any]) {

  def getSettings: Task[Settings] = settings.get

  def useWebcam(name: String): TaskManaged[Webcam] =
    activeController.get.toManaged_.flatMap {
      case Some(Controller.Menu(menu)) =>
        Webcam.managedByName(name)
      case Some(Controller.Room(room)) =>
        Webcam.managedByName(name)
    }

  def setName(name: String): Task[Unit] =
    activeController.get.flatMap {
      case Some(Controller.Menu(menu)) =>
        settings.update(_.copy(name = name))
      case Some(Controller.Room(room)) =>
        Task.unit
    }

  def getActiveWebcam: TaskManaged[Webcam] =
    settings.get.toManaged_.flatMap(options =>
      Webcam.managedByName(options.selectedVideo)
    )

  def kek(chunk: Chunk[BufferedImage]): Chunk[ImageSegment] =
    chunk
      .flatMap(ImageSegment.fromImage(_, 1, (Random.nextInt(2) + 1).toByte))
      .map(
        segment =>
          if (segment.header.userId == 2) {
            segment.copy(header = segment.header.copy(x = ((segment.header.x + 1) % 3).toByte))
          } else {
            segment
          }
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
        (activeController.set(Some(Controller.Menu(menuController))) *>
          runOnFxThread { () =>
            primaryStage.setScene(new Scene(root))
            primaryStage.show()
            primaryStage.setWidth(600)
            primaryStage.setHeight(400)
          }).onError(x => UIO(println(x)))

      case SceneType.Room =>
        (for {
          roomController <- RoomController.apply(this)
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
          _ <- activeController.set(Some(Controller.Room(roomController)))
          _ <- roomController.start
          // TODO: Send video and audio to server here
          fiber <- getActiveWebcam
            .use(webcam =>
              webcam.stream.run(
                roomController.selfVideoSink
                  .zipPar(roomController.imageSegmentsSink.contramapChunks(kek))
              )
            ).unit.forkDaemon
          maybeOldSelfVideoFiber <- selfVideoFiber.getAndSet(Some(fiber))
          _ <- Task.foreach(maybeOldSelfVideoFiber)(_.interrupt)
        } yield ()).onError(x => UIO(println(x.prettyPrint)))
    }
}

object Mediator {
  def apply(primaryStage: Stage)(implicit runtime: Runtime[Any]): Task[Mediator] =
    for {
      audioNames <- Playback.names()
      videoNames <- Webcam.names
      inputOptions <- Ref.make(Settings("Это я", useAudio = true, useVideo = true, audioNames.head, videoNames.head))
      activeController <- Ref.make[Option[Controller]](None)
      selfVideoFiber <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
      videoSegmentsFiber <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
    } yield new Mediator(primaryStage, inputOptions, activeController, selfVideoFiber, videoSegmentsFiber)
}
