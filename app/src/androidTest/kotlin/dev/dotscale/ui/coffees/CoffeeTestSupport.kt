package dev.dotscale.ui.coffees

import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import dev.dotscale.coffee.label.LabelOcr
import dev.dotscale.coffee.label.OcrLine
import dev.dotscale.coffee.label.SyntheticLabel
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** OCR de pruebas: devuelve [lines], falla con [failure] o espera a [gate]. */
class FakeLabelOcr(
  var lines: List<OcrLine> = emptyList(),
  var failure: Exception? = null,
  var gate: CompletableDeferred<Unit>? = null,
) : LabelOcr {
  var reads = 0

  override suspend fun read(photo: File): List<OcrLine> {
    reads++
    gate?.await()
    failure?.let { throw it }
    return lines
  }
}

/**
 * Registro de resultados de prueba: la galería devuelve una imagen de `cacheDir` y la cámara escribe una en el
 * último destino que el editor pidió ([cameraTarget]). Con [cancel] las dos "se cancelan".
 */
class FakeActivityResults(private val cacheDir: File) : ActivityResultRegistryOwner {
  var cameraTarget: File? = null
  var cancel = false
  var launches = 0

  override val activityResultRegistry = object : ActivityResultRegistry() {
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
      launches++
      val output: Any = when (contract) {
        is ActivityResultContracts.TakePicture -> {
          if (!cancel) cameraTarget?.let { SyntheticLabel.writeJpeg(it) }
          !cancel && cameraTarget != null
        }
        is ActivityResultContracts.PickVisualMedia -> {
          if (cancel) {
            NO_URI
          } else {
            Uri.fromFile(File(cacheDir, "gallery-${System.nanoTime()}.jpg").also { SyntheticLabel.writeJpeg(it) })
          }
        }
        else -> error("contrato no previsto: $contract")
      }
      @Suppress("UNCHECKED_CAST")
      dispatchResult(requestCode, if (output === NO_URI) null as O else output as O)
    }
  }

  private companion object {
    val NO_URI = Any()
  }
}
