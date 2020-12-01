import java.util.concurrent.TimeUnit

import javax.sound.sampled.{AudioFormat, AudioSystem}
import media._
import web.{MediaClient, MediaServer}
import zio._
import zio.clock._
import zio.console._

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    udpVideoStream.orDie

  val port = 8282
  val videoBufferSize = 1024 * 64
  val audioFormat = new AudioFormat(16000.0f, 16, 1, true, true)
  val audioBufferSize = 8

  def udpVideoStream =
    Webcam
      .managed()
      .use(
        cam =>
          MediaServer
            .accept(port, videoBufferSize)
            .map(ImageCodec.decode)
            .tap(i => UIO(println(i)))
            .runDrain
            .forkDaemon *>
            cam.stream
              .mapConcatChunk(ImageCodec.encode(_, 42))
              .flattenChunks
              .run(MediaClient.mediaSink("localhost", port))
      )
      .as(ExitCode.success)

  def udpPlaybackStream =
    Microphone
      .managed(_.toList.drop(1).head, audioFormat)
      .zip(Playback.managed(_.last, audioFormat))
      .use {
        case (mic, play) =>
          MediaServer
            .accept(port, audioBufferSize)
            .flattenChunks
            .run(play.sink)
            .forkDaemon *>
            mic
              .stream(audioBufferSize)
              .run(MediaClient.mediaSink("localhost", port))
      }
      .as(ExitCode.success)
}
