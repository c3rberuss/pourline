package dev.dotscale.tools

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Los tres campos de la calculadora de ratio. */
enum class RatioField { DOSE, RATIO, WATER }

/**
 * Lo que muestra la calculadora: el texto de cada campo, tal como se escribió o se calculó, y los campos que no
 * valen (vacíos, no numéricos o fuera de rango).
 */
data class RatioCalculatorState(
  val dose: String,
  val ratio: String,
  val water: String,
  val invalid: Set<RatioField> = emptySet(),
) {
  fun text(field: RatioField): String = when (field) {
    RatioField.DOSE -> dose
    RatioField.RATIO -> ratio
    RatioField.WATER -> water
  }

  internal fun withText(field: RatioField, text: String): RatioCalculatorState = when (field) {
    RatioField.DOSE -> copy(dose = text)
    RatioField.RATIO -> copy(ratio = text)
    RatioField.WATER -> copy(water = text)
  }
}

/**
 * La calculadora de ratio: café, ratio (1:x) y agua, los tres editables.
 *
 * - Con el café o el ratio se recalcula el agua.
 * - Con el agua se recalcula el café, porque el ratio es lo que el usuario suele querer conservar.
 * - Si el campo que haría de ancla no vale, se recalcula el otro (por ejemplo, con el ratio vacío, café y agua
 *   dan el ratio).
 */
object RatioCalculator {
  const val MAX_DOSE_G = 1_000.0
  const val MIN_RATIO = 1.0
  const val MAX_RATIO = 50.0
  const val MAX_WATER_G = 50_000.0

  fun initial(doseG: Double, ratio: Double, locale: Locale = Locale.getDefault()) = RatioCalculatorState(
    dose = format(doseG, locale),
    ratio = format(ratio, locale),
    water = format(doseG * ratio, locale),
  )

  /** El usuario escribió [text] en [field]: se guarda tal cual y se recalcula el campo que corresponda. */
  fun edit(state: RatioCalculatorState, field: RatioField, text: String, locale: Locale = Locale.getDefault()): RatioCalculatorState {
    val typed = state.withText(field, text)
    val value = parse(field, text) ?: return typed.copy(invalid = typed.invalid + field)
    val next = typed.copy(invalid = typed.invalid - field)
    val dose = valueOf(next, RatioField.DOSE)
    val ratio = valueOf(next, RatioField.RATIO)
    val water = valueOf(next, RatioField.WATER)
    return when (field) {
      RatioField.DOSE -> when {
        ratio != null -> next.computed(RatioField.WATER, value * ratio, locale)
        water != null -> next.computed(RatioField.RATIO, water / value, locale)
        else -> next
      }
      RatioField.RATIO -> when {
        dose != null -> next.computed(RatioField.WATER, dose * value, locale)
        water != null -> next.computed(RatioField.DOSE, water / value, locale)
        else -> next
      }
      RatioField.WATER -> when {
        ratio != null -> next.computed(RatioField.DOSE, value / ratio, locale)
        dose != null -> next.computed(RatioField.RATIO, value / dose, locale)
        else -> next
      }
    }
  }

  /** El valor de [text] para [field], con coma o punto decimal; `null` si no es un número dentro del rango. */
  fun parse(field: RatioField, text: String): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && inRange(field, it) }
  }

  /** Un decimal como mucho y sin ",0": "15", "16,7". */
  fun format(value: Double, locale: Locale = Locale.getDefault()): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else String.format(locale, "%.1f", tenths / 10.0)
  }

  private fun inRange(field: RatioField, value: Double): Boolean = when (field) {
    RatioField.DOSE -> value > 0.0 && value <= MAX_DOSE_G
    RatioField.RATIO -> value in MIN_RATIO..MAX_RATIO
    RatioField.WATER -> value > 0.0 && value <= MAX_WATER_G
  }

  private fun valueOf(state: RatioCalculatorState, field: RatioField): Double? =
    if (field in state.invalid) null else parse(field, state.text(field))

  /** Escribe un valor calculado, redondeado como se muestra, y lo marca como inválido si se sale del rango. */
  private fun RatioCalculatorState.computed(field: RatioField, value: Double, locale: Locale): RatioCalculatorState {
    val shown = (value * 10).roundToLong() / 10.0
    val valid = abs(shown) > 0.0 && inRange(field, shown)
    return withText(field, format(value, locale)).copy(invalid = if (valid) invalid - field else invalid + field)
  }
}
