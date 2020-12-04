package ui.conrollers.room

import java.awt.image.BufferedImage

import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.{FlowPane, StackPane, TilePane}
import media.ImageSegment
import zio.console._
import zio.stream._
import zio.{Fiber, RIO, Ref, Task, UIO, ZIO, ZManaged}

import scala.util.Try

class RoomController(
                      val selfTile: Ref[Option[TileInfo]],
                      val tiles: Ref[Map[Byte, TileInfo]],
                      selfVideoProcess: Ref[Option[Fiber[Throwable, Unit]]],
                      videoProcess: Ref[Option[Fiber[Throwable, Unit]]],
                    )(implicit runtime: zio.Runtime[Any]) {

  @FXML
  var tilesPane: TilePane = _

  var images: List[ImageView] = List.empty

  def tilesNum(size: Int): Int =
    Math.sqrt(size).ceil.toInt

  def addOne(): Unit = {
    runtime.unsafeRunAsync_(
      tiles
        .get
        .map(_.maxByOption(_._1).fold(0)(_._1))
        .flatMap(maxId => addUser((maxId + 1).toByte, s"Это тоже я ${maxId + 1}"))
    )
  }

  def removeOne(): Unit = {
    runtime.unsafeRunAsync_(
      tiles
        .get
        .map(_.maxByOption(_._1))
        .flatMap(key => ZIO.foreach(key)(pair => removeUser(pair._1)))
    )
  }

  def makeTileNode(userName: String, imageView: ImageView): StackPane = {
    val label = new Label(userName)
    val tileNode = new StackPane(imageView, label)
    tileNode.setStyle("-fx-border-color: blue; -fx-border-width: 1 ; ")
    tileNode
  }

  def addUser(userId: Byte, userName: String): Task[Unit] = {
    val imageView = new ImageView()
    val bufferedImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
    val node = makeTileNode(userName, imageView)
    val tileInfo = TileInfo(userName, node, imageInfo = Some(ImageInfo(imageView, bufferedImage)))

    for {
      _ <- tiles.update(_.updated(userId, tileInfo))
      _ <- ZIO(Platform.runLater(() => addTile(node)))
    } yield ()
  }

  def removeUser(userId: Byte): Task[Unit] = {
    tiles
      .modify(tiles => (tiles.get(userId), tiles.removed(userId)))
      .collectM[Any, Throwable, Unit](new NoSuchElementException(s"User $userId is not a member of room")) {
        case Some(tileInfo) => ZIO(Platform.runLater(() => tilesPane.getChildren.remove(tileInfo.node))).unit
      }
  }

  def addTile(tileNode: StackPane): Unit = {
    tileNode.setAlignment(Pos.CENTER)
    tilesPane.getChildren.add(tileNode)
  }

  def consumeSelfVideo(selfVideoStream: Stream[Throwable, BufferedImage]): Task[Unit] =
    for {
      fiber <- selfVideoStream.foreach(image =>
        selfTile.get.flatMap(
          selfTile =>
            ZIO(
              for {
                tile <- selfTile
                imageInfo <- tile.imageInfo
                _ = imageInfo.imageView.setImage(SwingFXUtils.toFXImage(image, null))
              } yield ()
            )
        )
      ).forkDaemon
      maybeOldFiber <- videoProcess.getAndSet(Some(fiber))
      _ <- ZIO.foreach(maybeOldFiber)(_.interrupt)
    } yield ()

  // Process batches if too slow
  def consumeImageSegments(videoStream: Stream[Throwable, ImageSegment]): Task[Unit] =
    for {
      fiber <- videoStream.foreach(imageSegment =>
        tiles.update(tiles =>
          (for {
            tileInfo <- tiles.get(imageSegment.header.userId)
            imageInfo <- tileInfo.imageInfo
            newImageInfo = imageInfo.copy(bufferedImage = imageSegment.image)
            _ = Try(newImageInfo.imageView.setImage(SwingFXUtils.toFXImage(imageSegment.image, null)))
            newTileInfo = tileInfo.copy(imageInfo = Some(newImageInfo))
          } yield tiles.updated(imageSegment.header.userId, newTileInfo)).getOrElse(tiles)
        )
      ).forkDaemon
      maybeOldFiber <- videoProcess.getAndSet(Some(fiber))
      _ <- ZIO.foreach(maybeOldFiber)(_.interrupt)
    } yield ()

  def start(
             selfVideoStream: Stream[Throwable, BufferedImage],
             videoStream: Stream[Throwable, ImageSegment],
           ): Task[Unit] =
    for {
      _ <- consumeImageSegments(videoStream)

      selfImageView = new ImageView
      selfTileNode = makeTileNode("Это я", selfImageView)
      bufferedImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)
      selfTileInfo = TileInfo("Это я", selfTileNode, Some(ImageInfo(selfImageView, bufferedImage)))
      _ <- selfTile.set(Some(selfTileInfo))
      _ <- ZIO(Platform.runLater(() => addTile(selfTileNode)))
      _ <- consumeSelfVideo(selfVideoStream)
    } yield ()

  def stop: UIO[Unit] =
    for {
      maybeSelfVideoFiber <- selfVideoProcess.getAndSet(None)
      maybeVideoFiber <- videoProcess.getAndSet(None)
      _ <- ZIO.foreach(maybeSelfVideoFiber)(_.interrupt)
      _ <- ZIO.foreach(maybeVideoFiber)(_.interrupt)
    } yield ()
}

object RoomController {
  def acquireRoomController(implicit runtime: zio.Runtime[Any]): UIO[RoomController] =
    for {
      selfTile <- Ref.make[Option[TileInfo]](None)
      tiles <- Ref.make[Map[Byte, TileInfo]](Map.empty)
      selfVideoProcess <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
      videoProcess <- Ref.make[Option[Fiber[Throwable, Unit]]](None)
    } yield new RoomController(selfTile, tiles, selfVideoProcess, videoProcess)

  def managed(implicit runtime: zio.Runtime[Any]): ZManaged[Any, Throwable, RoomController] =
    ZManaged.make(acquireRoomController)(_.stop)
}
