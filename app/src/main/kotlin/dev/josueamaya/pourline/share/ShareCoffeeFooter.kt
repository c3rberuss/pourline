package dev.josueamaya.pourline.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.history.compareColors
import dev.josueamaya.pourline.ui.theme.AntTheme

private val IconSize = 20.dp
private val DotSize = 12.dp

/** "día 9", "días 9 y 11" o `null` sin días. */
@Composable
private fun daysText(days: List<Int>): String? = when (days.size) {
  0 -> null
  1 -> stringResource(R.string.share_days_one, days[0])
  else -> stringResource(R.string.share_days_two, days[0], days[1])
}

/**
 * El pie con el café vinculado (§3.7). Una línea sin lado, o con lado y sola, es la forma normal: icono (o punto de
 * su color en la comparación), nombre y "Tostador · día N" debajo. Con dos líneas (dos cafés distintos en la
 * comparación) cada una va compacta, con su punto: "nombre · tostador · día N".
 */
@Composable
fun ShareCoffeeFooter(lines: List<ShareFooterLine>, modifier: Modifier = Modifier) {
  if (lines.isEmpty()) return
  Column(modifier.fillMaxWidth().testTag("share_footer"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (lines.size == 1) NormalLine(lines[0]) else lines.forEach { CompactLine(it) }
  }
}

@Composable
private fun sideColor(side: ShareSide): Color {
  val colors = compareColors(AntTheme.colors)
  return if (side == ShareSide.A) colors.a else colors.b
}

@Composable
private fun NormalLine(line: ShareFooterLine) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    val side = line.side
    if (side == null) {
      Icon(AntIcons.Coffee, contentDescription = null, modifier = Modifier.size(IconSize), tint = AntTheme.colors.primary.base)
    } else {
      Box(Modifier.size(DotSize).background(sideColor(side), CircleShape))
    }
    Column(Modifier.weight(1f)) {
      AntText(line.name, Modifier.testTag("share_footer_name"), strong = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val detail = listOfNotNull(line.roaster, daysText(line.days)).joinToString(" · ")
      if (detail.isNotEmpty()) {
        AntText(detail, type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun CompactLine(line: ShareFooterLine) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    line.side?.let { Box(Modifier.size(DotSize).background(sideColor(it), CircleShape)) }
    val rest = listOfNotNull(line.roaster, daysText(line.days)).joinToString(" · ")
    val text = buildAnnotatedString {
      withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = AntTheme.colors.text)) { append(line.name) }
      if (rest.isNotEmpty()) withStyle(SpanStyle(color = AntTheme.colors.textTertiary)) { append(" · $rest") }
    }
    Text(text, Modifier.weight(1f), style = AntTheme.typography.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
