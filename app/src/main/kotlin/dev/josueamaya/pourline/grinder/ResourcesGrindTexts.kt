package dev.josueamaya.pourline.grinder

import android.content.Context
import dev.josueamaya.pourline.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** [GrindTexts] con los recursos de la aplicación, leídos en cada llamada para seguir el idioma actual. */
class ResourcesGrindTexts(context: Context) : GrindTexts {
  private val resources = context.applicationContext.resources

  override fun quantity(unit: GrindUnit, value: Double): String {
    val whole = abs(value - value.roundToLong()) < 1e-6
    // Un valor no entero cae siempre en el plural `other`.
    val count = if (whole) value.roundToLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else 2
    val decimals = if (whole) 0 else (1..MAX_DECIMALS).firstOrNull { abs(value * POW[it] - (value * POW[it]).roundToLong()) < 1e-6 } ?: MAX_DECIMALS
    return resources.getQuantityString(plural(unit), count, number(value, decimals))
  }

  override fun unitName(unit: GrindUnit): String = resources.getString(
    when (unit) {
      GrindUnit.CLICKS -> R.string.grind_unit_name_clicks
      GrindUnit.POINTS -> R.string.grind_unit_name_points
      GrindUnit.NOTCHES -> R.string.grind_unit_name_notches
      GrindUnit.NUMBERS -> R.string.grind_unit_name_numbers
      GrindUnit.ROTATIONS -> R.string.grind_unit_name_rotations
      GrindUnit.STEPS -> R.string.grind_unit_name_steps
    },
  )

  override fun number(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

  override fun copyName(model: String): String = resources.getString(R.string.grinder_copy_name, model)

  private fun plural(unit: GrindUnit) = when (unit) {
    GrindUnit.CLICKS -> R.plurals.grind_unit_clicks
    GrindUnit.POINTS -> R.plurals.grind_unit_points
    GrindUnit.NOTCHES -> R.plurals.grind_unit_notches
    GrindUnit.NUMBERS -> R.plurals.grind_unit_numbers
    GrindUnit.ROTATIONS -> R.plurals.grind_unit_rotations
    GrindUnit.STEPS -> R.plurals.grind_unit_steps
  }

  private companion object {
    const val MAX_DECIMALS = 3
    val POW = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)
  }
}
