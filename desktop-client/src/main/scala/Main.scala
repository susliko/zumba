import java.util.concurrent.TimeUnit

import javax.sound.sampled.{AudioFormat, AudioSystem}
import media._
import zio._
import zio.clock._
import zio.console._

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    playbackStream.orDie

  def videoStream =
    for {
      start <- currentTime(TimeUnit.MILLISECONDS)
      s <- Webcam
        .managed()
        .use(
          x =>
            (x.stream zip x.stream)
              .take(10)
              .runDrain
        )
      end <- currentTime(TimeUnit.MILLISECONDS)
      _ <- putStrLn((end - start).toString)
    } yield ExitCode.success

  val audioFormat = new AudioFormat(16000.0f, 16, 1, true, true)
  def playbackStream =
    Microphone
      .managed(_.toList.drop(1).head, audioFormat)
      .zip(Playback.managed(_.last, audioFormat))
      .use {
        case (mic, play) => mic.stream(1024).run(play.sink)
      }
      .as(ExitCode.success)
}
