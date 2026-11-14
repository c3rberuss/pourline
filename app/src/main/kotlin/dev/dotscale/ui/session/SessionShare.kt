package dev.dotscale.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.brew.BrewSummary
import dev.dotscale.coffee.brewDaysSinceRoast
import dev.dotscale.session.ScaleUiState
import dev.dotscale.share.BrewShareData
import dev.dotscale.share.FooterCoffee
import dev.dotscale.share.footerLines
import dev.dotscale.ui.history.formatBrewDateTime
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
