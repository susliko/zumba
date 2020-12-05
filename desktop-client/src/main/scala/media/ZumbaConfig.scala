package media

import java.nio.file.{Files, Paths}

import cats.Show
import derevo.cats.show
import derevo.derive
import javax.sound.sampled.AudioFormat
import zio._

import scala.jdk.CollectionConverters._
import scala.util.Try

@derive(show)
case class ZumbaConfig(userId: Byte,
                       roomId: Byte,
                       supervisorUrl: String,
                       rumbaHost: String,
                       rumbaVideoPort: Int,
                       rumbaAudioPort: Int,
                       localVideoPort: Int,
                       localAudioPort: Int,
                       audioFormat: AudioFormat,
                       videoBufSize: Int,
                       audioBufSize: Int,
                       logPackets: Boolean)

object ZumbaConfig {
  implicit val audioFormatShow: Show[AudioFormat] = f =>
    s"AudioFormat(rate=${f.getFrameRate} frameSize=${f.getSampleSizeInBits})"

  private def findVar[T](vars: List[(String, String)],
                         name: String,
                         f: String => T,
                         default: T): T =
    vars
      .collectFirst { case (n, value) if n == name => Try(f(value)).toOption }
      .flatten
      .getOrElse(default)

  def withProps: Task[ZumbaConfig] = Task {
    val vars = Try {
      val cfgPath = System.getProperty("config")
      val strings = Files.readAllLines(Paths.get(cfgPath)).asScala.toList
      println(s"Read variables $strings")
      strings.map(line => {
        val l = line.split("=").take(2)
        (l(0), l(1))
      })
    }.fold(e => {
      println("Using default config"); List.empty[(String, String)]
    }, identity)
    val supervisorUrl =
      findVar(vars, "supervisorUrl", identity, "http://zumba.salamantos.me/")
    val userId = findVar(vars, "userId", _.toByte, 1.toByte)
    val roomId = findVar(vars, "roomId", _.toByte, 1.toByte)
    val rumbaHost = findVar(vars, "rumbaHost", identity, "143.110.168.156")
    val rumbaAudioPort = findVar(vars, "rumbaAudioPort", _.toInt, 5001)
    val rumbaVideoPort = findVar(vars, "rumbaVideoPort", _.toInt, 5002)
    val audioBufSize = findVar(vars, "audioBufSize", _.toInt, 1024)
    val sampleRate = findVar(vars, "sampleRate", _.toFloat, 16000f)
    val logPackets = findVar(vars, "logPackets", _.toBoolean, true)

    ZumbaConfig(
      userId,
      roomId,
      supervisorUrl,
      rumbaHost,
      rumbaVideoPort,
      rumbaAudioPort,
      localVideoPort = 4002,
      localAudioPort = 4001,
      audioFormat = new AudioFormat(sampleRate, 16, 1, true, true),
      videoBufSize = 1024 * 64,
      audioBufSize,
      logPackets
    )
  }
}
