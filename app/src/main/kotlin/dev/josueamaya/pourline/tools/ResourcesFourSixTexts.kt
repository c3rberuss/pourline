package dev.josueamaya.pourline.tools

import android.content.Context
import androidx.annotation.StringRes
import dev.josueamaya.pourline.R

/** [FourSixTexts] con los recursos de la aplicación, leídos en cada llamada para seguir el idioma actual. */
class ResourcesFourSixTexts(context: Context) : FourSixTexts {
  private val resources = context.applicationContext.resources

  override fun recipeName(taste: FourSixTaste, strength: FourSixStrength): String =
    resources.getString(R.string.four_six_recipe_name, resources.getString(taste.labelRes), resources.getString(strength.labelRes))

  override fun pourName(index: Int): String {
    val names = resources.getStringArray(R.array.four_six_pour_names)
    return names.getOrNull(index) ?: resources.getString(R.string.four_six_pour_name_n, index + 1)
  }

  override fun pourInstruction(phase: FourSixPhase, last: Boolean): String = resources.getString(
    when {
      last -> R.string.four_six_instruction_last
      phase == FourSixPhase.TASTE -> R.string.four_six_instruction_taste
      else -> R.string.four_six_instruction_strength
    },
  )

  override fun waitInstruction(): String = resources.getString(R.string.four_six_instruction_wait)

  override fun drainInstruction(): String = resources.getString(R.string.four_six_instruction_drain)

  override fun notes(): String = resources.getString(R.string.four_six_recipe_notes)
}

@get:StringRes
val FourSixTaste.labelRes: Int
  get() = when (this) {
    FourSixTaste.SWEET -> R.string.four_six_taste_sweet
    FourSixTaste.MEDIUM_SWEET -> R.string.four_six_taste_medium_sweet
    FourSixTaste.BALANCED -> R.string.four_six_taste_balanced
    FourSixTaste.MEDIUM_BRIGHT -> R.string.four_six_taste_medium_bright
    FourSixTaste.BRIGHT -> R.string.four_six_taste_bright
  }

@get:StringRes
val FourSixStrength.labelRes: Int
  get() = when (this) {
    FourSixStrength.LIGHTEST -> R.string.four_six_strength_lightest
    FourSixStrength.LIGHT -> R.string.four_six_strength_light
    FourSixStrength.STANDARD -> R.string.four_six_strength_standard
    FourSixStrength.STRONG -> R.string.four_six_strength_strong
    FourSixStrength.STRONGEST -> R.string.four_six_strength_strongest
  }

@get:StringRes
val FourSixPace.labelRes: Int
  get() = when (this) {
    FourSixPace.NORMAL -> R.string.four_six_pace_normal
    FourSixPace.SLOW -> R.string.four_six_pace_slow
    FourSixPace.SLOWEST -> R.string.four_six_pace_slowest
    FourSixPace.CUSTOM -> R.string.four_six_pace_custom
  }
