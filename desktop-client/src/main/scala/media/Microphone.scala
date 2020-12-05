package media

import cats.data.NonEmptyList
import javax.sound.sampled._
import zio._
import zio.blocking.Blocking
import zio.stream._

import scala.util.Try

class Microphone(line: TargetDataLine) {
  def stream(chunkSize: Int = 128): ZStream[Blocking, Throwable, Byte] =
    Stream.fromEffect(Task(line.start())) *> Stream.fromInputStream(
      new AudioInputStream(line),
      chunkSize
    )

  def format: UIO[AudioFormat] = UIO(line.getFormat)

  private def close: UIO[Unit] = UIO(line.close)
}

object Microphone {
  def names(audioFormat: AudioFormat = defaultAudioFormat): Task[List[String]] =
    Task {
      AudioSystem.getMixerInfo.toList
        .filter(
          info =>
            Try(AudioSystem.getTargetDataLine(audioFormat, info)).isSuccess
        )
        .map(_.getDescription)
    }

  def managed(
    choose: NonEmptyList[Mixer.Info] => Mixer.Info = _.head,
    format: AudioFormat = defaultAudioFormat
  ): TaskManaged[Microphone] =
    ZManaged.make(Task {
      val mixerInfos = NonEmptyList
        .fromList(
          AudioSystem.getMixerInfo.toList.filter(
            info => Try(AudioSystem.getTargetDataLine(format, info)).isSuccess
          )
        )
        .getOrElse(
          throw new RuntimeException("No available sound input devices!")
        )
      val mixerInfo = choose(mixerInfos)
      val line = AudioSystem.getTargetDataLine(format, mixerInfo)
      line.open()
      println(s"Using microphone ${mixerInfo.getDescription}")
      new Microphone(line)
    })(_.close)

  def managedByName(
    name: String,
    format: AudioFormat = defaultAudioFormat
  ): TaskManaged[Microphone] =
    managed(_.find(_.getDescription == name).get)
}
