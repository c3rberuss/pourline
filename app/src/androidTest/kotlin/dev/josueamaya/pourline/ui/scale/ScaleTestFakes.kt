package dev.josueamaya.pourline.ui.scale

import dev.josueamaya.pourline.brew.StageNotifier
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.settings.CoffeeStore
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.settings.ReferenceStore
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
