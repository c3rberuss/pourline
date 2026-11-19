package dev.josueamaya.pourline.ui.recipes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.recipe.share.ImportError
import dev.josueamaya.pourline.recipe.share.ImportNotice
import dev.josueamaya.pourline.recipe.share.ImportSource
import dev.josueamaya.pourline.recipe.share.PendingRecipeImport
import dev.josueamaya.pourline.share.QrImageReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Las entradas internas de "Importar" en Recetas: lo que da el escáner o una imagen de la galería pasa a
 * [PendingRecipeImport], que decide si abre la vista previa o avisa del error. [hasSession] dice si hay una sesión en
 * curso en el momento de enviar (no hay entradas internas durante una sesión, pero se comprueba igualmente).
 */
class RecipeImportEntryViewModel(
  private val imports: PendingRecipeImport,
  private val reader: QrImageReader,
  private val hasSession: () -> Boolean,
) : ViewModel() {
  private val _busy = MutableStateFlow(false)

  /** Se está leyendo una imagen. */
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  /** Abre el escáner y envía lo que lea. Corre en el ViewModel: una rotación a mitad no pierde el resultado. */
  fun scan(scanner: QrScanner) {
    viewModelScope.launch { onScan(scanner.scan()) }
  }

  fun onScan(result: ScanResult) {
    when (result) {
      is ScanResult.Scanned -> imports.submit(result.text, ImportSource.SCANNER, hasSession())
      ScanResult.Cancelled -> Unit
      ScanResult.Unavailable -> imports.notify(ImportNotice.Failed(ImportError.SCANNER_UNAVAILABLE))
    }
  }

  /** [uri] es `null` si el usuario cerró el selector. */
  fun onImage(uri: Uri?) {
    if (uri == null || _busy.value) return
    _busy.value = true
    viewModelScope.launch {
      try {
        imports.submit(reader.read(uri), hasSession())
      } finally {
        _busy.value = false
      }
    }
  }
}
