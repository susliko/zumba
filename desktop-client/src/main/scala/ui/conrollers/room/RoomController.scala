package ui.conrollers.room

import java.awt.image.BufferedImage

import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.{CheckBox, ComboBox, Label}
import javafx.scene.image.ImageView
import javafx.scene.layout.{StackPane, TilePane}
import javafx.scene.text.Text
import media.{ImageSegment, Microphone, Playback, Webcam}
import ui.conrollers.{Mediator, SceneType}
import zio.stream._
import zio.{Fiber, Ref, Task, TaskManaged, UIO, ZIO}
import ui.runOnFxThread
import zio.blocking.Blocking

import scala.util.Try

class RoomController(
                      mediator: Mediator,
                      val selfTileRef: Ref[Option[TileInfo]],
                      val tilesRef: Ref[Map[Byte, TileInfo]]
                    )(implicit runtime: zio.Runtime[Blocking]) {

  @FXML
  var tilesPane: TilePane = _

  @FXML
  var roomNumberText: Text = _

  @FXML
  var debugCheckBox: CheckBox = _

  @FXML
  var debugPanel: Node = _

  @FXML
  var useMicrophoneCheckBox: CheckBox = _

  @FXML
  var selectMicrophoneComboBox: ComboBox[String] = _

  @FXML
  var usePlaybackCheckBox: CheckBox = _

  @FXML
  var selectPlaybackComboBox: ComboBox[String] = _

  @FXML
  var useWebcamCheckBox: CheckBox = _

  @FXML
  var selectWebcamComboBox: ComboBox[String] = _

  // ***** Handlers *****

  @FXML
  def addOne(): Unit = {
    runtime.unsafeRunAsync_(
      tilesRef
        .get
        .map(_.maxByOption(_._1).fold(0)(_._1))
        .flatMap(maxId => addUser((maxId + 1).toByte, s"Это тоже я ${maxId + 1}"))
    )
  }

  @FXML
  def removeOne(): Unit = {
    runtime.unsafeRunAsync_(
      tilesRef
        .get
        .map(_.maxByOption(_._1))
        .flatMap(key => ZIO.foreach(key)(pair => removeUser(pair._1)))
    )
  }

  @FXML
  def switchMicrophone(): Unit = {
    runtime.unsafeRunAsync_(
      if (useMicrophoneCheckBox.isSelected) {
        mediator.enableMicrophone
      } else {
        mediator.disableMicrophone
      }
    )
  }

  @FXML
  def selectMicrophone(): Unit = {
    runtime.unsafeRunAsync_(
      mediator.selectMicrophone(selectMicrophoneComboBox.getValue)
    )
  }

  @FXML
  def switchPlayback(): Unit = {
    runtime.unsafeRunAsync_(
      if (usePlaybackCheckBox.isSelected) {
        mediator.enablePlayback
      } else {
        mediator.disablePlayback
      }
    )
  }

  @FXML
  def selectPlayback(): Unit = {
    runtime.unsafeRunAsync_(
      mediator.selectPlayback(selectPlaybackComboBox.getValue)
    )
  }

  @FXML
  def switchWebcam(): Unit = {
    runtime.unsafeRunAsync_(
      if (useWebcamCheckBox.isSelected) {
        mediator.enableWebcam
      } else {
        mediator.disableWebcam
      }
    )
  }

  @FXML
  def selectWebcam(): Unit = {
    runtime.unsafeRunAsync_(
      mediator.selectWebcam(selectWebcamComboBox.getValue)
    )
  }

  @FXML
  def leave(): Unit = {
    runtime.unsafeRunAsync_(
      mediator.switchScene(SceneType.Menu)
    )
  }

  // ***** API *****

  def makeTileNode(userName: String, imageView: ImageView): StackPane = {
    val label = new Label(userName)
    StackPane.setAlignment(label, Pos.BOTTOM_RIGHT)
    label.setStyle("-fx-background-color: rgba(150, 150, 150, 0.7)")
    val tileNode = new StackPane(imageView, label)
    tileNode.setStyle("-fx-border-color: blue; -fx-border-width: 1;")
    //    tilesPane.setPrefRows()!!!
    //    https://stackoverflow.com/questions/43369963/javafx-tile-pane-set-max-number-of-columns
    tileNode
  }

  def addUser(userId: Byte, userName: String): Task[Unit] = {
    val imageView = new ImageView()
    val bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    val node = makeTileNode(userName, imageView)
    val tileInfo = TileInfo(userName, node, imageInfo = Some(ImageInfo(imageView, bufferedImage)))

    for {
      _ <- tilesRef.update(_.updated(userId, tileInfo))
      _ <- runOnFxThread(() => addTile(node))
    } yield ()
  }

  def removeUser(userId: Byte): Task[Unit] = {
    tilesRef
      .modify(tiles => (tiles.get(userId), tiles.removed(userId)))
      .collectM[Any, Throwable, Unit](new NoSuchElementException(s"User $userId is not a member of room")) {
        case Some(tileInfo) => runOnFxThread(() => tilesPane.getChildren.remove(tileInfo.node))
      }
  }

  def addTile(tileNode: StackPane): Unit = {
    tileNode.setAlignment(Pos.CENTER)
    tilesPane.getChildren.add(tileNode)
  }

  def selfVideoSink: Sink[Throwable, BufferedImage, Any, Unit] =
    Sink.foreach(image =>
      selfTileRef.get.flatMap {
        selfTile =>
          ZIO(
            for {
              tile <- selfTile
              imageInfo <- tile.imageInfo
              _ = imageInfo.imageView.setImage(SwingFXUtils.toFXImage(image, null))
            } yield ()
          )
      }.catchAll(error =>
        UIO(println(s"Error while consuming self video: $error"))
      )
    )

  // Process batches if too slow
  def imageSegmentsSink: Sink[Throwable, ImageSegment, Any, Unit] =
    Sink.foreach { imageSegment =>
      tilesRef.update { tiles =>
        (for {
          tileInfo <- tiles.get(imageSegment.header.userId)
          imageInfo <- tileInfo.imageInfo
          raster = imageSegment.toRaster
          rasterBounds = raster.getBounds
          leftX = rasterBounds.x + rasterBounds.width
          bottomY = rasterBounds.y + rasterBounds.height
          newTiles = if (leftX > imageInfo.bufferedImage.getWidth && bottomY > imageInfo.bufferedImage.getHeight) {
            val newBufferedImage = new BufferedImage(leftX, bottomY, BufferedImage.TYPE_INT_RGB)
            newBufferedImage.setData(imageInfo.bufferedImage.getData())
            newBufferedImage.setData(raster)
            tiles.updated(
              imageSegment.header.userId, tileInfo.copy(imageInfo = Some(imageInfo.copy(bufferedImage = newBufferedImage)))
            )
          } else {
            imageInfo.bufferedImage.setData(raster)
            tiles
          }
          _ = Try(imageInfo.imageView.setImage(SwingFXUtils.toFXImage(imageInfo.bufferedImage, null)))
        } yield newTiles).getOrElse(tiles)
      }
    }

  def start: Task[Unit] =
    for {
      settings <- mediator.getSettings
      selfImageView = new ImageView
      selfTileNode = makeTileNode(settings.name, selfImageView)
      bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
      selfTileInfo = TileInfo(settings.name, selfTileNode, Some(ImageInfo(selfImageView, bufferedImage)))
      _ <- selfTileRef.set(Some(selfTileInfo))

      microphoneNames <- Microphone.names()
      playbackNames <- Playback.names()
      webcamNames <- Webcam.names

      _ <- runOnFxThread { () =>
        selectMicrophoneComboBox.getItems.setAll(microphoneNames: _*)
        selectPlaybackComboBox.getItems.setAll(playbackNames: _*)
        selectWebcamComboBox.getItems.setAll(webcamNames: _*)
        selectMicrophoneComboBox.setValue(settings.selectedMicrophone)
        selectPlaybackComboBox.setValue(settings.selectedPlayback)
        selectWebcamComboBox.setValue(settings.selectedWebcam)
        useMicrophoneCheckBox.setSelected(settings.useMicrophone)
        usePlaybackCheckBox.setSelected(settings.usePlayback)
        useWebcamCheckBox.setSelected(settings.useWebcam)
        roomNumberText.setText(s"Комната #${settings.roomId}")
        addTile(selfTileNode)
      }
    } yield ()

}

object RoomController {
  def apply(mediator: Mediator)(implicit runtime: zio.Runtime[Blocking]): UIO[RoomController] =
    for {
      selfTile <- Ref.make[Option[TileInfo]](None)
      tiles <- Ref.make[Map[Byte, TileInfo]](Map.empty)
    } yield new RoomController(mediator, selfTile, tiles)
}
