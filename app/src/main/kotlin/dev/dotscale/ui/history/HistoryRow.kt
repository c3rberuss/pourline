package dev.dotscale.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.brew.fromGrams
import dev.dotscale.history.BrewListItem
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntCardSize
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.scale.formatDuration
import dev.dotscale.ui.scale.formatNumber
import dev.dotscale.ui.scale.levelText
import dev.dotscale.ui.scale.tagColor
import dev.dotscale.ui.theme.AntTheme

private const val DISABLED_ALPHA = 0.45f

/** Fila del historial: minicurva, título, café, agua y tiempo en la unidad guardada, ratio y etiquetas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryRow(item: BrewListItem, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val state = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA).semantics { disabled() }
  AntCard(modifier.testTag("history_row").then(state), size = AntCardSize.Small, onClick = if (enabled) onClick else null) {
    Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm), verticalAlignment = Alignment.Top) {
      BrewPreviewLine(item.preview, Modifier.width(64.dp).height(40.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(
          text = item.name ?: formatBrewDateTime(item.startedAtEpochMs),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (item.name != null) {
          AntText(formatBrewDateTime(item.startedAtEpochMs), type = AntTextType.Secondary, maxLines = 1)
        }
        item.coffee?.let { AntText(it, type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        val water = "${formatNumber(fromGrams(item.totalWaterG, item.unit), item.unit)} ${item.unit.symbol}"
        val ratio = item.finalRatio?.let { " · " + stringResource(R.string.scale_ratio_value, it) }.orEmpty()
        AntText(
          text = stringResource(R.string.history_row_stats, water, formatDuration(item.totalTimeMs)) + ratio,
          type = AntTextType.Secondary,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
          verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
          modifier = Modifier.fillMaxWidth(),
        ) {
          val score = item.stabilityScore
          val level = item.stabilityLevel
          if (score != null && level != null) {
            AntTag(levelText(level, score), color = level.tagColor())
          } else {
            AntTag(stringResource(R.string.history_row_no_score))
          }
          item.fitScore?.let { AntTag(stringResource(R.string.history_row_fit, it), Modifier.testTag("history_row_fit")) }
          if (item.simulated) AntTag(stringResource(R.string.history_row_simulated), color = AntTagColor.Primary)
        }
      }
    }
  }
}

/** Minicurva decorativa: el agua acumulada como una línea sin ejes. Sin semántica, la fila ya lo dice en texto. */
@Composable
private fun BrewPreviewLine(values: List<Float>, modifier: Modifier = Modifier) {
  val color = AntTheme.colors.primary.base
  Canvas(modifier) {
    if (values.size < 2) return@Canvas
    val inset = 2.dp.toPx()
    val width = size.width - 2 * inset
    val height = size.height - 2 * inset
    val path = Path()
    values.forEachIndexed { i, value ->
      val x = inset + width * i / (values.size - 1)
      val y = inset + height * (1f - value.coerceIn(0f, 1f))
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawLine(color.copy(alpha = 0.2f), Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), strokeWidth = 1.dp.toPx())
  }
}
