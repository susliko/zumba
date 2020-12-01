package media
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.net.URL
import java.nio.ByteBuffer
import zio.Chunk
import javax.imageio.{IIOImage, ImageIO, ImageWriteParam}

case class ImageHeader(userId: Int, x: Byte, y: Byte) {
  def toBytes: Chunk[Byte] =
    Chunk(0.toByte) ++
      ImageHeader.intToBytes(userId) ++
      Chunk(x, y)
}

object ImageHeader {
  def fromBytes(bytes: Chunk[Byte]) =
    ImageHeader(bytesToInt(bytes.slice(1, 5)), bytes(5), bytes(6))

  private def bytesToInt(bytes: Chunk[Byte]): Int = {
    ByteBuffer.wrap(bytes.toArray).getInt
  }
  private def intToBytes(x: Int): Chunk[Byte] =
    Chunk(
      ((x >> 24) & 0xff).toByte,
      ((x >> 16) & 0xff).toByte,
      ((x >> 8) & 0xff).toByte,
      ((x >> 0) & 0xff).toByte
    )
}

case class ImageSegment(header: ImageHeader, image: BufferedImage) {
  def toBytes(quality: Float): Chunk[Byte] =
    header.toBytes ++ compress(image, quality)

  private def compress(image: BufferedImage, quality: Float): Chunk[Byte] = {
    val jpgWriter = ImageIO.getImageWritersByFormatName("JPEG").next
    val jpgWriteParam = jpgWriter.getDefaultWriteParam
    jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
    jpgWriteParam.setCompressionQuality(quality)
    val baos = new ByteArrayOutputStream()
    jpgWriter.setOutput(ImageIO.createImageOutputStream(baos))
    val outputImage = new IIOImage(image, null, null)
    jpgWriter.write(null, outputImage, jpgWriteParam)
    Chunk.fromArray(baos.toByteArray)
  }
}

object ImageCodec {
  def encode(image: BufferedImage,
             userId: Int,
             nTiles: Byte = 3,
             quality: Float = 0.3f): Chunk[Chunk[Byte]] = {
    val tileW = image.getWidth / nTiles;
    val tileH = image.getHeight / nTiles;
    Chunk.fromIterable(
      for (i <- 0 until nTiles; j <- 0 until nTiles)
        yield
          ImageSegment(
            ImageHeader(userId, i.toByte, j.toByte),
            image.getSubimage(i * tileW, j * tileH, tileW, tileH)
          ).toBytes(quality)
    )
  }

  def decode(bytes: Chunk[Byte]): ImageSegment = {
    val bais = new ByteArrayInputStream(bytes.drop(7).toArray)
    val image = ImageIO.read(bais)
    ImageSegment(ImageHeader.fromBytes(bytes.take(7)), image)
  }
}
