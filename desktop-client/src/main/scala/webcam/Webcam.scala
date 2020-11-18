package webcam
import java.awt.image.BufferedImage

import zio.stream.{Stream, ZStream}
import zio.stream.ZStream.Pull
import zio._
import com.github.sarxos.webcam.{Webcam => JWebcam}
import zio.clock.Clock

class Webcam(webcam: JWebcam) {
  def stream: ZStream[Clock, Throwable, BufferedImage] =
    Stream.fromIterator(Iterator.continually({
      println(webcam.getFPS)
      webcam.getImage
    }))

  private def close: UIO[Unit] = UIO(webcam.close())
}

object Webcam {
  def managed: TaskManaged[Webcam] =
    ZManaged.make(Task {
      val webcam = JWebcam.getDefault()
      webcam.open()
      new Webcam(webcam)
    })(_.close)
}
