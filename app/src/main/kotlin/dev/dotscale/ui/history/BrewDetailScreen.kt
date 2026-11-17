package dev.dotscale.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.brew.BrewConfig
import dev.dotscale.coffee.CoffeeEntity
import dev.dotscale.coffee.brewDaysSinceRoast
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.toGrindSetting
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRecord
import dev.dotscale.settings.ExpertFeature
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.share.BrewShareData
import dev.dotscale.share.FooterCoffee
import dev.dotscale.share.ShareContent
import dev.dotscale.share.ShareSender
import dev.dotscale.share.ShareTarget
import dev.dotscale.share.footerLines
import dev.dotscale.share.rememberShareLauncher
import dev.dotscale.share.rememberShareSender
import dev.dotscale.ui.coffees.CoffeeThumbnail
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntRate
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.grinders.GrindSettingText
import dev.dotscale.ui.scale.BrewSummaryCard
import dev.dotscale.ui.scale.Chart
import dev.dotscale.ui.scale.FitBlock
import dev.dotscale.ui.scale.fitView
import dev.dotscale.ui.scale.summaryStability
import dev.dotscale.ui.theme.AntTheme
import java.time.ZoneId
import java.util.Locale

@Composable
fun BrewDetailScreen(
  viewModel: BrewDetailViewModel,
  onBack: () -> Unit,
  onEdit: (Long) -> Unit,
  onCompare: (Long) -> Unit,
  onSaveAsRecipe: (Long) -> Unit,
  onOpenCoffee: (Long) -> Unit = {},
  shareSender: ShareSender? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state is BrewDetailState.Gone) onBack() }
  val snackbar = remember { SnackbarHostState() }
  val launcher = rememberShareLauncher(shareSender ?: rememberShareSender(), snackbar)

  val loaded = state as? BrewDetailState.Loaded
  val record = loaded?.record
  val shareData = loaded?.let { detailShareData(it.record, it.coffee) }
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = record?.entity?.let { it.name ?: formatBrewDateTime(it.startedAtEpochMs) } ?: stringResource(R.string.history_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
        actions = {
          if (record != null && shareData != null) {
            AntIconButton(
              AntIcons.Share,
              contentDescription = stringResource(R.string.share_image_action),
              onClick = { launcher.share(ShareTarget.Brew(record.entity.id), ShareContent.Brew(shareData)) },
              modifier = Modifier.testTag("brew_detail_share"),
            )
          }
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    if (loaded == null) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
    } else {
      Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
      ) {
        val record = loaded.record
        DetailContent(
          record,
          coffee = loaded.coffee,
          grinder = loaded.grinder,
          onOpenCoffee = onOpenCoffee,
          onEdit = { onEdit(record.entity.id) },
          onDelete = viewModel::delete,
          onCompare = { onCompare(record.entity.id) },
          onSaveAsRecipe = { onSaveAsRecipe(record.entity.id) },
        )
      }
    }
  }
  launcher.Host()
}

/** La imagen del detalle: título, método, ajuste, valoración, nota de cata y el café vinculado con los días de la preparación. */
@Composable
private fun detailShareData(record: BrewRecord, coffee: CoffeeEntity?): BrewShareData {
  val entity = record.entity
  val started = formatBrewDateTime(entity.startedAtEpochMs)
  val method = entity.method?.let { m -> if (m == BrewMethod.OTHER) entity.methodOther ?: m.label() else m.label() }
  val linked = if (entity.coffeeId != null) coffee else null
  return BrewShareData(
    samples = record.samples,
    summary = record.summary,
    unit = entity.unit,
    target = record.target,
    title = entity.name ?: started,
    subtitle = listOfNotNull(started.takeIf { entity.name != null }, method).joinToString(" · ").ifEmpty { null },
    fitScore = entity.fitScore,
    rating = entity.rating,
    notes = entity.notes,
    footer = footerLines(
      linked?.let { FooterCoffee(it.id, it.name, it.roaster, brewDaysSinceRoast(it.roastDate, entity.startedAtEpochMs, ZoneId.systemDefault())) },
    ),
    curveError = record.curveError,
  )
}

@Composable
private fun DetailContent(
  record: BrewRecord,
  coffee: CoffeeEntity?,
  grinder: Grinder?,
  onOpenCoffee: (Long) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onCompare: () -> Unit,
  onSaveAsRecipe: () -> Unit,
) {
  val entity = record.entity
  val mode = LocalExperienceMode.current
  if (entity.simulated) {
    Row { AntTag(stringResource(R.string.history_row_simulated), color = AntTagColor.Primary) }
  }
  if (mode.shows(ExpertFeature.BREW_DETAIL_CHART)) {
    if (record.curveError) {
      AntAlert(message = stringResource(R.string.brew_detail_curve_error), type = AntStatus.Error)
    } else {
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.brew_chart_title)) {
        Chart(record.samples, entity.unit, summaryStability(record.summary), target = record.target)
      }
    }
  }
  if (mode.shows(ExpertFeature.BREW_DETAIL_FIT)) {
    BrewSummaryCard(record.summary, entity.unit)
    if (record.targetError) {
      AntAlert(message = stringResource(R.string.brew_detail_target_error), type = AntStatus.Warning, modifier = Modifier.testTag("brew_detail_target_error"))
    }
    FitCard(record)
    if (entity.metricVersion != BrewConfig.METRIC_VERSION) {
      AntText(stringResource(R.string.brew_detail_old_metric), type = AntTextType.Secondary)
    }
    TastingCard(record, coffee, onOpenCoffee)
  } else {
    BrewFactsCard(record, coffee, onOpenCoffee)
  }
  DetailsCard(record, grinder)
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
  val compare = mode.shows(ExpertFeature.BREW_COMPARE)
  val saveRecipe = mode.shows(ExpertFeature.BREW_SAVE_AS_RECIPE)
  if (compare || saveRecipe) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      if (compare) {
        AntButton(
          text = stringResource(R.string.brew_detail_compare),
          onClick = onCompare,
          modifier = Modifier.weight(1f).testTag("brew_detail_compare"),
        )
      }
      if (saveRecipe) {
        AntButton(
          text = stringResource(R.string.brew_detail_save_recipe),
          onClick = onSaveAsRecipe,
          modifier = Modifier.weight(1f).testTag("brew_detail_save_recipe"),
          enabled = !record.curveError,
        )
      }
    }
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
private fun DetailsCard(record: BrewRecord, grinder: Grinder?) {
  val entity = record.entity
  val method = entity.method?.let { m -> if (m == BrewMethod.OTHER) entity.methodOther ?: m.label() else m.label() }
  val grind = entity.toGrindSetting()
  if (method == null && grind == null && entity.waterTempC == null) return
  AntCard(Modifier.fillMaxWidth().testTag("brew_detail_details"), title = stringResource(R.string.brew_detail_details)) {
    method?.let { value ->
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.brew_detail_method), type = AntTextType.Secondary)
        AntText(value)
      }
    }
    if (grind != null) {
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.brew_detail_grind), type = AntTextType.Secondary)
        GrindSettingText(grind, grinder, testTag = "brew_detail_grind")
      }
    }
    entity.waterTempC?.let {
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.brew_detail_temp), type = AntTextType.Secondary)
        AntText(stringResource(R.string.brew_detail_temp_value, formatTemp(it)))
      }
    }
  }
}

/**
 * "Cata": el café (pulsable si está en el catálogo), la valoración y las notas de cata. Cada parte se omite si
 * falta y, sin ninguna, la tarjeta no se muestra.
 */
@Composable
private fun TastingCard(record: BrewRecord, coffee: CoffeeEntity?, onOpenCoffee: (Long) -> Unit) {
  val entity = record.entity
  val coffeeName = coffee?.name ?: entity.coffee
  if (coffeeName == null && entity.rating == null && entity.notes == null) return
  AntCard(Modifier.fillMaxWidth().testTag("brew_detail_tasting"), title = stringResource(R.string.brew_detail_tasting)) {
    if (coffeeName != null) TastingCoffee(entity.coffeeId, coffeeName, coffee, entity.startedAtEpochMs, onOpenCoffee)
    if (coffeeName != null && (entity.rating != null || entity.notes != null)) AntDivider()
    entity.rating?.let { rating ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AntText(stringResource(R.string.brew_detail_rating), Modifier.weight(1f), type = AntTextType.Secondary)
        AntRate(rating, onValueChange = null, testTag = "brew_detail_rating")
      }
    }
    entity.notes?.let { notes ->
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.brew_detail_notes), type = AntTextType.Secondary)
        AntText(notes, Modifier.testTag("brew_detail_notes"))
      }
    }
  }
}

/** La fila del café: del catálogo, con miniatura, "Tostador · día N" y chevron; antiguo, solo el texto. */
@Composable
internal fun TastingCoffee(coffeeId: Long?, name: String, coffee: CoffeeEntity?, startedAtEpochMs: Long, onOpenCoffee: (Long) -> Unit) {
  val linked = coffeeId != null && coffee != null
  val days = coffee?.let { brewDaysSinceRoast(it.roastDate, startedAtEpochMs, ZoneId.systemDefault()) }
  val subtitle = when {
    coffeeId == null -> stringResource(R.string.brew_detail_coffee_legacy)
    else -> listOfNotNull(coffee?.roaster, days?.let { stringResource(R.string.coffee_log_day, it) }).joinToString(" · ").ifEmpty { null }
  }
  val click = if (linked) Modifier.clickable(role = Role.Button) { onOpenCoffee(coffeeId) } else Modifier
  Row(
    Modifier.fillMaxWidth().then(click).testTag("brew_detail_coffee"),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (linked) CoffeeThumbnail(coffee.photo1, COFFEE_THUMB_SIZE)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(name, strong = true, maxLines = 2, overflow = TextOverflow.Ellipsis)
      subtitle?.let { AntText(it, type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    if (linked) Icon(AntIcons.Right, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary)
  }
}

private val COFFEE_THUMB_SIZE = 40.dp

private fun formatTemp(celsius: Double): String =
  if (celsius % 1.0 == 0.0) celsius.toLong().toString() else String.format(Locale.getDefault(), "%.1f", celsius)
