package dev.josueamaya.pourline.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * La vista previa de una receta importada (SPEC-009 §3.6), de solo lectura: los avisos, los datos, la molienda, las
 * notas y los pasos con su instrucción. [onDone] cierra la pantalla (guardar, cancelar o un URI que ya no se lee).
 */
@Composable
fun RecipeImportScreen(viewModel: RecipeImportViewModel, onDone: () -> Unit, unit: WeightUnit = WeightUnit.GRAMS) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val current = ui
  LaunchedEffect(current) {
    if (current is RecipeImportUi.Saved || current is RecipeImportUi.Failed) onDone()
  }
  val context = LocalContext.current
  val texts = remember(context) { ResourcesGrindTexts(context) }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.recipe_import_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onDone)
        },
      )
    },
  ) { padding ->
    val ready = current as? RecipeImportUi.Ready
    if (ready == null) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      if (ready.shared.trimmed.isNotEmpty()) {
        AntAlert(
          message = stringResource(R.string.recipe_import_trimmed, trimmedPartsText(ready.shared.trimmed, ready.shared.partialInstructions)),
          type = AntStatus.Warning,
          modifier = Modifier.testTag("recipe_import_trimmed"),
        )
      }
      if (ready.identical) {
        AntAlert(
          message = stringResource(R.string.recipe_import_identical),
          type = AntStatus.Info,
          modifier = Modifier.testTag("recipe_import_identical"),
        )
      }
      RecipeSummary(ready.shared.recipe, unit, "recipe_import")
      GrindSection(ready.grind, ready.pick, viewModel::pick, texts, "recipe_import")
      ready.shared.recipe.notes?.let { notes ->
        Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
          AntText(stringResource(R.string.recipe_import_notes), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
          AntText(notes, Modifier.testTag("recipe_import_notes"))
        }
      }
      AntText(stringResource(R.string.recipe_edit_stages), style = AntTheme.typography.heading5)
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        ready.shared.recipe.stages.forEachIndexed { index, stage -> StageRow(stage, index, unit, "recipe_import") }
      }
      AntButton(
        text = stringResource(R.string.recipe_import_save),
        onClick = viewModel::save,
        modifier = Modifier.fillMaxWidth().testTag("recipe_import_save"),
        type = AntButtonType.Primary,
        loading = ready.saving,
        enabled = !ready.saving,
      )
      AntButton(
        text = stringResource(R.string.recipe_import_cancel),
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().testTag("recipe_import_cancel"),
      )
    }
  }
}
