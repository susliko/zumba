package ui.conrollers

sealed trait SceneType

object SceneType {
  case object Menu extends SceneType
  case object Room extends SceneType
}
