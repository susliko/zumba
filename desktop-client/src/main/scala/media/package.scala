import javax.sound.sampled.AudioFormat

package object media {
  val defaultAudioFormat = new AudioFormat(8000.0f, 16, 1, true, true)
}
