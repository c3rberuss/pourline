package dev.dotscale.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.grinder.GrindTexts
import dev.dotscale.grinder.Grinder
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntListItem
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntTheme

/** "Los nombres de las unidades de la jerarquía: Rotaciones · Números · Clics". */
fun Grinder.unitsSummary(texts: GrindTexts): String = units.joinToString(" · ") { texts.unitName(it.unit) }

/** Ajustes → Molinos: mis molinos, con el predeterminado marcado (D28, D31). */
@Composable
fun GrindersScreen(
  viewModel: GrindersViewModel,
  onBack: () -> Unit,
  onOpenGrinder: (Long) -> Unit,
  onAddGrinder: () -> Unit,
  onOpenConverter: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val texts = rememberGrindTexts()
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.grinders_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
        actions = {
          AntIconButton(
            AntIcons.Convert,
            contentDescription = stringResource(R.string.converter_title),
            onClick = onOpenConverter,
            modifier = Modifier.testTag("grinders_converter"),
          )
        },
      )
    },
  ) { padding ->
    val current = state ?: return@Scaffold
    LazyColumn(
      Modifier.fillMaxSize().padding(padding).testTag("grinders_list"),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      if (current.owned.isEmpty()) {
        item(key = "empty") {
          AntEmpty(
            stringResource(R.string.grinders_empty),
            modifier = Modifier.testTag("grinders_empty"),
            icon = AntIcons.Grinder,
            action = { AddButton(onAddGrinder) },
          )
        }
      } else {
        items(current.owned, key = { it.id }) { grinder ->
          AntListItem(
            title = grinder.displayName,
            description = grinder.unitsSummary(texts),
            modifier = Modifier.testTag("grinder_row_${grinder.id}"),
            extra = {
              Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (grinder.id == current.defaultId) {
                  AntTag(stringResource(R.string.grinder_tag_default), Modifier.testTag("grinder_default_${grinder.id}"), color = AntTagColor.Primary)
                }
                if (!grinder.isPredefined) AntTag(stringResource(R.string.grinder_tag_own))
                if (grinder.retired) AntTag(stringResource(R.string.grinder_tag_retired), color = AntTagColor.Warning)
                Icon(AntIcons.Right, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary)
              }
            },
            onClick = { onOpenGrinder(grinder.id) },
          )
        }
        item(key = "add") {
          Row(Modifier.fillMaxWidth().padding(horizontal = AntTheme.spacing.base)) { AddButton(onAddGrinder, Modifier.fillMaxWidth()) }
        }
      }
    }
  }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  AntButton(
    text = stringResource(R.string.grinders_add),
    onClick = onClick,
    modifier = modifier.testTag("grinders_add"),
    type = AntButtonType.Dashed,
    icon = AntIcons.Plus,
  )
}
