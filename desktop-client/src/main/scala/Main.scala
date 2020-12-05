import javax.sound.sampled.AudioFormat
import media._
import web.MediaClient
import zio._

case class ZumbaConfig(supervisorUrl: String,
                       rumbaHost: String,
                       rumbaVideoPort: Int,
                       rumbaAudioPort: Int,
                       localVideoPort: Int,
                       localAudioPort: Int,
                       audioFormat: AudioFormat,
                       videoBufSize: Int,
                       audioBufSize: Int)

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    udpVideoStream.orDie

  val cfg = ZumbaConfig(
    supervisorUrl = "http://zumba.salamantos.me/",
    rumbaHost = "143.110.168.156",
    rumbaVideoPort = 5002,
    rumbaAudioPort = 5001,
    localVideoPort = 4002,
    localAudioPort = 4001,
    audioFormat = new AudioFormat(16000.0f, 16, 1, true, true),
    videoBufSize = 1024 * 64,
    audioBufSize = 8096
  )

  def udpVideoStream =
    (Webcam
      .managed() zip MediaClient.managed[ImageSegment](cfg.localVideoPort))
      .use {
        case (webcam, client) =>
          client.ack(
            cfg.rumbaHost,
            cfg.rumbaVideoPort,
            ImageHeader(21, 107, 0, 0).toBytes
          ) *>
            client
              .acceptStream(cfg.videoBufSize)
              .map(_.toRaster)
              .tap(i => UIO(println(i)))
              .runDrain
              .forkDaemon *>
            webcam.stream
              .mapConcatChunk(ImageSegment.fromImage(_, 21, 107))
              .run(client.sendSink("localhost", cfg.rumbaVideoPort))
      }
      .as(ExitCode.success)

  def udpPlaybackStream =
    Microphone
      .managed(_.toList.drop(1).head, cfg.audioFormat)
      .zip(Playback.managed(_.last, cfg.audioFormat))
      .zip(MediaClient.managed[AudioSegment](cfg.localAudioPort))
      .use {
        case ((mic, play), client) =>
          client.ack(
            cfg.rumbaHost,
            cfg.rumbaAudioPort,
            AudioHeader(1, 1).toBytes
          ) *>
            client
              .acceptStream(cfg.audioBufSize)
              .tap(c => UIO(println(c)))
              .mapConcatChunk(_.audio)
              .run(play.sink)
              .forkDaemon *>
            mic
              .stream(cfg.audioBufSize)
              .mapChunks(chunk => Chunk(AudioSegment(AudioHeader(1, 1), chunk)))
              .run(client.sendSink(cfg.rumbaHost, cfg.rumbaAudioPort))
      }
      .as(ExitCode.success)
}
