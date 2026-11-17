package dev.josueamaya.pourline.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.coffee.brewDaysSinceRoast
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.share.BrewShareData
import dev.josueamaya.pourline.share.FooterCoffee
import dev.josueamaya.pourline.share.footerLines
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import java.time.ZoneId

/**
 * La imagen del resumen: la hora de inicio (la de la sesión guardada, o ahora si aún no se guardó), el objetivo, el
 * ajuste y el café. La valoración y las notas salen de la preparación guardada, si ya se añadieron.
 */
@Composable
internal fun summaryShareData(ui: ScaleUiState, summary: BrewSummary, rating: Int? = null, notes: String? = null): BrewShareData {
  val startedAt = ui.lastBrew?.startedAtEpochMs
  val fit = ui.lastFit
  val coffee = ui.lastBrew?.coffee
  return BrewShareData(
    samples = ui.brew.samples,
    summary = summary,
    unit = ui.unit,
    target = fit?.curve,
    title = formatBrewDateTime(startedAt ?: System.currentTimeMillis()),
    subtitle = fit?.let { stringResource(R.string.target_chip, it.name) },
    fitScore = fit?.fit?.score,
    rating = rating,
    notes = notes,
    footer = footerLines(
      coffee?.let {
        FooterCoffee(it.id, it.name, it.roaster, startedAt?.let { start -> brewDaysSinceRoast(it.roastDate, start, ZoneId.systemDefault()) })
      },
    ),
  )
}
