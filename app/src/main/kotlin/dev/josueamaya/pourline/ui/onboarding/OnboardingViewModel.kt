package dev.josueamaya.pourline.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExperienceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** El catálogo de molinos del paso 2 y el cierre del onboarding (D6). */
class OnboardingViewModel(
  private val grinders: GrinderRepository,
  private val defaults: DefaultGrinderStore,
  private val experience: ExperienceSettings,
) : ViewModel() {
  /** El catálogo sin los retirados; `null` mientras carga. */
  val catalog: StateFlow<List<Grinder>?> = grinders.catalog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /**
   * Termina el onboarding: con [grinderId] lo marca como "Lo tengo" y como predeterminado, y después guarda el modo y
   * `onboarding_done` en una sola escritura.
   */
  fun finish(mode: ExperienceMode, grinderId: Long?) {
    viewModelScope.launch {
      if (grinderId != null) {
        grinders.setOwned(grinderId, true)
        defaults.setDefaultGrinderId(grinderId)
      }
      experience.completeOnboarding(mode)
    }
  }
}
