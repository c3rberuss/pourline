package dev.josueamaya.pourline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme

/** Puntos de 6 dp que indican la página actual de un pager; el activo va en `primary`. */
@Composable
fun AntPageIndicator(count: Int, selected: Int, modifier: Modifier = Modifier) {
  val colors = AntTheme.colors
  Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(count) { index ->
      Box(Modifier.size(6.dp).background(if (index == selected) colors.primary.base else colors.fill, CircleShape))
    }
  }
}
