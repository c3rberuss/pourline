package dev.dotscale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.dotscale.ui.theme.AntTheme

/**
 * `Form.Item` de Ant Design: etiqueta (con asterisco si es [required] y [extra] a la derecha), el contenido
 * y debajo el [error] o, si no hay, la [help].
 *
 * @param errorTag `testTag` del texto de error.
 */
@Composable
fun AntFormItem(
  label: String,
  modifier: Modifier = Modifier,
  required: Boolean = false,
  error: String? = null,
  help: String? = null,
  errorTag: String? = null,
  extra: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  val colors = AntTheme.colors
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
      Text(
        buildAnnotatedString {
          append(label)
          if (required) withStyle(SpanStyle(color = colors.error.text)) { append(" *") }
        },
        modifier = Modifier.weight(1f),
        color = colors.textSecondary,
        style = AntTheme.typography.textSM,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      extra?.invoke(this)
    }
    content()
    when {
      error != null -> AntText(
        error,
        modifier = if (errorTag != null) Modifier.testTag(errorTag) else Modifier,
        type = AntTextType.Danger,
        style = AntTheme.typography.textSM,
      )
      help != null -> AntText(help, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    }
  }
}
