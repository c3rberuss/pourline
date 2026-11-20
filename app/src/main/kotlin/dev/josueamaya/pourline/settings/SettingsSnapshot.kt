package dev.josueamaya.pourline.settings

import android.content.SharedPreferences

/**
 * Los ajustes de uso que viajan en una copia de seguridad (SPEC-011 §3.2), con el valor tal como está guardado en
 * `SharedPreferences`. `null` es "clave ausente": no se escribe en la copia y, al restaurar, se borra en el destino.
 * `tour_seen` no está: el estado del recorrido guiado es del dispositivo.
 */
data class SettingsSnapshot(
  val themeMode: String? = null,
  val experienceMode: String? = null,
  val onboardingDone: Boolean? = null,
  val useScale: Boolean? = null,
  val lastScaleAddress: String? = null,
  val lastScaleName: String? = null,
  val autoFinishSeconds: Int? = null,
  val countdownSeconds: Int? = null,
  val lastCoffeeId: Long? = null,
  val defaultDoseG: Float? = null,
  val defaultRatio: Float? = null,
  val targetRefType: String? = null,
  val targetRefId: Long? = null,
  val stageVibration: Boolean? = null,
  val stageSound: Boolean? = null,
  val defaultGrinderId: Long? = null,
  val grinderCatalogVersion: Int? = null,
)

/**
 * Una clase de `settings` que exporta y restaura sus claves en una copia de seguridad (SPEC-011 §3.2 y §3.5). Las
 * claves se escriben a través de la clase, no directamente en `SharedPreferences`, para que sus `StateFlow` en
 * memoria reflejen los valores nuevos sin recrearla. `TourSettings` no la implementa: el recorrido guiado es del
 * dispositivo.
 */
interface BackupableSettings {
  /** Copia en [snapshot] las claves de esta clase tal como están guardadas, sin validar; un tipo inesperado cuenta como ausente. */
  fun exportInto(snapshot: SettingsSnapshot): SettingsSnapshot

  /**
   * Escribe las claves de esta clase con los valores de [snapshot], en una sola edición confirmada (`commit`), borra
   * las que son `null` y recalcula los `StateFlow` con la misma lectura que usa el constructor: un valor fuera de rango
   * cae en el de por defecto, sin lanzar.
   */
  fun restoreFrom(snapshot: SettingsSnapshot)
}

internal fun SharedPreferences.storedString(key: String): String? = stored(key) { getString(key, null) }

internal fun SharedPreferences.storedInt(key: String): Int? = stored(key) { getInt(key, 0) }

internal fun SharedPreferences.storedLong(key: String): Long? = stored(key) { getLong(key, 0L) }

internal fun SharedPreferences.storedFloat(key: String): Float? = stored(key) { getFloat(key, 0f) }

internal fun SharedPreferences.storedBoolean(key: String): Boolean? = stored(key) { getBoolean(key, false) }

/** La clave ausente, o con un tipo distinto del esperado (`ClassCastException`), es `null`. */
private inline fun <T> SharedPreferences.stored(key: String, read: SharedPreferences.() -> T?): T? =
  if (contains(key)) runCatching { read() }.getOrNull() else null

internal fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
  if (value == null) remove(key) else putString(key, value)
}

internal fun SharedPreferences.Editor.putOrRemove(key: String, value: Int?) {
  if (value == null) remove(key) else putInt(key, value)
}

internal fun SharedPreferences.Editor.putOrRemove(key: String, value: Long?) {
  if (value == null) remove(key) else putLong(key, value)
}

internal fun SharedPreferences.Editor.putOrRemove(key: String, value: Float?) {
  if (value == null) remove(key) else putFloat(key, value)
}

internal fun SharedPreferences.Editor.putOrRemove(key: String, value: Boolean?) {
  if (value == null) remove(key) else putBoolean(key, value)
}
