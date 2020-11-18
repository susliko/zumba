import javax.imageio.ImageIO
import java.io.File
import java.util.concurrent.TimeUnit

import com.github.sarxos.webcam.{Webcam => JWebcam}
import webcam.Webcam
import zio._
import zio.console._
import zio.clock._
import zio.stream.ZStream

object Main extends zio.App {
//  ImageIO.write(webcam.getImage, "PNG", new File("hello-world.png"))

  def run(args: List[String]): URIO[ZEnv, ExitCode] = zstream

  def rawStream =
    ZIO {
      val camera = JWebcam.getDefault
      camera.open()
      JWebcam.getDriver
      var start = System.currentTimeMillis()
      var i = 0
      while (i < 600) {
        i += 1
        println(camera.getFPS)
        camera.getImage
      }
      var end = System.currentTimeMillis()
      println(end - start)
    }.ignore.as(ExitCode.success)

  def zstream =
    for {
      start <- currentTime(TimeUnit.MILLISECONDS)
      s <- Webcam.managed
        .use(
          x =>
            (x.stream zip x.stream)
              .take(300)
              .runDrain
        )
        .ignore
      end <- currentTime(TimeUnit.MILLISECONDS)
      _ <- putStrLn((end - start).toString)
    } yield ExitCode.success
}
