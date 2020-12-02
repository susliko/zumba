package media
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import javax.imageio.{IIOImage, ImageIO, ImageWriteParam}
import zio.Chunk

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

object ImageCodec {
  def encode(image: BufferedImage,
             roomId: Byte,
             userId: Byte,
             nTiles: Byte = 3,
             quality: Float = 0.3f): Chunk[Chunk[Byte]] = {
    val tileW = image.getWidth / nTiles;
    val tileH = image.getHeight / nTiles;
    Chunk.fromIterable(
      for (i <- 0 until nTiles; j <- 0 until nTiles)
        yield
          ImageSegment(
            ImageHeader(roomId, userId, i.toByte, j.toByte),
            image.getSubimage(i * tileW, j * tileH, tileW, tileH)
          ).toBytes(quality)
    )
  }

  def decode(bytes: Chunk[Byte]): ImageSegment = {
    val bais = new ByteArrayInputStream(bytes.drop(ImageHeader.size).toArray)
    val image = ImageIO.read(bais)
    ImageSegment(ImageHeader.fromBytes(bytes.take(ImageHeader.size)), image)
  }
}
