package microphone
import cats.data.NonEmptyList
import javax.sound.sampled._
import zio._
import zio.blocking.Blocking
import zio.stream._

import scala.util.Try

class Microphone(line: TargetDataLine) {
  def stream(chunkSize: Int = 128): ZStream[Blocking, Throwable, Byte] =
    Stream.fromEffect(Task(line.start())) *> Stream.fromInputStream({
      val s = new AudioInputStream(line)
      println(s.getFormat)
      s
    }, chunkSize)

  def format: UIO[AudioFormat] = UIO(line.getFormat)

  private def close: UIO[Unit] = UIO(line.close)
}

object Microphone {
  val format = new AudioFormat(8000.0f, 16, 1, true, true)

  def names: Task[List[String]] = Task {
    AudioSystem.getMixerInfo.toList
      .filter(
        info => Try(AudioSystem.getTargetDataLine(format, info)).isSuccess
      )
      .map(_.getDescription)
  }

  def managed(
    choose: NonEmptyList[Mixer.Info] => Mixer.Info = _.head
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
      new Microphone(line)
    })(_.close)

  def managedByName(name: String): TaskManaged[Microphone] =
    managed(_.find(_.getDescription == name).get)
}
