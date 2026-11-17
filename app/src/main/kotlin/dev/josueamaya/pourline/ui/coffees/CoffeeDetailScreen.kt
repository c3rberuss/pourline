package dev.josueamaya.pourline.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.CoffeeDetail
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.coffee.CoffeeLogEntry
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.share.ShareContent
import dev.josueamaya.pourline.share.ShareSender
import dev.josueamaya.pourline.share.ShareTarget
import dev.josueamaya.pourline.share.rememberShareLauncher
import dev.josueamaya.pourline.share.rememberShareSender
import dev.josueamaya.pourline.share.toShareData
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntCollapse
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.components.AntProgress
import dev.josueamaya.pourline.ui.components.AntProgressStatus
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTitle
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val PERCENT = 100f

@Composable
fun CoffeeDetailScreen(
  viewModel: CoffeeDetailViewModel,
  onBack: () -> Unit,
  onEdit: (Long) -> Unit,
  onNewBag: (Long) -> Unit,
  onOpenBrew: (Long) -> Unit,
  shareSender: ShareSender? = null,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val launcher = rememberShareLauncher(shareSender ?: rememberShareSender(), snackbar)
  val scope = rememberCoroutineScope()
  var factsExpanded by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(state) { if (state is CoffeeDetailState.Gone) onBack() }

  val detail = (state as? CoffeeDetailState.Loaded)?.detail
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = detail?.coffee?.name.orEmpty(),
        subtitle = detail?.coffee?.roaster,
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
        actions = {
          if (detail != null) {
            AntIconButton(
              AntIcons.Share,
              contentDescription = stringResource(R.string.share_image_action),
              onClick = {
                launcher.share(ShareTarget.Coffee(detail.coffee.id), ShareContent.Coffee(detail.coffee.toShareData(detail.daysSinceRoast)))
              },
              modifier = Modifier.testTag("coffee_detail_share"),
            )
          }
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    if (detail == null) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    val archivedText = stringResource(R.string.coffee_detail_archived_message)
    val unarchivedText = stringResource(R.string.coffee_detail_unarchived_message)
    val toggleArchived = {
      val archive = !detail.coffee.archived
      viewModel.setArchived(archive)
      scope.launch { snackbar.showAntMessage(if (archive) archivedText else unarchivedText, AntStatus.Success) }
      Unit
    }
    DetailList(
      detail = detail,
      padding = padding,
      factsExpanded = factsExpanded,
      onFactsExpandedChange = { factsExpanded = it },
      onEdit = { onEdit(detail.coffee.id) },
      onNewBag = { onNewBag(detail.coffee.id) },
      onToggleArchived = toggleArchived,
      onDelete = { confirmDelete = true },
      onOpenBrew = onOpenBrew,
    )
    if (confirmDelete) {
      val count = detail.brews.size
      AntModal(
        title = stringResource(R.string.coffee_detail_delete_title),
        text = if (count > 0) {
          pluralStringResource(R.plurals.coffee_detail_delete_with_brews, count, count)
        } else {
          stringResource(R.string.coffee_detail_delete_empty)
        },
        confirmText = stringResource(R.string.coffee_detail_delete),
        cancelText = stringResource(R.string.coffee_detail_delete_cancel),
        onConfirm = {
          confirmDelete = false
          viewModel.delete()
        },
        onDismiss = { confirmDelete = false },
        danger = true,
      )
    }
  }
  launcher.Host()
}

@Composable
private fun DetailList(
  detail: CoffeeDetail,
  padding: PaddingValues,
  factsExpanded: Boolean,
  onFactsExpandedChange: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onNewBag: () -> Unit,
  onToggleArchived: () -> Unit,
  onDelete: () -> Unit,
  onOpenBrew: (Long) -> Unit,
) {
  val coffee = detail.coffee
  LazyColumn(
    Modifier.fillMaxSize().padding(padding).testTag("coffee_detail_list"),
    contentPadding = PaddingValues(AntTheme.spacing.base),
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
  ) {
    if (coffee.archived) {
      item(key = "archived") {
        AntAlert(
          message = stringResource(R.string.coffee_detail_archived_alert),
          modifier = Modifier.testTag("coffee_detail_archived"),
          type = AntStatus.Info,
        )
      }
    } else if (detail.soldOut) {
      item(key = "sold_out") {
        AntAlert(
          message = stringResource(R.string.coffee_detail_sold_out_title),
          modifier = Modifier.testTag("coffee_detail_sold_out"),
          type = AntStatus.Warning,
          description = stringResource(R.string.coffee_detail_sold_out_text),
          action = {
            AntButton(
              text = stringResource(R.string.coffee_detail_archive),
              onClick = onToggleArchived,
              modifier = Modifier.testTag("coffee_detail_sold_out_archive"),
              size = dev.josueamaya.pourline.ui.theme.AntSize.Small,
            )
          },
        )
      }
    }
    if (coffee.photo1 != null || coffee.photo2 != null) {
      item(key = "photos") { Photos(coffee.photo1, coffee.photo2) }
    }
    if (coffee.roastDate != null || detail.stockG != null) {
      item(key = "status") { StatusCard(detail) }
    }
    item(key = "log_header") {
      Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        AntTitle(stringResource(R.string.coffee_detail_log_title), level = 5)
        AntText(
          pluralStringResource(R.plurals.coffee_detail_log_count, detail.brews.size, detail.brews.size),
          type = AntTextType.Secondary,
        )
      }
    }
    if (detail.brews.isEmpty()) {
      item(key = "log_empty") {
        AntText(stringResource(R.string.coffee_detail_log_empty), Modifier.testTag("coffee_log_empty"), type = AntTextType.Secondary)
      }
    } else {
      items(detail.brews, key = { it.brewId }) { entry -> CoffeeLogRow(entry, onClick = { onOpenBrew(entry.brewId) }) }
    }
    if (hasFacts(coffee)) {
      item(key = "facts") {
        AntCollapse(
          title = stringResource(R.string.coffee_detail_facts),
          expanded = factsExpanded,
          onExpandedChange = onFactsExpandedChange,
          extra = factsSummary(coffee),
          testTag = "coffee_detail_facts",
        ) { CoffeeFacts(coffee) }
      }
    }
    item(key = "actions") {
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          AntButton(
            stringResource(R.string.coffee_detail_edit),
            onEdit,
            Modifier.weight(1f).testTag("coffee_detail_edit"),
          )
          AntButton(
            stringResource(R.string.coffee_detail_new_bag),
            onNewBag,
            Modifier.weight(1f).testTag("coffee_detail_new_bag"),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          AntButton(
            stringResource(if (coffee.archived) R.string.coffee_detail_unarchive else R.string.coffee_detail_archive),
            onToggleArchived,
            Modifier.weight(1f).testTag("coffee_detail_archive"),
          )
          AntButton(
            stringResource(R.string.coffee_detail_delete),
            onDelete,
            Modifier.weight(1f).testTag("coffee_detail_delete"),
            type = AntButtonType.Default,
            danger = true,
          )
        }
      }
    }
  }
}

@Composable
private fun Photos(photo1: String?, photo2: String?) {
  val photos = listOfNotNull(photo1, photo2)
  if (photos.size == 1) {
    CoffeePhotoView(photos[0], Modifier.fillMaxWidth().aspectRatio(4f / 3f).testTag("coffee_detail_photo_0"))
  } else {
    Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      photos.forEachIndexed { i, photo ->
        CoffeePhotoView(photo, Modifier.weight(1f).aspectRatio(3f / 4f).testTag("coffee_detail_photo_$i"))
      }
    }
  }
}

@Composable
private fun StatusCard(detail: CoffeeDetail) {
  val coffee = detail.coffee
  AntCard(Modifier.fillMaxWidth().testTag("coffee_detail_status")) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.base)) {
      detail.daysSinceRoast?.let { days ->
        AntStatistic(days.toString(), Modifier.weight(1f).testTag("coffee_detail_days"), title = stringResource(R.string.coffee_detail_days))
      }
      detail.stockG?.let { stock ->
        val bag = coffee.bagWeightG
        AntStatistic(
          value = when {
            detail.soldOut -> stringResource(R.string.coffee_sold_out)
            bag != null -> stringResource(R.string.coffee_detail_stock_of, formatGrams(stock), formatGrams(bag))
            else -> formatGrams(stock)
          },
          modifier = Modifier.weight(1f).testTag("coffee_detail_stock"),
          title = stringResource(R.string.coffee_detail_stock),
          valueColor = if (detail.soldOut) AntTheme.colors.error.text else AntTheme.colors.text,
        )
      }
    }
    val bag = coffee.bagWeightG
    val stock = detail.stockG
    if (bag != null && stock != null) {
      AntProgress(
        percent = if (detail.soldOut) 0f else (stock / bag * PERCENT).toFloat().coerceIn(0f, PERCENT),
        modifier = Modifier.padding(top = AntTheme.spacing.xs).testTag("coffee_detail_progress"),
        status = if (detail.soldOut) AntProgressStatus.Exception else AntProgressStatus.Normal,
        showInfo = false,
      )
    }
  }
}

private val previewDetail = CoffeeDetail(
  coffee = CoffeeEntity(
    id = 1,
    name = "Etiopía Guji Hambela",
    roaster = "Café de Altura",
    originCountry = "Etiopía",
    originRegion = "Guji",
    varieties = listOf("Heirloom", "74110"),
    process = CoffeeProcess.NATURAL,
    altitudeMinM = 1800,
    altitudeMaxM = 2200,
    roastLevel = RoastLevel.LIGHT,
    roastDate = LocalDate.of(2026, 10, 12),
    roasterNotes = "Jazmín, bergamota y durazno maduro.",
    bagWeightG = 250.0,
    createdAtEpochMs = 0,
  ),
  stockG = 180.0,
  soldOut = false,
  daysSinceRoast = 9,
  brews = listOf(
    CoffeeLogEntry(2, 1_794_000_000_000, rating = 4, notes = "Mandarina y miel", daysSinceRoast = 9, doseG = 15.0),
    CoffeeLogEntry(1, 1_793_900_000_000, rating = null, notes = null, daysSinceRoast = 8, doseG = 15.0),
  ),
)

@Composable
private fun CoffeeDetailPreviewBody() {
  DetailList(previewDetail, PaddingValues(), factsExpanded = true, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Detalle claro", showBackground = true, heightDp = 1400)
@Composable
private fun CoffeeDetailPreview() {
  PourlineTheme(darkTheme = false) { CoffeeDetailPreviewBody() }
}

@Preview(name = "Detalle oscuro", showBackground = true, heightDp = 1400)
@Composable
private fun CoffeeDetailPreviewDark() {
  PourlineTheme(darkTheme = true) { CoffeeDetailPreviewBody() }
}
