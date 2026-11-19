package dev.josueamaya.pourline.recipe.share

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Un mensaje de la importación para el usuario. */
sealed interface ImportNotice {
  data class Failed(val error: ImportError) : ImportNotice

  /** La receta llegó con una sesión en curso y se verá al terminar. */
  data object Deferred : ImportNotice

  /** La receta importada se guardó. */
  data object Added : ImportNotice
}

/**
 * La cola de las importaciones, en memoria de la app (D14). Todas las entradas la usan: lo que llega con una sesión
 * en curso espera aquí y quien navega ([pending]) lo abre cuando la sesión termina. Solo hay una importación pendiente:
 * la última reemplaza a la anterior. Si el proceso muere, se pierde (§4).
 */
class PendingRecipeImport {
  private val _pending = MutableStateFlow<String?>(null)

  /** El URI de la receta que espera su vista previa, o `null`. */
  val pending: StateFlow<String?> = _pending.asStateFlow()

  private val channel = Channel<ImportNotice>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /** Los mensajes, en orden. Se guardan hasta que alguien los recoja. */
  val notices: Flow<ImportNotice> = channel.receiveAsFlow()

  /** Procesa [text] y deja el resultado en [pending] o en [notices]. */
  fun submit(text: String?, source: ImportSource, hasSession: Boolean): ImportOutcome =
    apply(routeImport(text, source, hasSession))

  /** Lo mismo con el resultado de leer una imagen. */
  fun submit(read: QrReadResult, hasSession: Boolean): ImportOutcome = apply(routeImageRead(read, hasSession))

  private fun apply(outcome: ImportOutcome): ImportOutcome {
    when (outcome) {
      is ImportOutcome.Open -> _pending.value = outcome.uri
      is ImportOutcome.Defer -> {
        _pending.value = outcome.uri
        notify(ImportNotice.Deferred)
      }
      is ImportOutcome.Fail -> notify(ImportNotice.Failed(outcome.error))
    }
    return outcome
  }

  /** Quita [uri] de [pending] si sigue siendo la pendiente. `false` si ya la reemplazó otra. */
  fun consume(uri: String): Boolean = _pending.compareAndSet(uri, null)

  fun notify(notice: ImportNotice) {
    channel.trySend(notice)
  }
}
