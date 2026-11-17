package dev.josueamaya.pourline.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dónde se guardan los pasos de los recorridos guiados que el usuario ya vio. Lo implementa [TourSettings]; en los tests, uno en memoria. */
interface TourStore {
  /** Los ids de los `TourStep` vistos. Un id que ya no existe en ningún recorrido no interviene. */
  val seen: StateFlow<Set<String>>

  /** Añade [ids] a los vistos; no escribe nada si ya estaban. */
  fun markSeen(ids: Collection<String>)

  /** Olvida todo lo visto ("Ver recorrido" en Ajustes). */
  fun reset()
}

/**
 * Los pasos vistos de los recorridos (SPEC-008), en el mismo archivo `"settings"` que [BrewSettings] y
 * [ExperienceSettings]. Una sola instancia por proceso (ver `PourlineApp`). Es independiente de `onboarding_done`: una
 * instalación existente empieza sin pasos vistos. Un valor ilegible se lee como vacío, sin error.
 */
class TourSettings internal constructor(private val prefs: SharedPreferences) : TourStore {
  constructor(context: Context, prefsName: String = BrewSettings.PREFS_NAME) :
    this(context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE))

  private val _seen = MutableStateFlow(read())
  override val seen: StateFlow<Set<String>> = _seen.asStateFlow()

  override fun markSeen(ids: Collection<String>) {
    val merged = _seen.value + ids
    if (merged == _seen.value) return
    prefs.edit { putStringSet(KEY_SEEN, merged) }
    _seen.value = merged
  }

  override fun reset() {
    prefs.edit { remove(KEY_SEEN) }
    _seen.value = emptySet()
  }

  /** Copia el conjunto (el que devuelve `SharedPreferences` no se debe modificar); otro tipo bajo la clave es vacío. */
  private fun read(): Set<String> = runCatching { prefs.getStringSet(KEY_SEEN, null)?.toSet() }.getOrNull() ?: emptySet()

  companion object {
    const val KEY_SEEN = "tour_seen"
  }
}
