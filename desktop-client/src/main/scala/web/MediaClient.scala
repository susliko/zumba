package web
import media.MediaCodec
import zio._
import zio.stream._
import zio.nio.channels.DatagramChannel
import zio.nio.core.{Buffer, SocketAddress}

case class MediaClient[T](channel: DatagramChannel)(
  implicit codec: MediaCodec[T]
) {
  def sendSink(hostname: String, port: Int): Sink[Throwable, T, T, Unit] =
    Sink
      .fromEffect(SocketAddress.inetSocketAddress(hostname, port))
      .flatMap(
        addr =>
          Sink.foreach(
            el =>
              Buffer
                .byte(codec.toBytes(el))
                .flatMap(
                  buffer =>
                    channel
                      .send(buffer, addr)
                      .unit
              )
        )
      )

  def acceptStream(capacity: Int): Stream[Throwable, T] =
    Stream
      .fromEffect(Buffer.byte(capacity))
      .flatMap(
        buffer =>
          Stream
            .fromEffect(
              channel.receive(buffer) *> buffer.flip *> buffer.getChunk()
          )
      )
      .forever
      .mapConcatM(
        chunk =>
          codec
            .fromBytes(chunk)
            .fold(
              e => UIO(println("Decoding error $e")).as(List.empty[T]),
              r => UIO(List(r))
          )
      )
}

object MediaClient {
  def managed[T: MediaCodec](localPort: Int): TaskManaged[MediaClient[T]] =
    Managed
      .fromEffect(SocketAddress.inetSocketAddress(localPort))
      .flatMap(addr => DatagramChannel.bind(Some(addr)))
      .tap(
        _ => UIO(println(s"Listening for UDP traffic at $localPort")).toManaged_
      )
      .map(MediaClient.apply[T])

}
