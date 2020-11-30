package web
import zio._
import zio.stream._
import zio.nio.channels.DatagramChannel
import zio.nio.core.SocketAddress

object MediaServer {
  // one chunk = content of one received DatagramPacket. If specified "capacity" of the chunk is lower than
  // actual size of data in packet, part of data will be silently discarded
  def accept(port: Int, capacity: Int): Stream[Throwable, Chunk[Byte]] =
    Stream
      .managed(
        Managed
          .fromEffect(SocketAddress.inetSocketAddress(port))
          .flatMap(addr => DatagramChannel.bind(Some(addr)))
      )
      .flatMap(
        channel => Stream.fromEffect(channel.readChunk(capacity)).forever
      )
}
