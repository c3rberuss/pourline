package dev.dotscale.ui.grinders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.grinder.GrindDirection
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.GrindTexts
import dev.dotscale.grinder.GrindUnit
import dev.dotscale.grinder.ResourcesGrindTexts

/** Los textos del formato en el idioma actual; se recrean al cambiar la configuración (idioma). */
@Composable
fun rememberGrindTexts(): GrindTexts {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  return remember(context, configuration) { ResourcesGrindTexts(context) }
}

@Composable
fun GrindUnit.label(): String = stringResource(
  when (this) {
    GrindUnit.CLICKS -> R.string.grind_unit_name_clicks
    GrindUnit.POINTS -> R.string.grind_unit_name_points
    GrindUnit.NOTCHES -> R.string.grind_unit_name_notches
    GrindUnit.NUMBERS -> R.string.grind_unit_name_numbers
    GrindUnit.ROTATIONS -> R.string.grind_unit_name_rotations
    GrindUnit.STEPS -> R.string.grind_unit_name_steps
  },
)

@Composable
fun GrindLevel.label(): String = stringResource(
  when (this) {
    GrindLevel.EXTRA_FINE -> R.string.grind_level_extra_fine
    GrindLevel.FINE -> R.string.grind_level_fine
    GrindLevel.MEDIUM_FINE -> R.string.grind_level_medium_fine
    GrindLevel.MEDIUM -> R.string.grind_level_medium
    GrindLevel.MEDIUM_COARSE -> R.string.grind_level_medium_coarse
    GrindLevel.COARSE -> R.string.grind_level_coarse
    GrindLevel.EXTRA_COARSE -> R.string.grind_level_extra_coarse
  },
)

/** La abreviatura de la regla: EF, F, MF, M, MG, G, EG (en inglés XF, F, MF, M, MC, C, XC). */
@Composable
fun GrindLevel.abbreviation(): String = stringResource(
  when (this) {
    GrindLevel.EXTRA_FINE -> R.string.grind_level_extra_fine_abbr
    GrindLevel.FINE -> R.string.grind_level_fine_abbr
    GrindLevel.MEDIUM_FINE -> R.string.grind_level_medium_fine_abbr
    GrindLevel.MEDIUM -> R.string.grind_level_medium_abbr
    GrindLevel.MEDIUM_COARSE -> R.string.grind_level_medium_coarse_abbr
    GrindLevel.COARSE -> R.string.grind_level_coarse_abbr
    GrindLevel.EXTRA_COARSE -> R.string.grind_level_extra_coarse_abbr
  },
)

@Composable
fun GrindDirection.label(): String = stringResource(
  when (this) {
    GrindDirection.HIGHER_IS_COARSER -> R.string.grinder_direction_coarser
    GrindDirection.HIGHER_IS_FINER -> R.string.grinder_direction_finer
  },
)
