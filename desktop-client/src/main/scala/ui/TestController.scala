package ui

import java.awt.image.BufferedImage

import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.image.ImageView
import javafx.scene.layout.FlowPane
import zio.ZIO
import zio.stream._
import zio.console._
import zio.duration._

class TestController(stream: Stream[Throwable, BufferedImage]) {

  @FXML
  var videos: FlowPane = _

  var images: List[ImageView] = List.empty

  def addOne(): Unit = {
    val newImage = new ImageView()
    videos.getChildren.add(newImage)
    images = images.appended(newImage)
  }

  def removeOne(): Unit = {
    if (images.nonEmpty) {
      images = images.init
      videos.getChildren.remove(videos.getChildren.size - 1)
    }
  }

  val kek =
    stream
      .foreach { image =>
        ZIO {
          val fxImage = SwingFXUtils.toFXImage(image, null)
          images.foreach(_.setImage(fxImage))
        }
      }
}
