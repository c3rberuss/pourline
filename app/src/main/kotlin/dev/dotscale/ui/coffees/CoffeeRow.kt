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
import dev.dotscale.coffee.CoffeeListItem
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntCardSize
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

private val THUMB_SIZE = 56.dp
private val THUMB_SIZE_COMPACT = 40.dp

/**
 * Fila de un café: miniatura, nombre con "Agotado", tostador y "Tostado 12 oct 2026 · día 9" (lo que distingue
 * dos bolsas del mismo café). [compact] es la del selector: miniatura de 40 dp y el stock tras la fecha.
 * [selected] añade "Elegido" y [archivedTag], "Archivado".
 */
@Composable
fun CoffeeRow(
  item: CoffeeListItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  selected: Boolean = false,
  archivedTag: Boolean = false,
  testTag: String = "coffee_row",
) {
  AntCard(modifier.testTag(testTag), size = AntCardSize.Small, onClick = onClick) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm), verticalAlignment = Alignment.Top) {
      CoffeeThumbnail(item.photo1, if (compact) THUMB_SIZE_COMPACT else THUMB_SIZE)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
          AntText(item.name, Modifier.weight(1f, fill = false), strong = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (item.soldOut) AntTag(stringResource(R.string.coffee_sold_out), Modifier.testTag("coffee_sold_out_tag"), color = AntTagColor.Error)
          if (archivedTag && item.archived) AntTag(stringResource(R.string.coffee_archived_tag), Modifier.testTag("coffee_archived_tag"))
          if (selected) AntTag(stringResource(R.string.coffee_selected_tag), Modifier.testTag("coffee_selected_tag"), color = AntTagColor.Primary)
        }
        item.roaster?.let { AntText(it, type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        val roast = roastLine(item)
        val stock = stockLine(item)
        if (compact) {
          val grams = item.stockG?.takeIf { !item.soldOut }?.let(::formatGrams)
          AntText(
            listOfNotNull(roast, grams).joinToString(" · "),
            type = AntTextType.Secondary,
            style = AntTheme.typography.textSM,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        } else {
          AntText(roast, type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Ellipsis)
          stock?.let { AntText(it, type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1) }
        }
      }
    }
  }
}

/** "Tostado 12 oct 2026 · día 9", o "Sin fecha de tueste". */
@Composable
internal fun roastLine(item: CoffeeListItem): String {
  val date = item.roastDate ?: return stringResource(R.string.coffee_row_no_roast_date)
  val days = item.daysSinceRoast
  return if (days != null) {
    stringResource(R.string.coffee_row_roasted, formatCoffeeDate(date), days)
  } else {
    stringResource(R.string.coffee_row_roasted_no_days, formatCoffeeDate(date))
  }
}

/** "Quedan 180 g"; `null` sin peso de bolsa o con la bolsa agotada. */
@Composable
internal fun stockLine(item: CoffeeListItem): String? {
  val stock = item.stockG ?: return null
  if (item.soldOut) return null
  return stringResource(R.string.coffee_row_stock, formatGrams(stock))
}
