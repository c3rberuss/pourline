package dev.josueamaya.pourline

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.josueamaya.pourline.recipe.share.ImportSource
import dev.josueamaya.pourline.recipe.share.PendingRecipeImport
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.share.QrImageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Las entradas externas de una receta (SPEC-009 §3.5): `ACTION_VIEW` de `pourline://recipe?...` y `ACTION_SEND` de una
 * imagen. Las dos acaban en [PendingRecipeImport], que abre la vista previa o, con una sesión en curso, la aplaza.
 * [scope] es el de la app: leer la imagen sobrevive a una rotación a mitad.
 */
class RecipeIntentHandler(
  private val imports: PendingRecipeImport,
  private val reader: QrImageReader,
  private val hasSession: () -> Boolean,
  private val scope: CoroutineScope,
) {
  /** `true` si [intent] era una receta (aunque no se pudiera leer: el aviso ya se dio); `false` si no es asunto nuestro. */
  fun handle(intent: Intent): Boolean = when {
    intent.action == Intent.ACTION_VIEW && intent.data?.scheme.equals(RecipeShareCodec.SCHEME, ignoreCase = true) -> {
      imports.submit(intent.dataString, ImportSource.LINK, hasSession())
      true
    }
    intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
      val uri = streamOf(intent)
      if (uri == null) {
        imports.submit(null, ImportSource.IMAGE, hasSession())
      } else {
        scope.launch(Dispatchers.Main.immediate) { imports.submit(reader.read(uri), hasSession()) }
      }
      true
    }
    else -> false
  }

  private fun streamOf(intent: Intent): Uri? =
    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
      ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
}

/**
 * Si el intent de arranque de la actividad hay que procesarlo: no si se está recreando (rotación, proceso restaurado) ni
 * si se abrió desde Recientes, porque entonces es el intent con el que nació la tarea y ya se procesó.
 */
fun shouldHandleLaunchIntent(recreated: Boolean, intentFlags: Int): Boolean =
  !recreated && intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
