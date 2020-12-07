package ui.conrollers.menu

import javafx.fxml.FXML
import javafx.scene.control.{Button, CheckBox, TextField}
import javafx.scene.paint.Color
import javafx.scene.text.Text
import ui.conrollers.{Mediator, SceneType}
import web.UByte
import zio.blocking.Blocking
import zio.{Runtime, Task}
import ui.runOnFxThread

class MenuController(mediator: Mediator)(implicit runtime: Runtime[Blocking]) {
  @FXML
  var nameTextField: TextField = _

  @FXML
  var roomTextField: TextField = _

  @FXML
  var enterInfoText: Text = _

  @FXML
  var createInfoText: Text = _

  @FXML
  var enterButton: Button = _

  @FXML
  var createButton: Button = _

  @FXML
  var useMicrophone: CheckBox = _

  @FXML
  var useWebcam: CheckBox = _

  @FXML
  def enterRoom(): Unit =
    runtime.unsafeRunAsync_(
      (for {
        _ <- runOnFxThread { () =>
          enterInfoText.setText("Присоединяемся...")
          enterInfoText.setVisible(true)
          enterInfoText.setManaged(true)
          enterInfoText.setFill(Color.BLACK)
          enterButton.setDisable(true)
          createButton.setDisable(true)
        }
        roomId <- Task(new UByte(roomTextField.getText.toInt))
        _ <- mediator.joinRoom(roomId)
      } yield ()).catchAll(
        error =>
          runOnFxThread { () =>
            enterInfoText.setText("Такой комнаты нет")
            enterInfoText.setFill(Color.RED)
            enterButton.setDisable(false)
            createButton.setDisable(false)
          }
      )


    )

  @FXML
  def createRoom(): Unit =
    runtime.unsafeRunAsync_(
      (for {
        _ <- runOnFxThread { () =>
          createInfoText.setText("Создаем...")
          createInfoText.setVisible(true)
          createInfoText.setManaged(true)
          createInfoText.setFill(Color.BLACK)
          enterButton.setDisable(true)
          createButton.setDisable(true)
        }
        _ <- mediator.createRoom
      } yield ()).catchAll(
        error =>
          runOnFxThread { () =>
            createInfoText.setText("Попробуйте еще раз")
            createInfoText.setFill(Color.RED)
            enterButton.setDisable(false)
            createButton.setDisable(false)
          }
      )
    )

  @FXML
  def switchMicrophone(): Unit =
    runtime.unsafeRunAsync_(
      if (useMicrophone.isSelected) {
        mediator.enableMicrophone
      } else {
        mediator.disableMicrophone
      }
    )

  @FXML
  def switchWebcam(): Unit =
    runtime.unsafeRunAsync_(
      if (useWebcam.isSelected) {
        mediator.enableWebcam
      } else {
        mediator.disableWebcam
      }
    )

  @FXML
  def changeName(): Unit =
    runtime.unsafeRunAsync_(mediator.setName(nameTextField.getText))

  def start: Task[Unit] =
    mediator.getSettings.flatMap(settings =>
      runOnFxThread(() => nameTextField.setText(settings.name))
    )
}
