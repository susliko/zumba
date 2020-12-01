package web
import zio._
import zio.stream._
import zio.nio.channels.DatagramChannel
import zio.nio.core.{Buffer, SocketAddress}

object MediaClient {
  def mediaSink(hostname: String,
                port: Int): Sink[Throwable, Byte, Byte, Unit] =
    Sink.managed(
      Managed
        .fromEffect(
          SocketAddress
            .inetSocketAddress(hostname, port)
        )
        .flatMap(addr => DatagramChannel.connect(addr).map((addr, _)))
    ) {
      case (addr, channel) =>
        Sink.foldLeftChunksM(()) {
          case (_, chunk) =>
            Buffer
              .byte(chunk)
              .flatMap(
                buffer =>
                  channel
                    .send(buffer, addr)
                    .unit
              )
        }
    }
}
