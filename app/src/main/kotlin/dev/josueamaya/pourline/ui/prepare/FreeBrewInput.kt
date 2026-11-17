package dev.josueamaya.pourline.ui.prepare

import dev.josueamaya.pourline.settings.FreeBrewDefaults
import kotlin.math.abs
import kotlin.math.roundToLong

/** Un número con coma o punto decimal y, como mucho, un decimal; `null` si no lo es. */
internal fun parseOneDecimal(text: String): Double? {
  val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
  if (!value.isFinite()) return null
  val tenths = value * 10
  return value.takeIf { abs(tenths - tenths.roundToLong()) < 1e-6 }
}

/** La dosis de Preparar en gramos: de 5 a 60 con un decimal (D27); `null` si el texto no vale. */
internal fun parseFreeDose(text: String): Double? =
  parseOneDecimal(text)?.takeIf { it in FreeBrewDefaults.MIN_DOSE_G..FreeBrewDefaults.MAX_DOSE_G }

/**
 * "Empezar sin báscula" se habilita con receta, o sin ella si la dosis y el ratio escritos en Preparar son válidos
 * (SPEC-007 §3.1). A diferencia de "Empezar", no depende de la báscula.
 */
internal fun canStartScaleless(hasTarget: Boolean, doseText: String, ratioText: String): Boolean =
  hasTarget || (parseFreeDose(doseText) != null && parseFreeRatio(ratioText) != null)

/** El ratio de Preparar: de 10,0 a 20,0 con un decimal (D27); `null` si el texto no vale. */
internal fun parseFreeRatio(text: String): Double? =
  parseOneDecimal(text)?.takeIf { it in FreeBrewDefaults.MIN_RATIO..FreeBrewDefaults.MAX_RATIO }
