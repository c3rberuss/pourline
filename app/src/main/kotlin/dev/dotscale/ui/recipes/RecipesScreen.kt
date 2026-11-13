package dev.dotscale.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.components.showAntMessage
import dev.dotscale.ui.theme.AntTheme

@Composable
fun RecipesScreen(
  viewModel: RecipesViewModel,
  onBack: () -> Unit,
  onOpenRecipe: (Long) -> Unit,
  onCreateRecipe: () -> Unit,
) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }

  val deletedText = stringResource(R.string.recipe_deleted)
  val undoLabel = stringResource(R.string.recipe_undo)
  LaunchedEffect(pendingUndo) {
    val deleted = pendingUndo ?: return@LaunchedEffect
    val result = snackbar.showAntMessage(deletedText, AntStatus.Success, actionLabel = undoLabel)
    if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted) else viewModel.clearPendingUndo(deleted)
  }
  // Salir de la pantalla vacía el deshacer.
  DisposableEffect(viewModel) { onDispose { viewModel.clearPendingUndo() } }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.recipe_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
        actions = {
          AntIconButton(
            AntIcons.Plus,
            contentDescription = stringResource(R.string.recipe_add),
            onClick = onCreateRecipe,
            modifier = Modifier.testTag("recipes_add"),
          )
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      val rows = items
      if (rows != null) {
        if (rows.isEmpty()) {
          item(key = "empty") {
            AntEmpty(
              stringResource(R.string.recipe_empty),
              icon = AntIcons.Recipe,
              action = {
                AntButton(
                  text = stringResource(R.string.recipe_add),
                  onClick = onCreateRecipe,
                  modifier = Modifier.testTag("recipes_empty_add"),
                  type = AntButtonType.Primary,
                )
              },
            )
          }
        } else {
          items(rows, key = { it.id }) { item -> RecipeRow(item, onClick = { onOpenRecipe(item.id) }) }
        }
      }
    }
  }
}
