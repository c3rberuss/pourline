package dev.josueamaya.pourline.share

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Lo que se dibuja en la imagen. La foto del café ya viene decodificada: la captura no espera a ninguna carga. */
@Stable
sealed interface ShareRequest {
  data class Coffee(val data: CoffeeShareData, val photo: ImageBitmap?) : ShareRequest

  data class Brew(val data: BrewShareData) : ShareRequest

  data class Compare(val data: CompareShareData) : ShareRequest
}

private const val TAG = "ShareCapture"

/** Si la tarjeta no se dibuja nunca (la app está en segundo plano, por ejemplo), la captura falla en lugar de esperar siempre. */
private const val FIRST_DRAW_TIMEOUT_MS = 10_000L

/** Sin nuevos dibujados durante este tiempo, la tarjeta se da por estable (las gráficas miden su ancho tras el primero). */
private const val QUIET_MS = 100L

/** Tope de esperas de [QUIET_MS]: si la tarjeta siguiera redibujándose, se captura igualmente. */
private const val MAX_SETTLE_ROUNDS = 10

/**
 * Dibuja la tarjeta de [request] fuera de pantalla y la entrega como [ImageBitmap] de 1080 × 1350 px (D30). La
 * tarjeta se mide al tamaño fijo dentro de un nodo de 1 × 1 px, con tema claro y densidad fija, y se graba en una
 * `GraphicsLayer` **sin** dibujarla en pantalla. Con `request == null` no compone nada. Un fallo al leer la capa
 * llama a [onFailed].
 */
@Composable
fun ShareCaptureHost(request: ShareRequest?, onCaptured: (ImageBitmap) -> Unit, onFailed: () -> Unit) {
  if (request == null) return
  val layer = rememberGraphicsLayer()
  val draws = remember { MutableStateFlow(0) }
  val density = remember { Density(SHARE_DENSITY, fontScale = 1f) }
  val width = with(density) { SHARE_SIZE.width.roundToPx() }
  val height = with(density) { SHARE_SIZE.height.roundToPx() }

  Layout(
    content = {
      CompositionLocalProvider(LocalDensity provides density) {
        PourlineTheme(darkTheme = false) {
          Box(
            Modifier.drawWithContent {
              layer.record { this@drawWithContent.drawContent() }
              draws.update { it + 1 }
            },
          ) { ShareCard(request) }
        }
      }
    },
  ) { measurables, _ ->
    val placeable = measurables.firstOrNull()?.measure(Constraints.fixed(width, height))
    layout(1, 1) { placeable?.place(0, 0) }
  }

  LaunchedEffect(request) {
    // La capa no tiene tamaño hasta que el nodo se dibuja por primera vez. Después se espera a que deje de redibujarse.
    if (withTimeoutOrNull(FIRST_DRAW_TIMEOUT_MS) { draws.first { it > 0 } } == null) {
      Log.w(TAG, "La tarjeta no llegó a dibujarse")
      onFailed()
      return@LaunchedEffect
    }
    for (round in 0 until MAX_SETTLE_ROUNDS) {
      val seen = draws.value
      withContext(Dispatchers.Default) { delay(QUIET_MS) }
      if (draws.value == seen) break
    }
    try {
      onCaptured(layer.toImageBitmap())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "No se pudo capturar la imagen", e)
      onFailed()
    }
  }
}

@Composable
private fun ShareCard(request: ShareRequest) {
  when (request) {
    is ShareRequest.Coffee -> CoffeeShareCard(request.data, request.photo)
    is ShareRequest.Brew -> BrewShareCard(request.data)
    is ShareRequest.Compare -> CompareShareCard(request.data)
  }
}
