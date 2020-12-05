import javafx.application.Platform
import zio.Task

import scala.util.{Failure, Success, Try}

package object ui {
  def runOnFxThread(effect: () => Unit): Task[Unit] =
    Task.effectAsync{ callback =>
      Platform.runLater { () =>
        Try(effect()) match {
          case Failure(exception) => callback(Task.fail(exception))
          case Success(_) => callback(Task.unit)
        }
      }
    }
}
