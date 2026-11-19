package dev.josueamaya.pourline.ui.history

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
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewConfig
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.coffee.brewDaysSinceRoast
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.toGrindSetting
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.share.BrewShareData
import dev.josueamaya.pourline.share.FooterCoffee
import dev.josueamaya.pourline.share.ShareContent
import dev.josueamaya.pourline.share.ShareSender
import dev.josueamaya.pourline.share.ShareTarget
import dev.josueamaya.pourline.share.footerLines
import dev.josueamaya.pourline.share.rememberShareLauncher
import dev.josueamaya.pourline.share.rememberShareSender
import dev.josueamaya.pourline.ui.coffees.CoffeeThumbnail
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntRate
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.grinders.GrindSettingText
import dev.josueamaya.pourline.ui.scale.BrewSummaryCard
import dev.josueamaya.pourline.ui.scale.Chart
import dev.josueamaya.pourline.ui.scale.FitBlock
import dev.josueamaya.pourline.ui.scale.TargetChart
import dev.josueamaya.pourline.ui.scale.fitView
import dev.josueamaya.pourline.ui.scale.summaryStability
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.tour.TourHost
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor
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
              modifier = Modifier.tourAnchor("brew_detail_share").testTag("brew_detail_share"),
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
  TourHost(Tours.brewDetail, ready = loaded != null)
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
    scaleless = entity.scaleless,
    doseG = entity.doseG,
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
  if (entity.scaleless) {
    // SPEC-007: sin muestras, ni estabilidad ni ajuste. Solo los datos y, en Experto con receta, la curva del plan.
    Row { AntTag(stringResource(R.string.history_row_scaleless), Modifier.testTag("brew_detail_scaleless"), color = AntTagColor.Primary) }
    val curve = record.target
    if (mode.shows(ExpertFeature.BREW_DETAIL_CHART) && curve != null) {
      AntCard(Modifier.fillMaxWidth().tourAnchor("brew_detail_chart").testTag("brew_detail_target_chart"), title = stringResource(R.string.brew_detail_target_chart)) {
        TargetChart(curve)
      }
    }
    BrewFactsCard(record, coffee, onOpenCoffee)
  } else {
    ScaleBrewBody(record, coffee, onOpenCoffee)
  }
  DetailsCard(record, grinder)
  DetailButtons(record, onEdit, onDelete, onCompare, onSaveAsRecipe)
}

/** El cuerpo del detalle de una preparación con báscula: gráfica, resumen, ajuste y cata en Experto; los datos en Básico. */
@Composable
private fun ScaleBrewBody(record: BrewRecord, coffee: CoffeeEntity?, onOpenCoffee: (Long) -> Unit) {
  val entity = record.entity
  val mode = LocalExperienceMode.current
  if (mode.shows(ExpertFeature.BREW_DETAIL_CHART)) {
    if (record.curveError) {
      AntAlert(message = stringResource(R.string.brew_detail_curve_error), type = AntStatus.Error)
    } else {
      AntCard(Modifier.fillMaxWidth().tourAnchor("brew_detail_chart").testTag("brew_detail_chart"), title = stringResource(R.string.brew_chart_title)) {
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
}

@Composable
private fun DetailButtons(
  record: BrewRecord,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onCompare: () -> Unit,
  onSaveAsRecipe: () -> Unit,
) {
  val mode = LocalExperienceMode.current
  // Dos filas: cuatro botones no caben en una de 360 dp.
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    AntButton(
      text = stringResource(R.string.brew_detail_edit),
      onClick = onEdit,
      modifier = Modifier.weight(1f).tourAnchor("brew_detail_edit").testTag("brew_detail_edit"),
    )
    AntButton(
      text = stringResource(R.string.brew_detail_delete),
      onClick = onDelete,
      modifier = Modifier.weight(1f).testTag("brew_detail_delete"),
      type = AntButtonType.Primary,
      danger = true,
    )
  }
  // Una preparación sin báscula no se compara ni se guarda como receta (SPEC-007 §3.6).
  val compare = mode.shows(ExpertFeature.BREW_COMPARE) && !record.entity.scaleless
  val saveRecipe = mode.shows(ExpertFeature.BREW_SAVE_AS_RECIPE) && !record.entity.scaleless
  if (compare || saveRecipe) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      if (compare) {
        AntButton(
          text = stringResource(R.string.brew_detail_compare),
          onClick = onCompare,
          modifier = Modifier.weight(1f).tourAnchor("brew_detail_compare").testTag("brew_detail_compare"),
        )
      }
      if (saveRecipe) {
        AntButton(
          text = stringResource(R.string.brew_detail_save_recipe),
          onClick = onSaveAsRecipe,
          modifier = Modifier.weight(1f).tourAnchor("brew_detail_save_recipe").testTag("brew_detail_save_recipe"),
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
  AntCard(Modifier.fillMaxWidth().tourAnchor("brew_detail_details").testTag("brew_detail_details"), title = stringResource(R.string.brew_detail_details)) {
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
