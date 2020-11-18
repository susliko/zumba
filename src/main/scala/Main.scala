import cats.effect.{ExitCode, IO, IOApp}
import com.github.sarxos.webcam.Webcam
import javax.imageio.ImageIO
import java.io.File
import tofu.syntax.monadic._
import scala.jdk.CollectionConverters._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    IO {
      val webcam: Webcam = Webcam.getDefault()
      println(Webcam.getWebcams.asScala.toList.map(cam => cam.getName))
      webcam.open
      webcam.close()
      ImageIO.write(webcam.getImage, "PNG", new File("hello-world.png"))
    }.as(ExitCode.Success)
}
