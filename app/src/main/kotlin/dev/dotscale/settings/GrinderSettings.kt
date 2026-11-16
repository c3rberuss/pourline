package dev.dotscale.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dónde se recuerda el molino predeterminado (SPEC-006). Lo implementa [GrinderSettings]; en los tests, un falso. */
interface DefaultGrinderStore {
  val defaultGrinderId: StateFlow<Long?>

  fun setDefaultGrinderId(id: Long?)
}

/** La versión del catálogo de molinos ya sincronizada con la base de datos. */
interface CatalogVersionStore {
  /** 0 si todavía no se ha sembrado. */
  val catalogVersion: Int

  fun setCatalogVersion(version: Int)
}

/** Molino predeterminado y versión del catálogo, en el mismo archivo `"settings"` que [BrewSettings]. */
class GrinderSettings(
  context: Context,
  prefsName: String = BrewSettings.PREFS_NAME,
) : DefaultGrinderStore, CatalogVersionStore {
  private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  private val _defaultGrinderId = MutableStateFlow(if (prefs.contains(KEY_DEFAULT_GRINDER)) prefs.getLong(KEY_DEFAULT_GRINDER, 0) else null)
  override val defaultGrinderId: StateFlow<Long?> = _defaultGrinderId.asStateFlow()

  override val catalogVersion: Int get() = prefs.getInt(KEY_CATALOG_VERSION, 0)

  override fun setDefaultGrinderId(id: Long?) {
    prefs.edit { if (id == null) remove(KEY_DEFAULT_GRINDER) else putLong(KEY_DEFAULT_GRINDER, id) }
    _defaultGrinderId.value = id
  }

  override fun setCatalogVersion(version: Int) {
    prefs.edit { putInt(KEY_CATALOG_VERSION, version) }
  }

  companion object {
    const val KEY_DEFAULT_GRINDER = "default_grinder_id"
    const val KEY_CATALOG_VERSION = "grinder_catalog_version"
  }
}
