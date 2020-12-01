import java.util.concurrent.TimeUnit

import javax.sound.sampled.{AudioFormat, AudioSystem}
import media._
import web.{MediaClient, MediaServer}
import zio._
import zio.clock._
import zio.console._

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    udpPlaybackStream.orDie

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
        case (mic, play) => mic.stream(128).run(play.sink)
      }
      .as(ExitCode.success)

  val port = 8282
  val chunkSize = 8
  def udpPlaybackStream =
    Microphone
      .managed(_.toList.drop(1).head, audioFormat)
      .zip(Playback.managed(_.last, audioFormat))
      .use {
        case (mic, play) =>
          MediaServer
            .accept(port, chunkSize)
            .flattenChunks
            .run(play.sink)
            .forkDaemon *>
            mic
              .stream(chunkSize)
              .run(MediaClient.mediaSink("localhost", port))
      }
      .as(ExitCode.success)
}
