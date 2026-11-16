package dev.dotscale.ui.session

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.ChartAxis
import dev.dotscale.brew.downsample
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.scale.ChartScale
import dev.dotscale.ui.scale.LineWidth
import dev.dotscale.ui.scale.PlotArea
import dev.dotscale.ui.scale.brewChartColors
import dev.dotscale.ui.scale.drawFlow
import dev.dotscale.ui.theme.AntTheme

/** Lo que abarca la minigráfica del flujo: los últimos 30 s. */
internal const val SPARKLINE_WINDOW_MS = 30_000L

/**
 * Las muestras de los últimos [windowMs] con el tiempo desplazado para que la última quede en `windowMs` (el borde
 * derecho) y la ventana empiece en 0. Las muestras inválidas se conservan: son los huecos de la línea.
 */
internal fun sparklineWindow(samples: List<BrewSample>, windowMs: Long = SPARKLINE_WINDOW_MS): List<BrewSample> {
  val last = samples.lastOrNull() ?: return emptyList()
  val start = last.tMs - windowMs
  // Las muestras van en orden de tiempo: se busca el corte en lugar de filtrar la sesión entera en cada muestra.
  val from = samples.binarySearch { it.tMs.compareTo(start) }.let { if (it >= 0) it else -it - 1 }
  return samples.subList(from, samples.size).map { it.copy(tMs = it.tMs - start) }
}

/**
 * El flujo de los últimos 30 s, sin ejes, con el mismo color por nivel de estabilidad que la gráfica de la sesión. La
 * escala vertical son los topes de [ChartAxis.flowTop] sobre la ventana, para que no salte con cada muestra.
 */
@Composable
internal fun FlowSparkline(samples: List<BrewSample>, unit: WeightUnit, modifier: Modifier = Modifier) {
  val colors = brewChartColors(AntTheme.colors)
  var widthPx by remember { mutableIntStateOf(0) }
  val window = remember(samples) { sparklineWindow(samples) }
  val points = remember(window, unit, widthPx) {
    downsample(window, widthPx, SPARKLINE_WINDOW_MS) { fromGrams(it.flowGps, unit).coerceAtLeast(0.0) }
  }
  val flowTop = remember(window, unit) {
    ChartAxis.flowTop(window.filter { it.valid }.maxOfOrNull { fromGrams(it.flowGps, unit) } ?: 0.0, unit)
  }
  Canvas(modifier.testTag("session_flow_sparkline").onSizeChanged { widthPx = it.width }) {
    val inset = LineWidth.toPx() / 2f
    val plot = PlotArea(left = inset, right = size.width - inset, top = inset, bottom = size.height - inset)
    drawFlow(plot, ChartScale(SPARKLINE_WINDOW_MS, 0.0, flowTop), points, colors)
  }
}
