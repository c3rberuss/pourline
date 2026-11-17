package dev.josueamaya.pourline.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.theme.AntTheme

/** Raíz de la pestaña Herramientas: la calculadora de ratio, el generador 4:6 y el convertidor de molienda. */
@Composable
fun ToolsScreen(
  onOpenRatio: () -> Unit,
  onOpenFourSix: () -> Unit,
  onOpenConverter: () -> Unit,
) {
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = { AntTopBar(title = stringResource(R.string.tools_title)) },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base).testTag("tools"),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      AntCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        ToolRow(AntIcons.Calculator, R.string.tools_ratio_title, R.string.tools_ratio_description, "tools_ratio", onOpenRatio)
        ToolRow(AntIcons.Dripper, R.string.tools_four_six_title, R.string.tools_four_six_description, "tools_four_six", onOpenFourSix)
        ToolRow(
          AntIcons.Convert,
          R.string.tools_converter_title,
          R.string.tools_converter_description,
          "tools_converter",
          onOpenConverter,
          divider = false,
        )
      }
    }
  }
}

@Composable
private fun ToolRow(icon: ImageVector, title: Int, description: Int, tag: String, onClick: () -> Unit, divider: Boolean = true) {
  AntListItem(
    title = stringResource(title),
    description = stringResource(description),
    modifier = Modifier.testTag(tag),
    avatar = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = AntTheme.colors.textSecondary) },
    extra = { Icon(AntIcons.Right, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
    divider = divider,
    onClick = onClick,
  )
}
