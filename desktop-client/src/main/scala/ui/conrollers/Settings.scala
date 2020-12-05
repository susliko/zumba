package ui.conrollers

final case class Settings(
                           name: String,
                           useMicrophone: Boolean,
                           useWebcam: Boolean,
                           selectedMicrophone: String,
                           selectedWebcam: String
                         )
