package ui.conrollers

import web.UByte

final case class Settings(
                           name: String,
                           useMicrophone: Boolean,
                           usePlayback: Boolean,
                           useWebcam: Boolean,
                           selectedMicrophone: String,
                           selectedPlayback: String,
                           selectedWebcam: String,
                           userId: UByte,
                           roomId: UByte,
                           workerHost: String,
                           workerVideoPort: Int,
                           workerAudioPort: Int,
                         )
