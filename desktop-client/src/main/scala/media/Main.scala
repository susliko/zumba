package media
import cats.Show
import web.MediaClient
import zio._
import zio.console._
import zio.stream.ZSink
import cats.implicits._

object Main extends zio.App {

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    (for {
      cfg <- ZumbaConfig.withProps
      _ <- UIO(println(s"Running with ${cfg.show.split(",").mkString("\n  ")}"))
      micros <- Microphone.names(cfg.audioFormat)
      playbacks <- Playback.names(cfg.audioFormat)
      _ <- putStrLn(s"Available audio inputs:\n${micros.mkString("\n")}")
      _ <- putStrLn("")
      _ <- putStrLn(s"Available audio outputs:\n${playbacks.mkString("\n")}")
      _ <- putStrLn("")
      _ <- udpVideoStream(cfg)
    } yield ExitCode.success).orDie

  def udpVideoStream(cfg: ZumbaConfig) =
    (Webcam
      .managed() zip MediaClient.managed[ImageSegment](cfg.localVideoPort))
      .use {
        case (webcam, client) =>
          client
            .acceptStream(cfg.videoBufSize)
            .tap(i => UIO(println(i)))
            .map(_.toRaster)
            .runDrain
            .forkDaemon *>
            webcam.stream
              .mapConcatChunk(ImageSegment.fromImage(_, cfg.roomId, cfg.userId))
              .tap(s => UIO(println(s.toRaster.getBounds)))
              .run(client.sendSink(cfg.rumbaHost, cfg.rumbaVideoPort))
      }
      .as(ExitCode.success)

  def udpPlaybackStream(cfg: ZumbaConfig) =
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
              .acceptStream(cfg.audioBufSize * 8)
              .tap(
                c =>
                  UIO(println(s"Got ${c.header} of size ${c.audio.size}"))
                    .when(cfg.logPackets)
              )
              .mapConcatChunk(_.audio)
              .run(play.sink)
              .forkDaemon *>
            mic
              .stream(cfg.audioBufSize)
              .mapChunks(
                chunk =>
                  Chunk(
                    AudioSegment(AudioHeader(cfg.roomId, cfg.userId), chunk)
                )
              )
              .tap(c => UIO(println(s"Sending $c")).when(cfg.logPackets))
              .run(client.sendSink(cfg.rumbaHost, cfg.rumbaAudioPort))
      }
      .as(ExitCode.success)
}
