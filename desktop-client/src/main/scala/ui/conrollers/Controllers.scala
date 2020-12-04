package ui.conrollers

import ui.conrollers.room.RoomController
import zio.ZManaged
import zio.console.Console

final case class Controllers(roomController: RoomController)

object Controllers {
  def managed(implicit runtime: zio.Runtime[Any]): ZManaged[Console, Throwable, Controllers] =
    RoomController.managed.map(roomController => Controllers(roomController))
}
