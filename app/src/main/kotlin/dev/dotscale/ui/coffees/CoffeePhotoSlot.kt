package dev.dotscale.ui.coffees

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import dev.dotscale.R
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.antBorder
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Un hueco de foto de la etiqueta (D7): cuadrado, con borde discontinuo y las opciones Cámara y Galería si está
 * vacío; con foto, la imagen recortada y, debajo, "Reemplazar" (un menú con Cámara y Galería) y "Quitar".
 * Cancelar la cámara o la galería no hace nada.
 *
 * @param newCameraTarget el archivo de caché donde la cámara escribe la foto (`CoffeePhotoStore.newCameraTarget`).
 * @param onPhoto recibe cómo abrir la foto elegida o capturada.
 */
@Composable
fun CoffeePhotoSlot(
  slot: Int,
  photo: String?,
  busy: Boolean,
  newCameraTarget: () -> File,
  onPhoto: (open: () -> InputStream) -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  // La ruta pendiente se guarda: la actividad puede recrearse mientras la cámara está abierta.
  var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
    val path = pendingCapture
    pendingCapture = null
    if (taken && path != null) onPhoto { FileInputStream(path) }
  }
  val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
    if (uri != null) {
      onPhoto { context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("No se pudo abrir la foto") }
    }
  }
  val launchCamera = {
    val target = newCameraTarget()
    pendingCapture = target.path
    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target))
  }
  val launchGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

  val label = stringResource(R.string.coffee_edit_photo_label, slot)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    Box(
      Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .testTag("coffee_photo_slot_$slot")
        .let { if (photo == null) it.clip(RoundedCornerShape(AntTheme.radius.lg)).background(AntTheme.colors.fillQuaternary) else it }
        .let { if (photo == null) it.antBorder(AntTheme.colors.border, AntTheme.radius.lg, dashed = true) else it },
      contentAlignment = Alignment.Center,
    ) {
      if (photo != null) {
        CoffeePhotoView(photo, Modifier.fillMaxSize(), contentDescription = label)
      } else {
        Column(
          Modifier.semantics { contentDescription = label },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
        ) {
          AntButton(
            stringResource(R.string.coffee_photo_camera),
            launchCamera,
            Modifier.testTag("coffee_photo_camera_$slot"),
            size = AntSize.Small,
            icon = AntIcons.Camera,
            enabled = !busy,
          )
          AntButton(
            stringResource(R.string.coffee_photo_gallery),
            launchGallery,
            Modifier.testTag("coffee_photo_gallery_$slot"),
            size = AntSize.Small,
            icon = AntIcons.Image,
            enabled = !busy,
          )
        }
      }
      if (busy) {
        Box(
          Modifier.fillMaxSize().background(AntTheme.colors.fillSecondary).testTag("coffee_photo_busy_$slot"),
          contentAlignment = Alignment.Center,
        ) { AntSpin() }
      }
    }
    if (photo != null) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        var menu by remember { mutableStateOf(false) }
        Box {
          AntButton(
            stringResource(R.string.coffee_photo_replace),
            { menu = true },
            Modifier.testTag("coffee_photo_replace_$slot"),
            type = AntButtonType.Link,
            size = AntSize.Small,
            enabled = !busy,
          )
          DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = AntTheme.colors.bgElevated) {
            DropdownMenuItem(
              text = { androidx.compose.material3.Text(stringResource(R.string.coffee_photo_camera), color = AntTheme.colors.text) },
              onClick = {
                menu = false
                launchCamera()
              },
              modifier = Modifier.testTag("coffee_photo_replace_camera_$slot"),
            )
            DropdownMenuItem(
              text = { androidx.compose.material3.Text(stringResource(R.string.coffee_photo_gallery), color = AntTheme.colors.text) },
              onClick = {
                menu = false
                launchGallery()
              },
              modifier = Modifier.testTag("coffee_photo_replace_gallery_$slot"),
            )
          }
        }
        AntButton(
          stringResource(R.string.coffee_photo_remove),
          onRemove,
          Modifier.testTag("coffee_photo_remove_$slot"),
          type = AntButtonType.Link,
          size = AntSize.Small,
          danger = true,
          enabled = !busy,
        )
      }
    }
  }
}
