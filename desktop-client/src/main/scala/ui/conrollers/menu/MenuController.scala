package ui.conrollers.menu

import javafx.fxml.FXML
import ui.conrollers.{Mediator, SceneType}
import zio.{Runtime, Task}

class MenuController(mediator: Mediator)(implicit runtime: Runtime[Any]) {
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
        println("Create!")
      }
    )
}
