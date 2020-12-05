package media

import java.awt.image.{BufferedImage, Raster}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import javax.imageio.{IIOImage, ImageIO, ImageWriteParam}
import zio.Chunk

import scala.util.Try

trait MediaCodec[T] {
  def toBytes(t: T): Chunk[Byte]
  def fromBytes(bytes: Chunk[Byte]): Either[Throwable, T]
}

object MediaCodec {
  implicit val audioCodec: MediaCodec[AudioSegment] =
    new MediaCodec[AudioSegment] {
      def toBytes(t: AudioSegment): Chunk[Byte] =
        t.header.toBytes ++ t.audio

      def fromBytes(bytes: Chunk[Byte]): Either[Throwable, AudioSegment] =
        Try {
          AudioSegment(
            AudioHeader.fromBytes(bytes.take(AudioHeader.size)),
            bytes.drop(AudioHeader.size)
          )
        }.toEither
    }

  implicit val imageCodec: MediaCodec[ImageSegment] =
    new MediaCodec[ImageSegment] {
      private val quality: Float = 0.3f
      def toBytes(t: ImageSegment): Chunk[Byte] = t.toBytes(quality)

      def fromBytes(bytes: Chunk[Byte]): Either[Throwable, ImageSegment] =
        Try {
          val bais =
            new ByteArrayInputStream(bytes.drop(ImageHeader.size).toArray)
          val image = ImageIO.read(bais)
          ImageSegment(
            ImageHeader.fromBytes(bytes.take(ImageHeader.size)),
            image
          )
        }.toEither
    }
}

case class AudioSegment(header: AudioHeader, audio: Chunk[Byte])
case class AudioHeader(roomId: Byte, userId: Byte) {
  def toBytes: Chunk[Byte] = Chunk(roomId, userId)
}
object AudioHeader {
  val size = 2
  def fromBytes(bytes: Chunk[Byte]) = AudioHeader(bytes(0), bytes(1))
}

case class ImageHeader(roomId: Byte, userId: Byte, x: Byte, y: Byte) {
  def toBytes: Chunk[Byte] = Chunk(roomId, userId, x, y)
}
object ImageHeader {
  val size = 4
  def fromBytes(bytes: Chunk[Byte]) =
    ImageHeader(bytes(0), bytes(1), bytes(2), bytes(3))
}

case class ImageSegment(header: ImageHeader, image: BufferedImage) {
  def toBytes(quality: Float): Chunk[Byte] =
    header.toBytes ++ compress(image, quality)
  def toRaster: Raster = {
    val width = image.getWidth
    val height = image.getHeight
    image.getData.createTranslatedChild(width * header.x, height * header.y)
  }

  private def compress(image: BufferedImage, quality: Float): Chunk[Byte] = {
    val jpgWriter = ImageIO.getImageWritersByFormatName("JPEG").next
    val jpgParams = jpgWriter.getDefaultWriteParam
    jpgParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
    jpgParams.setCompressionQuality(quality)
    val baos = new ByteArrayOutputStream()
    jpgWriter.setOutput(ImageIO.createImageOutputStream(baos))
    val outputImage = new IIOImage(image, null, null)
    jpgWriter.write(null, outputImage, jpgParams)
    Chunk.fromArray(baos.toByteArray)
  }
}

object ImageSegment {
  def fromImage(image: BufferedImage,
                roomId: Byte,
                userId: Byte,
                nTiles: Byte = 3): Chunk[ImageSegment] = {
    val tileW = image.getWidth / nTiles;
    val tileH = image.getHeight / nTiles;
    Chunk.fromIterable(
      for (i <- 0 until nTiles; j <- 0 until nTiles)
        yield
          ImageSegment(
            ImageHeader(roomId, userId, i.toByte, j.toByte),
            image.getSubimage(i * tileW, j * tileH, tileW, tileH)
          )
    )
  }
}
