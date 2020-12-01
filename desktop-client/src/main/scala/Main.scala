import javax.sound.sampled.AudioFormat
import media._
import web.{MediaClient, MediaServer}
import zio._

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    udpPlaybackStream.orDie

  val port = 8282
  val videoBufferSize = 1024 * 64
  val audioFormat = new AudioFormat(16000.0f, 16, 1, true, true)
  val audioChunkSize = 8
  val audioBufferSize = 16

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
              .mapConcatChunk(ImageCodec.encode(_, 21, 42))
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
            .map(AudioCodec.decode)
            .tap(c => UIO(println(c)))
            .mapConcatChunk(_.audio)
            .run(play.sink)
            .forkDaemon *>
            mic
              .stream(audioChunkSize)
              .mapChunks(AudioCodec.encode(_, 21, 42))
              .run(MediaClient.mediaSink("localhost", port))
      }
      .as(ExitCode.success)
}
