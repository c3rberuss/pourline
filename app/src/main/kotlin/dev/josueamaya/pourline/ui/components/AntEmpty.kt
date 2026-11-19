package dev.josueamaya.pourline.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme

/** `Empty` (variante simple): ilustración tenue, descripción y acción opcional. */
@Composable
fun AntEmpty(
  description: String,
  modifier: Modifier = Modifier,
  icon: ImageVector = AntIcons.Inbox,
  action: (@Composable () -> Unit)? = null,
) {
  val colors = AntTheme.colors
  Column(
    modifier.fillMaxWidth().padding(vertical = AntTheme.spacing.xl, horizontal = AntTheme.spacing.base),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = colors.textQuaternary)
    Text(description, color = colors.textTertiary, style = AntTheme.typography.text, textAlign = TextAlign.Center)
    action?.let {
      Column(Modifier.padding(top = AntTheme.spacing.xs)) { it() }
    }
  }
}
