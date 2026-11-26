package dev.josueamaya.pourline.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntSegmented
import dev.josueamaya.pourline.ui.components.AntSegmentedOption
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRecipeList
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
  /** Las entradas de "Importar" (escáner y galería); sin él, la barra no muestra el icono. */
  importer: RecipeImportEntryViewModel? = null,
  /** La receta recomendada elegida en Preparar, que su lista marca como "Activa". */
  activeCatalogKey: String? = null,
  /** Abre el detalle de una receta recomendada (por su clave). */
  onOpenCatalogRecipe: (String) -> Unit = {},
) {
  val segment by viewModel.segment.collectAsStateWithLifecycle()
  val catalogUi by viewModel.catalog.ui.collectAsStateWithLifecycle()
  // Al mostrar las recomendadas se descarga si toca; también tras recrear la pantalla (otro idioma).
  LaunchedEffect(segment) { if (segment == RecipesSegment.CATALOG) viewModel.catalog.onShown() }
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
  TourLazyAnchor(
    "recipe_active_tag",
    listState,
    present = segment == RecipesSegment.MINE && activeRecipeId != null && items?.any { it.id == activeRecipeId } == true,
  )
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
          if (importer != null) ImportAction(importer)
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
    Column(Modifier.fillMaxSize().padding(padding)) {
      AntSegmented(
        options = listOf(
          AntSegmentedOption(RecipesSegment.MINE, stringResource(R.string.recipes_segment_mine)),
          AntSegmentedOption(RecipesSegment.CATALOG, stringResource(R.string.recipes_segment_catalog)),
        ),
        selected = segment,
        onSelect = viewModel::selectSegment,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = AntTheme.spacing.base)
          .padding(top = AntTheme.spacing.base, bottom = AntTheme.spacing.xs),
        itemModifier = { option ->
          when (option) {
            RecipesSegment.MINE -> Modifier.testTag("recipes_segment_mine")
            RecipesSegment.CATALOG -> Modifier.tourAnchor("recipes_catalog_segment").testTag("recipes_segment_catalog")
          }
        },
      )
      when (segment) {
        RecipesSegment.MINE -> MyRecipes(
          items = items,
          listState = listState,
          activeRecipeId = activeRecipeId,
          onOpenRecipe = onOpenRecipe,
          onCreateRecipe = onCreateRecipe,
          onSeeCatalog = { viewModel.selectSegment(RecipesSegment.CATALOG) },
        )
        RecipesSegment.CATALOG -> CatalogRecipeList(catalogUi, viewModel.catalog, activeCatalogKey, onOpenCatalogRecipe)
      }
    }
  }
  TourHost(Tours.recipes, ready = items != null)
}

/** "Mis recetas": la lista del usuario y, sin recetas, el estado vacío con "Crear receta" y "Ver recomendadas". */
@Composable
private fun MyRecipes(
  items: List<RecipeListItem>?,
  listState: LazyListState,
  activeRecipeId: Long?,
  onOpenRecipe: (Long) -> Unit,
  onCreateRecipe: () -> Unit,
  onSeeCatalog: () -> Unit,
) {
  LazyColumn(
    Modifier.fillMaxSize(),
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
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
                AntButton(
                  text = stringResource(R.string.recipe_add),
                  onClick = onCreateRecipe,
                  modifier = Modifier.testTag("recipes_empty_add"),
                  type = AntButtonType.Primary,
                )
                AntButton(
                  text = stringResource(R.string.recipes_empty_see_catalog),
                  onClick = onSeeCatalog,
                  modifier = Modifier.testTag("recipes_empty_see_catalog"),
                  type = AntButtonType.Link,
                )
              }
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
            activeAnchor = active,
          )
        }
      }
    }
  }
}

/** El icono "Importar" y su menú: escanear un QR con la cámara o leerlo de una imagen de la galería (SPEC-009 §3.5). */
@Composable
private fun ImportAction(importer: RecipeImportEntryViewModel) {
  val busy by importer.busy.collectAsStateWithLifecycle()
  val scanner = rememberQrScanner()
  val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> importer.onImage(uri) }
  var menu by remember { mutableStateOf(false) }
  Box {
    AntIconButton(
      AntIcons.ScanQr,
      contentDescription = stringResource(R.string.recipe_import_action),
      onClick = { menu = true },
      modifier = Modifier.testTag("recipes_import"),
      loading = busy,
    )
    DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = AntTheme.colors.bgElevated) {
      DropdownMenuItem(
        text = { androidx.compose.material3.Text(stringResource(R.string.recipe_import_scan), color = AntTheme.colors.text) },
        onClick = {
          menu = false
          importer.scan(scanner)
        },
        modifier = Modifier.testTag("recipes_import_scan"),
      )
      DropdownMenuItem(
        text = { androidx.compose.material3.Text(stringResource(R.string.recipe_import_from_image), color = AntTheme.colors.text) },
        onClick = {
          menu = false
          picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        },
        modifier = Modifier.testTag("recipes_import_image"),
      )
    }
  }
}
