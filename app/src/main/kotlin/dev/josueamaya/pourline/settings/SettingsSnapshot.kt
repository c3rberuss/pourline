package dev.josueamaya.pourline.settings

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
