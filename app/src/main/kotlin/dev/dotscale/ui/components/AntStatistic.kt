package dev.dotscale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.dotscale.ui.theme.AntTheme

/**
 * `Statistic` de Ant Design: título pequeño y valor grande con cifras tabulares (no "bailan" al cambiar),
 * con prefijo/sufijo opcionales alineados por la línea base. TalkBack lo lee como un único elemento.
 */
@Composable
fun AntStatistic(
  value: String,
  modifier: Modifier = Modifier,
  title: String? = null,
  prefix: String? = null,
  suffix: String? = null,
  valueStyle: TextStyle = AntTheme.typography.heading3.copy(fontWeight = FontWeight.Normal),
  valueColor: Color = AntTheme.colors.text,
  affixStyle: TextStyle = AntTheme.typography.textLG,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  titleExtra: (@Composable () -> Unit)? = null,
) {
  val colors = AntTheme.colors
  val spacing = AntTheme.spacing
  Column(
    modifier.semantics(mergeDescendants = true) {},
    horizontalAlignment = horizontalAlignment,
    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
  ) {
    if (title != null || titleExtra != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        title?.let { Text(it, color = colors.textTertiary, style = AntTheme.typography.text) }
        titleExtra?.invoke()
      }
    }
    Row {
      prefix?.let {
        Text(it, Modifier.alignByBaseline().padding(end = spacing.xxs), color = valueColor, style = affixStyle)
      }
      Text(
        text = value,
        modifier = Modifier.alignByBaseline(),
        color = valueColor,
        style = valueStyle.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
      )
      suffix?.let {
        Text(it, Modifier.alignByBaseline().padding(start = spacing.xxs), color = colors.textSecondary, style = affixStyle)
      }
    }
  }
}
