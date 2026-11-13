package dev.dotscale.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeLogEntry
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntCardSize
import dev.dotscale.ui.components.AntRate
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.history.formatBrewDateTime
import dev.dotscale.ui.theme.AntTheme

private const val NOTES_MAX_LINES = 2

/** Una preparación de la bitácora: fecha y valoración, "Día 9 · 15 g" y las notas de cata a dos líneas. */
@Composable
fun CoffeeLogRow(entry: CoffeeLogEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
  AntCard(modifier.testTag("coffee_log_row"), size = AntCardSize.Small, onClick = onClick) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        AntText(formatBrewDateTime(entry.startedAtEpochMs), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (entry.rating != null) AntRate(entry.rating, onValueChange = null, starSize = 14.dp, testTag = "coffee_log_rating")
      }
      val facts = listOfNotNull(
        entry.daysSinceRoast?.let { stringResource(R.string.coffee_log_day, it) },
        entry.doseG?.let(::formatGrams),
      )
      if (facts.isNotEmpty()) AntText(facts.joinToString(" · "), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      entry.notes?.takeIf { it.isNotBlank() }?.let {
        AntText(it, type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = NOTES_MAX_LINES, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
