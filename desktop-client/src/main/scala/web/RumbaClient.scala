package web
import io.circe.generic.JsonCodec
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import zio._
import sttp.client3._
import sttp.client3.httpclient.zio.HttpClientZioBackend

@JsonCodec
case class Room(users: List[User] = Nil,
                worker_host: String,
                worker_video_port: Int,
                worker_audio_port: Int,
)

@JsonCodec
case class User(user_id: Byte, name: String)

case class RumbaClient(backend: HttpClientZioBackend, url: String) {
  type UserId = Byte
  type RoomId = Byte
  def createUser(name: String): Task[UserId] =
    makeReq[CreateUser, User](CreateUser(name), "/user/create").map(_.user_id)
  def removeUser(userId: UserId): Task[Unit] =
    makeReq[RemoveUser, Unit](RemoveUser(userId), "/user/remove")
  def createRoom(userId: UserId): Task[Room] =
    makeReq[CreateRoom, Room](CreateRoom(userId), "/room/create")
  def joinRoom(user_id: UserId, room_id: RoomId): Task[Room] =
    makeReq[JoinRoom, Room](JoinRoom(user_id, room_id), "/room/join")
  def leaveRoom(user_id: UserId, room_id: RoomId): Task[Unit] =
    makeReq[LeaveRoom, Unit](LeaveRoom(user_id, room_id), "/room/leave").unit

  private def makeReq[R: Encoder, D: Decoder](body: R, path: String): Task[D] =
    backend
      .send(
        basicRequest
          .post(uri"$url$path")
          .body(body.asJson.noSpaces)
          .mapResponse(
            _.fold(
              e => throw new RuntimeException(e),
              decode[D](_).fold(e => throw e, identity)
            )
          )
      )
      .map(_.body)
}

@JsonCodec
case class CreateUser(name: String)
@JsonCodec
case class RemoveUser(userId: Byte)

@JsonCodec
case class CreateRoom(user_id: Byte)

@JsonCodec
case class JoinRoom(user_id: Byte, room_id: Byte)

@JsonCodec
case class LeaveRoom(user_id: Byte, room_id: Byte)
