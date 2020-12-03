package ui.conrollers.room

import java.awt.image.BufferedImage

import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane

final case class TileInfo(name: String, node: StackPane, imageInfo: Option[ImageInfo])

final case class ImageInfo(imageView: ImageView, bufferedImage: BufferedImage)
