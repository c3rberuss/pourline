package dev.dotscale.ui.scale

import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetStage
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
