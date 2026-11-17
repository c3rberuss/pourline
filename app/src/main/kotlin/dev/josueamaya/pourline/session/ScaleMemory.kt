package dev.josueamaya.pourline.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** La última báscula real que llegó a `Ready`. */
data class RememberedScale(val address: String, val name: String)

/** Dónde se recuerda la última báscula real. Lo implementa `ConnectionSettings`; en los tests, un falso. */
interface ScaleMemory {
  val lastScale: StateFlow<RememberedScale?>

  fun remember(scale: RememberedScale)
}

/** Sin memoria: el valor por defecto del gestor. */
object NoScaleMemory : ScaleMemory {
  override val lastScale: StateFlow<RememberedScale?> = MutableStateFlow(null)

  override fun remember(scale: RememberedScale) = Unit
}
