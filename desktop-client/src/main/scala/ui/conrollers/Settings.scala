package ui.conrollers

final case class Settings(
                           name: String,
                           isAudioActive: Boolean,
                           isVideoActive: Boolean,
                           selectedAudio: String,
                           selectedVideo: String
                         )
