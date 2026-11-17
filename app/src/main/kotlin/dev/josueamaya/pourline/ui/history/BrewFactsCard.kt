package dev.josueamaya.pourline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.components.AntRate
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.formatDuration
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * Los datos de una preparación en modo Básico (D9): fecha, receta, café (enlace), dosis, agua, ratio, tiempo total,
 * valoración y notas. Lo que no hay se omite.
 */
@Composable
fun BrewFactsCard(record: BrewRecord, coffee: CoffeeEntity?, onOpenCoffee: (Long) -> Unit, modifier: Modifier = Modifier) {
  val entity = record.entity
  val unit = entity.unit
  val coffeeName = coffee?.name ?: entity.coffee
  val recipe = entity.referenceName?.takeIf { entity.referenceType == ReferenceType.RECIPE }
  val grams = { value: Double -> "${formatNumber(fromGrams(value, unit), unit)} ${unit.symbol}" }
  AntCard(modifier.fillMaxWidth().testTag("brew_detail_facts"), title = stringResource(R.string.brew_facts_title)) {
    FactRow(stringResource(R.string.brew_facts_date), formatBrewDateTime(entity.startedAtEpochMs), "date")
    recipe?.let { FactRow(stringResource(R.string.brew_facts_recipe), it, "recipe") }
    if (coffeeName != null) {
      AntDivider()
      TastingCoffee(entity.coffeeId, coffeeName, coffee, entity.startedAtEpochMs, onOpenCoffee)
      AntDivider()
    }
    entity.doseG?.let { FactRow(stringResource(R.string.scale_dose), grams(it), "dose") }
    FactRow(stringResource(R.string.brew_summary_total_water), grams(entity.totalWaterG), "water")
    entity.finalRatio?.let { FactRow(stringResource(R.string.scale_ratio), stringResource(R.string.scale_ratio_value, it), "ratio") }
    FactRow(stringResource(R.string.brew_summary_total_time), formatDuration(entity.totalTimeMs), "time")
    entity.rating?.let { rating ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AntText(stringResource(R.string.brew_detail_rating), Modifier.weight(1f), type = AntTextType.Secondary)
        AntRate(rating, onValueChange = null, testTag = "brew_detail_rating")
      }
    }
    entity.notes?.let { notes ->
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.brew_detail_notes), type = AntTextType.Secondary)
        AntText(notes, Modifier.testTag("brew_detail_notes"))
      }
    }
  }
}

@Composable
private fun FactRow(label: String, value: String, key: String) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    AntText(label, Modifier.weight(1f), type = AntTextType.Secondary)
    AntText(value, Modifier.testTag("brew_detail_facts_$key"), textAlign = TextAlign.End)
  }
}
