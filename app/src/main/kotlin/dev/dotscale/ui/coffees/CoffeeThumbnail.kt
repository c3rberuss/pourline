package dev.dotscale.ui.coffees

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.coffee.photo.LocalCoffeeImages
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.theme.AntTheme

private const val DETAIL_PHOTO_MAX_SIDE = 1200

/** Miniatura cuadrada del café; sin foto (o sin poder leerla), el marcador con la taza. */
@Composable
fun CoffeeThumbnail(photo: String?, size: Dp, modifier: Modifier = Modifier) {
  val images = LocalCoffeeImages.current
  val bitmap by produceState<ImageBitmap?>(null, photo, images) {
    value = if (photo != null && images != null) images.thumbnail(photo) else null
  }
  val shape = RoundedCornerShape(AntTheme.radius.base)
  Box(modifier.size(size).clip(shape).background(AntTheme.colors.fillTertiary), contentAlignment = Alignment.Center) {
    val image = bitmap
    if (image != null) {
      Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Icon(AntIcons.Coffee, contentDescription = null, modifier = Modifier.size(24.dp), tint = AntTheme.colors.textQuaternary)
    }
  }
}

/** La foto grande recortada al centro, para el detalle y el editor. El tamaño lo pone [modifier]. */
@Composable
fun CoffeePhotoView(photo: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
  val images = LocalCoffeeImages.current
  val bitmap by produceState<ImageBitmap?>(null, photo, images) {
    value = if (photo != null && images != null) images.photo(photo, DETAIL_PHOTO_MAX_SIDE) else null
  }
  Box(
    modifier.clip(RoundedCornerShape(AntTheme.radius.lg)).background(AntTheme.colors.fillTertiary),
    contentAlignment = Alignment.Center,
  ) {
    val image = bitmap
    if (image != null) {
      Image(image, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Icon(AntIcons.Coffee, contentDescription = null, modifier = Modifier.size(32.dp), tint = AntTheme.colors.textQuaternary)
    }
  }
}
