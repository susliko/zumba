package ui.conrollers

final case class Settings(
                           name: String,
                           useMicrophone: Boolean,
                           usePlayback: Boolean,
                           useWebcam: Boolean,
                           selectedMicrophone: String,
                           selectedPlayback: String,
                           selectedWebcam: String
                         )
