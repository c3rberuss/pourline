package dev.josueamaya.pourline.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.ui.scale.BrewChart

/**
 * La curva objetivo de [recipe] (sin muestras): la vista previa del editor y del detalle de una recomendada (SPEC-012,
 * D41). Quien la usa decide si se muestra según el modo (`ExpertFeature.RECIPE_CURVE_PREVIEW`).
 */
@Composable
internal fun RecipeCurvePreview(recipe: Recipe, testTag: String, modifier: Modifier = Modifier) {
  val curve = remember(recipe) { TargetCurve.fromRecipe(recipe) }
  val unit = WeightUnit.GRAMS
  BrewChart(
    samples = emptyList(),
    unit = unit,
    waterUnitLabel = unit.symbol,
    flowUnitLabel = stringResource(R.string.scale_flow_value, unit.symbol),
    description = stringResource(R.string.recipe_edit_preview),
    modifier = modifier.testTag(testTag),
    target = curve,
  )
}
