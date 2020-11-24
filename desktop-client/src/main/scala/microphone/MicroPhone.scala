package microphone
import javax.sound.sampled._
import zio._
import zio.blocking.Blocking
import zio.stream._

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
  def managed: TaskManaged[Microphone] =
    ZManaged.make(Task {
      val format = new AudioFormat(8000.0f, 16, 1, true, true)
      val line = AudioSystem.getTargetDataLine(format)
      line.open()
      new Microphone(line)
    })(_.close)
}
