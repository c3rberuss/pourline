package dev.dotscale.ui.scale

import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetStage
import dev.dotscale.settings.CoffeeStore
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.settings.ReferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recuerda la referencia solo en memoria, para no tocar las preferencias reales del dispositivo. */
class MemoryReferenceStore(initial: ReferenceKey? = null) : ReferenceStore {
  private val _reference = MutableStateFlow(initial)
  override val reference: StateFlow<ReferenceKey?> = _reference.asStateFlow()

  override fun setReference(key: ReferenceKey?) {
    _reference.value = key
  }
}

object SilentNotifier : StageNotifier {
  override fun stageStarted(stage: TargetStage) = Unit

  override fun release() = Unit
}

/** Recuerda el último café solo en memoria, para no tocar las preferencias reales del dispositivo. */
class MemoryCoffeeStore(initial: Long? = null) : CoffeeStore {
  private val _lastCoffeeId = MutableStateFlow(initial)
  override val lastCoffeeId: StateFlow<Long?> = _lastCoffeeId.asStateFlow()

  override fun setLastCoffeeId(id: Long?) {
    _lastCoffeeId.value = id
  }
}
