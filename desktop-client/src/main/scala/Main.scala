import javax.imageio.ImageIO
import java.io.File
import java.net.{DatagramPacket, DatagramSocket}
import java.util.concurrent.TimeUnit

import com.github.sarxos.webcam.{Webcam => JWebcam}
import javax.sound.sampled.AudioSystem
import microphone.Microphone
import webcam.Webcam
import zio._
import zio.console._
import zio.clock._
import zio.stream.ZStream

object Main extends zio.App {
  def run(args: List[String]): URIO[ZEnv, ExitCode] = audioStream.orDie

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

  def audioStream =
    for {
      start <- currentTime(TimeUnit.MILLISECONDS)
      names <- Microphone.names
      _ <- putStrLn(names.toString)
      s <- Microphone
        .managed()
        .use(x => x.stream().runDrain)
      end <- currentTime(TimeUnit.MILLISECONDS)
      _ <- putStrLn((end - start).toString)
    } yield ExitCode.success

  def getMixers = UIO {
    val infos = AudioSystem.getMixerInfo.toList
    infos
      .filter(info => {
        val mixer = AudioSystem.getMixer(info)
        val tg = mixer.getTargetLineInfo
        if (tg.nonEmpty) println(tg.toList)
        tg.toList.nonEmpty
      })
      .map(info => {
        println(info.getName)
        println(info.getDescription)
      })
  }
}
