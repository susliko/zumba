package web

import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio._

class UByte(val intValue: Int) extends AnyVal {
  def toByte: Byte = intValue.toByte

  override def toString: String = intValue.toString
}

object UByte {
  implicit val encoder: Encoder[UByte] =
    Encoder.encodeInt.contramap[UByte](_.intValue)
  implicit val decoder: Decoder[UByte] = Decoder.decodeInt.map(new UByte(_))
}

@JsonCodec
case class Room(worker_host: String,
                worker_video_port: Int,
                worker_audio_port: Int,
)

@JsonCodec
case class RoomWithId(room_id: UByte,
                      worker_host: String,
                      worker_video_port: Int,
                      worker_audio_port: Int,
)

@JsonCodec
case class RoomWithUsers(users: Map[String, User],
                         worker_host: String,
                         worker_video_port: Int,
                         worker_audio_port: Int,
)

@JsonCodec
case class User(id: UByte, name: String)

case class SupervisorClient(http: SttpBackend[Task, Any], url: String) {
  type UserId = UByte
  type RoomId = UByte

  def createUser(name: String): Task[UserId] = {
    val req = basicRequest
      .post(uri"$url/user/create?name=$name")
      .response(asJson[User])
    UIO(println(req.toCurl)) *>
      http
        .send(req)
        .tap(r => UIO(println(r.toString())))
        .flatMap(_.body.fold(Task.fail(_), r => UIO(r.id)))
  }

  def removeUser(userId: UserId): Task[Unit] = {
    val req = basicRequest
      .post(uri"$url/user/remove?user_id=${userId.toString}")
    UIO(println(req.toCurl)) *>
      http
        .send(req)
        .tap(r => UIO(println(r.toString())))
        .unit
  }

  def createRoom(userId: UserId): Task[RoomWithId] =
    postWithBody[CreateRoom, RoomWithId](
      CreateRoom(userId),
      List("room", "create")
    )
  def joinRoom(roomId: RoomId, userId: UserId): Task[Room] =
    postWithBody[JoinRoom, Room](JoinRoom(userId, roomId), List("room", "join"))
  def roomInfo(roomId: RoomId): Task[RoomWithUsers] = {
    val req = basicRequest
      .get(uri"$url".addPath("room", "id", roomId.toString))
      .response(asJson[RoomWithUsers])
    UIO(println(req.toCurl)) *>
      http
        .send(req)
        .tap(r => UIO(println(r.toString())))
        .flatMap(_.body.fold(Task.fail(_), r => UIO(r)))
  }

  def leaveRoom(roomId: RoomId, userId: UserId): Task[Unit] = {
    val req =
      basicRequest
        .post(uri"$url/room/leave/")
        .body(LeaveRoom(userId, roomId).asJson.noSpaces)
    UIO(println(req.toCurl)) *>
      http
        .send(req.response(asJson[Json]))
        .tap(r => UIO(println(r.toString())))
        .unit
  }

  private def postWithBody[R: Encoder, D: Decoder](
    body: R,
    paths: List[String]
  ): Task[D] = {
    val req =
      basicRequest
        .post(uri"$url".addPath(paths))
        .body(body.asJson.noSpaces)
    UIO(println(req.toCurl)) *>
      http
        .send(req.response(asJson[D]))
        .tap(r => UIO(println(r.toString())))
        .flatMap(_.body.fold(Task.fail(_), r => UIO(r)))
  }
}

object SupervisorClient {
  def managed(url: String): TaskManaged[SupervisorClient] =
    AsyncHttpClientZioBackend.managed().map(SupervisorClient(_, url))
}

@JsonCodec
case class CreateUser(name: String)
@JsonCodec
case class RemoveUser(userId: UByte)

@JsonCodec
case class CreateRoom(user_id: UByte)

@JsonCodec
case class JoinRoom(user_id: UByte, room_id: UByte)

@JsonCodec
case class LeaveRoom(user_id: UByte, room_id: UByte)
