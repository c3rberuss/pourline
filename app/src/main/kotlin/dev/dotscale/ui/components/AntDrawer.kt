package dev.dotscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.ui.theme.AntTheme
import kotlinx.coroutines.launch

/**
 * `Drawer placement="bottom"` adaptado a móvil: bottom sheet modal con cabecera (título y cerrar)
 * y cuerpo con padding. [onClose] se llama cuando la hoja ya se ha ocultado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntDrawer(
  title: String,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = AntTheme.colors
  val spacing = AntTheme.spacing
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val corner = AntTheme.radius.lg * 2

  ModalBottomSheet(
    onDismissRequest = onClose,
    modifier = modifier,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = corner, topEnd = corner),
    containerColor = colors.bgElevated,
    contentColor = colors.text,
    scrimColor = colors.bgMask,
    dragHandle = {
      Box(
        Modifier
          .padding(top = spacing.xs)
          .size(width = 32.dp, height = 4.dp)
          .background(colors.fill, RoundedCornerShape(percent = 50)),
      )
    },
  ) {
    Row(
      Modifier.fillMaxWidth().padding(start = spacing.base, end = spacing.xxs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        modifier = Modifier.weight(1f).semantics { heading() },
        color = colors.text,
        style = AntTheme.typography.heading5,
      )
      AntIconButton(
        icon = AntIcons.Close,
        contentDescription = stringResource(R.string.action_close),
        onClick = {
          scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onClose() }
        },
      )
    }
    AntDivider()
    Column(
      Modifier.fillMaxWidth().padding(spacing.base),
      verticalArrangement = Arrangement.spacedBy(spacing.sm),
      content = content,
    )
  }
}
