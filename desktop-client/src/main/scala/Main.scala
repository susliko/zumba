import javax.sound.sampled.AudioFormat
import media._
import web.MediaClient
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
    (Webcam
      .managed() zip MediaClient.managed[ImageSegment](port))
      .use {
        case (webcam, client) =>
          client
            .acceptStream(videoBufferSize)
            .tap(i => UIO(println(i)))
            .runDrain
            .forkDaemon *>
            webcam.stream
              .mapConcatChunk(ImageSegment.fromImage(_, 21, 42))
              .run(client.sendSink("localhost", port))
      }
      .as(ExitCode.success)

  def udpPlaybackStream =
    Microphone
      .managed(_.toList.drop(1).head, audioFormat)
      .zip(Playback.managed(_.last, audioFormat))
      .zip(MediaClient.managed[AudioSegment](port))
      .use {
        case ((mic, play), client) =>
          client
            .acceptStream(audioBufferSize)
            .tap(c => UIO(println(c)))
            .mapConcatChunk(_.audio)
            .run(play.sink)
            .forkDaemon *>
            mic
              .stream(audioChunkSize)
              .mapChunks(chunk => Chunk(AudioSegment(AudioHeader(1, 2), chunk)))
              .run(client.sendSink("localhost", port))
      }
      .as(ExitCode.success)
}
