package webcam
import java.awt.image.BufferedImage

import cats.data.NonEmptyList
import zio.stream.{Stream, ZStream}
import zio._
import com.github.sarxos.webcam.{Webcam => JWebcam}
import scala.jdk.CollectionConverters._

class Webcam(webcam: JWebcam) {
  def stream: Stream[Throwable, BufferedImage] =
    Stream.fromIterator(Iterator.continually(webcam.getImage))

  def fps: UIO[Double] = UIO(webcam.getFPS)

  private def close: UIO[Unit] = UIO(webcam.close())
}

object Webcam {
  def names: Task[List[String]] = Task {
    JWebcam.getWebcams.asScala.toList.map(_.getName)
  }

  def managed(
    choose: NonEmptyList[JWebcam] => JWebcam = _.head
  ): TaskManaged[Webcam] =
    ZManaged.make(Task {
      val cameras = NonEmptyList
        .fromList(JWebcam.getWebcams().asScala.toList)
        .getOrElse(throw new RuntimeException("No cameras available!"))
      val webcam = choose(cameras)
      webcam.open()
      new Webcam(webcam)
    })(_.close)

  def managedByName(name: String): TaskManaged[Webcam] =
    managed(_.find(_.getName == name).get)
}
