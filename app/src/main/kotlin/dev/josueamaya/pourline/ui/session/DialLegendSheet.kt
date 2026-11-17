package dev.josueamaya.pourline.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.ui.components.AntDrawer
import dev.josueamaya.pourline.ui.components.AntProgressCircle
import dev.josueamaya.pourline.ui.components.AntProgressStop
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.brewChartColors
import dev.josueamaya.pourline.ui.scale.levelLabel
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.sin

/**
 * La leyenda del dial de Experto (el botón ⓘ de las métricas): qué es el aro, qué significa cada color, qué es la
 * marca y qué es la minigráfica. Los dibujos son el mismo aro y la misma minigráfica de la sesión, en pequeño.
 */
@Composable
internal fun DialLegendSheet(onClose: () -> Unit) {
  val chart = brewChartColors(AntTheme.colors)
  val primary = AntTheme.colors.primary.base
  AntDrawer(
    title = stringResource(R.string.session_dial_legend),
    onClose = onClose,
    modifier = Modifier.testTag("session_dial_legend_sheet"),
  ) {
    LegendRow(stringResource(R.string.session_dial_legend_ring)) {
      AntProgressCircle(
        fraction = 0.7f,
        modifier = Modifier.size(LegendArt),
        strokeWidth = LegendStroke,
        segments = listOf(0.3f),
      )
    }
    LegendRow(stringResource(R.string.session_dial_legend_colors)) {
      AntProgressCircle(
        fraction = 0.8f,
        modifier = Modifier.size(LegendArt),
        strokeWidth = LegendStroke,
        stops = listOf(
          AntProgressStop(0.1f, primary),
          AntProgressStop(0.45f, chart.stable),
          AntProgressStop(0.65f, chart.irregular),
          AntProgressStop(0.8f, chart.turbulent),
        ),
      )
    }
    Column(
      Modifier.fillMaxWidth().padding(start = LegendArt + AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    ) {
      Swatch(chart.stable, levelLabel(StabilityLevel.STABLE))
      Swatch(chart.irregular, levelLabel(StabilityLevel.IRREGULAR))
      Swatch(chart.turbulent, levelLabel(StabilityLevel.TURBULENT))
      Swatch(primary, stringResource(R.string.session_dial_legend_measuring))
    }
    LegendRow(stringResource(R.string.session_dial_legend_marker)) {
      AntProgressCircle(
        fraction = 0.55f,
        modifier = Modifier.size(LegendArt),
        strokeWidth = LegendStroke,
        stops = listOf(AntProgressStop(0.55f, chart.stable)),
        marker = 0.7f,
      )
    }
    LegendRow(stringResource(R.string.session_dial_legend_sparkline)) {
      val samples = remember { exampleFlow() }
      FlowSparkline(samples, WeightUnit.GRAMS, Modifier.width(LegendArt).height(LegendStroke * 4))
    }
  }
}

@Composable
private fun LegendRow(text: String, art: @Composable () -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(LegendArt), contentAlignment = Alignment.Center) { art() }
    AntText(text, Modifier.weight(1f))
  }
}

@Composable
private fun Swatch(color: Color, label: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
    AntText(label, type = AntTextType.Secondary)
  }
}

/** 30 s de ejemplo: 12 s de espera sin flujo y luego un vertido estable alrededor de 5 g/s. */
private fun exampleFlow(): List<BrewSample> = (0..60).map { i ->
  val tMs = i * 500L
  val pouring = tMs >= 12_000
  val flow = if (pouring) 5.0 + 0.2 * sin(i * 0.7) else 0.0
  BrewSample(tMs, waterG = 0.0, flowGps = flow, stability = if (pouring) 90 else null, valid = true)
}

private val LegendArt = 48.dp
private val LegendStroke = 6.dp
