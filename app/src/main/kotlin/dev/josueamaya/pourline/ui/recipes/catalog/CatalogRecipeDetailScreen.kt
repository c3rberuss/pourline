package dev.josueamaya.pourline.ui.recipes.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.recipes.GrindSection
import dev.josueamaya.pourline.ui.recipes.RecipeCurvePreview
import dev.josueamaya.pourline.ui.recipes.RecipeSummary
import dev.josueamaya.pourline.ui.recipes.StageRow
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlinx.coroutines.launch

private const val TAG = "catalog_detail"

/**
 * El detalle de una receta recomendada (SPEC-012 §3.8), de solo lectura: datos, molienda, notas y pasos, "Ver fuente", la
 * curva en Experto y los botones "Preparar" (sin guardar) y "Guardar en mis recetas". [openUrl] abre la fuente y devuelve
 * `false` si no hay navegador; los tests pasan el suyo.
 */
@Composable
fun CatalogRecipeDetailScreen(
  viewModel: CatalogRecipeDetailViewModel,
  onBack: () -> Unit,
  onPrepare: (String) -> Unit,
  openUrl: (String) -> Boolean,
  unit: WeightUnit = WeightUnit.GRAMS,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val texts = remember(context) { ResourcesGrindTexts(context) }
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val savedText = stringResource(R.string.catalog_detail_saved)
  val noBrowserText = stringResource(R.string.catalog_no_browser)
  LaunchedEffect(viewModel) {
    viewModel.saved.collect { snackbar.showAntMessage(savedText, AntStatus.Success) }
  }

  val ready = ui as? CatalogDetailUi.Ready
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = ready?.display?.name ?: stringResource(R.string.recipe_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    when (val current = ui) {
      CatalogDetailUi.Loading -> AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      CatalogDetailUi.Missing -> AntEmpty(
        stringResource(R.string.catalog_detail_missing),
        Modifier.padding(padding).testTag("${TAG}_missing"),
        icon = AntIcons.Recipe,
      )
      is CatalogDetailUi.Ready -> Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
      ) {
        val recipe = current.display
        RecipeSummary(recipe, unit, TAG)
        current.recipe.author?.let { AntText(stringResource(R.string.catalog_detail_author, it), Modifier.testTag("${TAG}_author"), type = AntTextType.Secondary) }
        current.recipe.context?.let { AntText(it, Modifier.testTag("${TAG}_context"), type = AntTextType.Secondary) }
        if (current.alreadySaved) {
          AntAlert(
            message = stringResource(R.string.catalog_detail_already_saved),
            type = AntStatus.Info,
            modifier = Modifier.fillMaxWidth().testTag("${TAG}_already_saved"),
          )
        }
        GrindSection(current.resolution, current.pick, viewModel::pick, texts, TAG)
        recipe.notes?.let { notes ->
          Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
            AntText(stringResource(R.string.recipe_import_notes), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
            AntText(notes, Modifier.testTag("${TAG}_notes"))
          }
        }
        if (LocalExperienceMode.current.shows(ExpertFeature.RECIPE_CURVE_PREVIEW)) {
          RecipeCurvePreview(recipe, testTag = "${TAG}_preview")
        }
        AntText(stringResource(R.string.recipe_edit_stages), style = AntTheme.typography.heading5)
        Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          recipe.stages.forEachIndexed { index, stage -> StageRow(stage, index, unit, TAG) }
        }
        current.recipe.sourceUrl?.let { url ->
          AntButton(
            text = stringResource(R.string.catalog_detail_source),
            onClick = { if (!openUrl(url)) scope.launch { snackbar.showAntMessage(noBrowserText, AntStatus.Warning) } },
            modifier = Modifier.testTag("${TAG}_source"),
            type = AntButtonType.Link,
          )
        }
        AntButton(
          text = stringResource(R.string.catalog_detail_prepare),
          onClick = { onPrepare(current.recipe.key) },
          modifier = Modifier.fillMaxWidth().testTag("${TAG}_prepare"),
          type = AntButtonType.Primary,
        )
        AntButton(
          text = stringResource(R.string.catalog_detail_save),
          onClick = viewModel::save,
          modifier = Modifier.fillMaxWidth().testTag("${TAG}_save"),
          loading = current.saving,
          enabled = !current.saving,
        )
      }
    }
  }
}
