package dev.dotscale.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.brew.BrewConfig
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRecord
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.scale.BrewSummaryCard
import dev.dotscale.ui.scale.FitBlock
import dev.dotscale.ui.scale.fitView
import dev.dotscale.ui.scale.Chart
import dev.dotscale.ui.scale.summaryStability
import dev.dotscale.ui.theme.AntTheme
import java.util.Locale

@Composable
fun BrewDetailScreen(
  viewModel: BrewDetailViewModel,
  onBack: () -> Unit,
  onEdit: (Long) -> Unit,
  onCompare: (Long) -> Unit,
  onSaveAsRecipe: (Long) -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state is BrewDetailState.Gone) onBack() }

  val record = (state as? BrewDetailState.Loaded)?.record
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = record?.entity?.let { it.name ?: formatBrewDateTime(it.startedAtEpochMs) } ?: stringResource(R.string.history_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    if (record == null) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
    } else {
      Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
      ) {
        DetailContent(
          record,
          onEdit = { onEdit(record.entity.id) },
          onDelete = viewModel::delete,
          onCompare = { onCompare(record.entity.id) },
          onSaveAsRecipe = { onSaveAsRecipe(record.entity.id) },
        )
      }
    }
  }
}

@Composable
private fun DetailContent(
  record: BrewRecord,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onCompare: () -> Unit,
  onSaveAsRecipe: () -> Unit,
) {
  val entity = record.entity
  if (entity.simulated) {
    Row { AntTag(stringResource(R.string.history_row_simulated), color = AntTagColor.Primary) }
  }
  if (record.curveError) {
    AntAlert(message = stringResource(R.string.brew_detail_curve_error), type = AntStatus.Error)
  } else {
    AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.brew_chart_title)) {
      Chart(record.samples, entity.unit, summaryStability(record.summary), record.target)
    }
  }
  BrewSummaryCard(record.summary, entity.unit)
  if (record.targetError) {
    AntAlert(message = stringResource(R.string.brew_detail_target_error), type = AntStatus.Warning, modifier = Modifier.testTag("brew_detail_target_error"))
  }
  FitCard(record)
  if (entity.metricVersion != BrewConfig.METRIC_VERSION) {
    AntText(stringResource(R.string.brew_detail_old_metric), type = AntTextType.Secondary)
  }
  DetailsCard(record)
  // Dos filas: cuatro botones no caben en una de 360 dp.
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    AntButton(
      text = stringResource(R.string.brew_detail_edit),
      onClick = onEdit,
      modifier = Modifier.weight(1f).testTag("brew_detail_edit"),
    )
    AntButton(
      text = stringResource(R.string.brew_detail_delete),
      onClick = onDelete,
      modifier = Modifier.weight(1f).testTag("brew_detail_delete"),
      type = AntButtonType.Primary,
      danger = true,
    )
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    AntButton(
      text = stringResource(R.string.brew_detail_compare),
      onClick = onCompare,
      modifier = Modifier.weight(1f).testTag("brew_detail_compare"),
    )
    AntButton(
      text = stringResource(R.string.brew_detail_save_recipe),
      onClick = onSaveAsRecipe,
      modifier = Modifier.weight(1f).testTag("brew_detail_save_recipe"),
      enabled = !record.curveError,
    )
  }
}

/** "Ajuste al objetivo" con las columnas guardadas, así que es el mismo que mostró el resumen al terminar. */
@Composable
private fun FitCard(record: BrewRecord) {
  val entity = record.entity
  if (entity.fitScore == null && entity.referenceType == null) return
  val fit = fitView(
    score = entity.fitScore,
    referenceName = entity.referenceName,
    targetWaterG = entity.targetWaterG ?: return,
    targetEndMs = entity.targetEndMs ?: return,
    totalWaterG = entity.totalWaterG,
    totalTimeMs = entity.totalTimeMs,
  ) ?: return
  AntCard(Modifier.fillMaxWidth().testTag("brew_detail_fit")) { FitBlock(fit, entity.unit) }
}

/** Los campos manuales; los vacíos se omiten y, si no hay ninguno, la tarjeta no se muestra. */
@Composable
private fun DetailsCard(record: BrewRecord) {
  val entity = record.entity
  val method = entity.method?.let { m -> if (m == BrewMethod.OTHER) entity.methodOther ?: m.label() else m.label() }
  val fields = listOfNotNull(
    entity.coffee?.let { stringResource(R.string.brew_detail_coffee) to it },
    method?.let { stringResource(R.string.brew_detail_method) to it },
    entity.grind?.let { stringResource(R.string.brew_detail_grind) to it },
    entity.waterTempC?.let {
      stringResource(R.string.brew_detail_temp) to stringResource(R.string.brew_detail_temp_value, formatTemp(it))
    },
    entity.notes?.let { stringResource(R.string.brew_detail_notes) to it },
  )
  if (fields.isEmpty()) return
  AntCard(Modifier.fillMaxWidth().testTag("brew_detail_details"), title = stringResource(R.string.brew_detail_details)) {
    fields.forEach { (label, value) ->
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(label, type = AntTextType.Secondary)
        AntText(value)
      }
    }
  }
}

private fun formatTemp(celsius: Double): String =
  if (celsius % 1.0 == 0.0) celsius.toLong().toString() else String.format(Locale.getDefault(), "%.1f", celsius)
