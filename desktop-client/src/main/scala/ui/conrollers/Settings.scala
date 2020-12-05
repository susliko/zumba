package ui.conrollers

final case class Settings(
                           name: String,
                           useAudio: Boolean,
                           useVideo: Boolean,
                           selectedAudio: String,
                           selectedVideo: String
                         )
