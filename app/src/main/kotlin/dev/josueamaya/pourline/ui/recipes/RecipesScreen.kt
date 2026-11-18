package dev.josueamaya.pourline.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.tour.TourHost
import dev.josueamaya.pourline.ui.tour.TourLazyAnchor
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor

@Composable
fun RecipesScreen(
  viewModel: RecipesViewModel,
  onBack: (() -> Unit)?,
  onOpenRecipe: (Long) -> Unit,
  onCreateRecipe: () -> Unit,
  /** La receta elegida en Preparar, que la lista marca como "Activa"; `null` sin receta elegida. */
  activeRecipeId: Long? = null,
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

  val listState = rememberLazyListState()
  // La lista perezosa solo compone lo que se ve: estos pasos se declaran para que el recorrido los busque desplazando.
  val firstBuiltIn = items?.firstOrNull { it.builtIn }?.id
  TourLazyAnchor("recipe_builtin_tag", listState, present = firstBuiltIn != null)
  TourLazyAnchor("recipe_active_tag", listState, present = activeRecipeId != null && items?.any { it.id == activeRecipeId } == true)
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.recipe_title),
        navigationIcon = onBack?.let { back ->
          {
            AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = back)
          }
        },
        actions = {
          AntIconButton(
            AntIcons.Plus,
            contentDescription = stringResource(R.string.recipe_add),
            onClick = onCreateRecipe,
            modifier = Modifier.tourAnchor("recipes_add").testTag("recipes_add"),
          )
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding),
      state = listState,
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
          items(rows, key = { it.id }) { item ->
            val active = item.id == activeRecipeId
            RecipeRow(
              item,
              onClick = { onOpenRecipe(item.id) },
              active = active,
              builtInAnchor = item.id == firstBuiltIn,
              activeAnchor = active,
            )
          }
        }
      }
    }
  }
  TourHost(Tours.recipes, ready = items != null)
}
