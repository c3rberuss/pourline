package dev.josueamaya.pourline.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntCardSize
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.history.label
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * Fila de una receta: nombre, método, "15 g · 250 g · 1:16,7 · 3 etapas" y las etiquetas "Predefinida" y,
 * si [active], "Activa". También la usa el selector de objetivo de Scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeRow(item: RecipeListItem, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
  AntCard(modifier.testTag("recipe_row"), size = AntCardSize.Small, onClick = onClick) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val method = item.method?.let { if (it == BrewMethod.OTHER) item.methodOther ?: it.label() else it.label() }
      method?.let { AntText(it, type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      AntText(recipeSummary(item.doseG, item.totalWaterG, item.stageCount), type = AntTextType.Secondary)
      if (item.builtIn || active) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
          verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (item.builtIn) AntTag(stringResource(R.string.recipe_builtin_tag), Modifier.testTag("recipe_builtin_tag"))
          if (active) AntTag(stringResource(R.string.recipe_active_tag), Modifier.testTag("recipe_active_tag"), color = AntTagColor.Primary)
        }
      }
    }
  }
}
