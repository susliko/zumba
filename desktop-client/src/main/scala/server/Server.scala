package server
import io.circe.generic.JsonCodec
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.Encoder
import zio._
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend

case class Server(backend: HttpClientZioBackend, url: String) {
  def createRoom(creator: String): Task[Room] =
    makeReq(CreateRoom(creator), "/room/create")
  def joinRoom(user: String, room_id: String): Task[Room] =
    makeReq(JoinRoom(user, room_id), "/room/join")
  def leave(user: String, room_id: String): Task[Unit] =
    makeReq(LeaveRoom(user, room_id), "/room/leave").unit

  private def makeReq[R: Encoder](body: R, path: String): Task[Room] =
    backend
      .send(
        basicRequest
          .post(uri"$url$path")
          .body(body.asJson.noSpaces)
          .mapResponse(
            _.fold(
              e => throw new RuntimeException(e),
              decode[Room](_).fold(e => throw e, identity)
            )
          )
      )
      .map(_.body)
}

@JsonCodec
case class CreateRoom(creator: String)

@JsonCodec
case class JoinRoom(user: String, room_id: String)

@JsonCodec
case class LeaveRoom(user: String, room_id: String)

@JsonCodec
case class Room(id: String,
                users: List[String],
                creator: String,
                worker_id: String)
