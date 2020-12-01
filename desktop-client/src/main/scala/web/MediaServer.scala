package web
import zio._
import zio.stream._
import zio.nio.channels.DatagramChannel
import zio.nio.core.{SocketAddress, Buffer}

object MediaServer {
  // one chunk = content of one received DatagramPacket. If specified "capacity" of the chunk is lower than
  // actual size of data in packet, part of data will be silently discarded
  def accept(port: Int, capacity: Int): Stream[Throwable, Chunk[Byte]] =
    Stream
      .managed(
        Managed
          .fromEffect(SocketAddress.inetSocketAddress(port))
          .flatMap(addr => DatagramChannel.bind(Some(addr)))
          .tap(
            _.localAddress
              .tap(
                addr =>
                  UIO(println(s"Listening for UDP traffic at ${addr.get}"))
              )
              .toManaged_
          )
      )
      .flatMap(
        channel =>
          Stream
            .fromEffect(Buffer.byte(capacity))
            .flatMap(
              buffer =>
                Stream
                  .fromEffect(
                    channel.receive(buffer) *> buffer.rewind *> buffer
                      .getChunk()
                )
            )
            .forever
      )
}
