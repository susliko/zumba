package ui.conrollers

import ui.conrollers.menu.MenuController
import ui.conrollers.room.RoomController

sealed trait Controller

object Controller {
  final case class Menu(menu: MenuController) extends Controller
  final case class Room(room: RoomController) extends Controller
}
