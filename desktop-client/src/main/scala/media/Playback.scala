package media

import cats.data.NonEmptyList
import javax.sound.sampled._
import zio.stream._
import zio._

import scala.util.Try

class Playback(line: SourceDataLine) {
  def sink: Sink[Throwable, Byte, Byte, Unit] =
    Sink.foldChunksM[Any, Throwable, Byte, Unit](())(_ => true) {
      case (_, chunk) =>
        Task(line.write(chunk.toArray, 0, chunk.length)).unit
    }
  private def close: UIO[Unit] = UIO(line.close())

}

object Playback {
  def names: Task[List[String]] =
    Task {
      AudioSystem.getMixerInfo.toList
        .filter { info =>
          Try(AudioSystem.getSourceDataLine(defaultAudioFormat, info)).isSuccess
        }
        .map(_.getDescription)
    }

  def managed(choose: NonEmptyList[Mixer.Info] => Mixer.Info = _.head,
              format: AudioFormat = defaultAudioFormat): TaskManaged[Playback] =
    ZManaged
      .make(Task {
        val mixerInfos = NonEmptyList
          .fromList(
            AudioSystem.getMixerInfo.toList.filter(
              info => Try(AudioSystem.getTargetDataLine(format, info)).isSuccess
            )
          )
          .getOrElse(
            throw new RuntimeException("No available sound output devices!")
          )
        val mixerInfo = choose(mixerInfos)
        val line = AudioSystem.getSourceDataLine(format, mixerInfo)
        println(s"Using playback ${mixerInfo.getDescription}")
        line.open()
        line.start()
        new Playback(line)
      })(_.close)

  def managedByName(
    name: String,
    format: AudioFormat = defaultAudioFormat
  ): TaskManaged[Playback] =
    managed(_.find(_.getDescription == name).get, format)
}
