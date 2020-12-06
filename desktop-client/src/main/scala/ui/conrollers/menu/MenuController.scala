package ui.conrollers.menu

import javafx.fxml.FXML
import javafx.scene.control.TextField
import ui.conrollers.{Mediator, SceneType}
import web.UByte
import zio.blocking.Blocking
import zio.{Runtime, Task}

class MenuController(mediator: Mediator)(implicit runtime: Runtime[Blocking]) {
  @FXML
  var nameTextField: TextField = _

  @FXML
  var roomTextField: TextField = _

  @FXML
  def enterRoom(): Unit =
    runtime.unsafeRunAsync_(
      mediator.joinRoom(new UByte(roomTextField.getText.toInt))
    )

  @FXML
  def createRoom(): Unit =
    runtime.unsafeRunAsync_(
      mediator.createRoom
    )

  @FXML
  def changeName(): Unit =
    runtime.unsafeRunAsync_(mediator.setName(nameTextField.getText))
}
