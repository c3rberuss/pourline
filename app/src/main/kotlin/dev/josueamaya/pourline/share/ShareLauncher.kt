package dev.josueamaya.pourline.share

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.photo.CoffeeImages
import dev.josueamaya.pourline.coffee.photo.LocalCoffeeImages
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.showAntMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** El contenido de la imagen que se quiere compartir. */
sealed interface ShareContent {
  data class Coffee(val data: CoffeeShareData) : ShareContent

  data class Brew(val data: BrewShareData) : ShareContent

  data class Compare(val data: CompareShareData) : ShareContent
}

private class Pending(val request: ShareRequest, val fileName: String)

/**
 * Encadena lo que pasa tras tocar "Compartir": carga la foto, dibuja la tarjeta ([Host]), escribe el PNG y abre el
 * selector con el [ShareSender]. Un fallo en cualquier paso muestra `AntMessage(Error)` y no abre el selector (§4).
 * Mientras hay una imagen en marcha, otro toque no hace nada.
 */
@Stable
class ShareLauncher internal constructor(
  private val scope: CoroutineScope,
  private val images: ShareImages,
  private val sender: ShareSender,
  private val coffeeImages: CoffeeImages?,
  private val snackbar: SnackbarHostState,
  private val chooserTitle: String,
  private val errorText: String,
) {
  private var pending by mutableStateOf<Pending?>(null)
  private var busy = false

  fun share(target: ShareTarget, content: ShareContent) {
    if (busy) return
    busy = true
    scope.launch {
      try {
        val request = when (content) {
          is ShareContent.Coffee -> ShareRequest.Coffee(content.data, content.data.photo?.let { coffeeImages?.photo(it, PHOTO_PX) })
          is ShareContent.Brew -> ShareRequest.Brew(content.data)
          is ShareContent.Compare -> ShareRequest.Compare(content.data)
        }
        pending = Pending(request, shareFileName(target))
      } catch (e: CancellationException) {
        busy = false
        throw e
      } catch (_: Exception) {
        fail()
      }
    }
  }

  /** Compone la tarjeta fuera de pantalla mientras hay una imagen pendiente; el resto del tiempo no emite nada. */
  @Composable
  fun Host() {
    ShareCaptureHost(
      request = pending?.request,
      onCaptured = { bitmap ->
        val current = pending ?: return@ShareCaptureHost
        pending = null
        scope.launch {
          try {
            val file = images.write(bitmap, current.fileName)
            sender.send(images.chooser(images.uri(file), chooserTitle))
            busy = false
          } catch (e: CancellationException) {
            busy = false
            throw e
          } catch (_: Exception) {
            fail()
          }
        }
      },
      onFailed = {
        pending = null
        scope.launch { fail() }
      },
    )
  }

  private suspend fun fail() {
    pending = null
    busy = false
    snackbar.showAntMessage(errorText, AntStatus.Error)
  }

  private companion object {
    /** 112 dp a densidad 3. */
    const val PHOTO_PX = SHARE_PHOTO_DP * 3
  }
}

/** El [ShareLauncher] de una pantalla. [snackbar] es el del `Scaffold` que muestra el aviso de error. */
@Composable
fun rememberShareLauncher(sender: ShareSender, snackbar: SnackbarHostState): ShareLauncher {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val coffeeImages = LocalCoffeeImages.current
  val title = stringResource(R.string.share_chooser_title)
  val error = stringResource(R.string.share_error)
  return remember(context, scope, sender, coffeeImages, snackbar, title, error) {
    ShareLauncher(scope, ShareImages(context.applicationContext), sender, coffeeImages, snackbar, title, error)
  }
}
