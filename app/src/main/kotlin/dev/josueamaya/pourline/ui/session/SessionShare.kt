package dev.josueamaya.pourline.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.coffee.brewDaysSinceRoast
import dev.josueamaya.pourline.history.BrewEntity
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.share.BrewShareData
import dev.josueamaya.pourline.share.FooterCoffee
import dev.josueamaya.pourline.share.footerLines
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import java.time.ZoneId

/**
 * La imagen del resumen: la hora de inicio (la de la sesión guardada, o ahora si aún no se guardó), el objetivo, el
 * ajuste y el café. La valoración y las notas salen de la preparación guardada, si ya se añadieron.
 *
 * Sin báscula (SPEC-007) no hay curva ni ajuste: van los gramos del plan (o los corregidos en la preparación guardada,
 * [entity]) y el nombre del objetivo como subtítulo.
 */
@Composable
internal fun summaryShareData(
  ui: ScaleUiState,
  summary: BrewSummary,
  rating: Int? = null,
  notes: String? = null,
  entity: BrewEntity? = null,
): BrewShareData {
  val startedAt = ui.lastBrew?.startedAtEpochMs
  val fit = ui.lastFit
  val coffee = ui.lastBrew?.coffee
  val scaleless = ui.scaleless
  val saved = entity?.takeIf { scaleless }
  return BrewShareData(
    samples = ui.brew.samples,
    summary = if (saved != null) summary.copy(totalWaterG = saved.totalWaterG, finalRatio = saved.finalRatio) else summary,
    unit = ui.sessionUnit,
    target = fit?.curve,
    title = formatBrewDateTime(startedAt ?: System.currentTimeMillis()),
    subtitle = if (scaleless) ui.target?.name?.let { stringResource(R.string.target_chip, it) } else fit?.let { stringResource(R.string.target_chip, it.name) },
    fitScore = fit?.fit?.score,
    rating = rating,
    notes = notes,
    footer = footerLines(
      coffee?.let {
        FooterCoffee(it.id, it.name, it.roaster, startedAt?.let { start -> brewDaysSinceRoast(it.roastDate, start, ZoneId.systemDefault()) })
      },
    ),
    scaleless = scaleless,
    doseG = if (!scaleless) null else if (saved != null) saved.doseG else ui.plannedDoseG,
  )
}
