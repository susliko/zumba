package ui.conrollers.menu

import javafx.fxml.FXML
import javafx.scene.control.TextField
import ui.conrollers.{Mediator, SceneType}
import zio.{Runtime, Task}

class MenuController(mediator: Mediator)(implicit runtime: Runtime[Any]) {
  @FXML
  var nameTextField: TextField = _

  @FXML
  def enterRoom(): Unit =
    runtime.unsafeRunAsync_(
      Task(println("Enter!")) *>
        mediator.switchScene(SceneType.Room)
    )

  @FXML
  def createRoom(): Unit =
    runtime.unsafeRunAsync_(
      Task {
        Task(println("Create!")) *>
          mediator.switchScene(SceneType.Room)
      }
    )

  @FXML
  def changeName(): Unit =
    runtime.unsafeRunAsync_(mediator.setName(nameTextField.getText))
}
