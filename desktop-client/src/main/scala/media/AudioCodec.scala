package media

import zio.Chunk

case class AudioHeader(roomId: Byte, userId: Byte) {
  def toBytes: Chunk[Byte] = Chunk(roomId, userId)
}

object AudioHeader {
  val size = 2
  def fromBytes(bytes: Chunk[Byte]) = AudioHeader(bytes(0), bytes(1))
}

case class AudioSegment(header: AudioHeader, audio: Chunk[Byte])

object AudioCodec {
  def encode(bytes: Chunk[Byte], roomId: Byte, userId: Byte): Chunk[Byte] =
    Chunk(roomId, userId) ++ bytes
  def decode(bytes: Chunk[Byte]): AudioSegment =
    AudioSegment(
      AudioHeader.fromBytes(bytes.take(AudioHeader.size)),
      bytes.drop(AudioHeader.size)
    )
}
