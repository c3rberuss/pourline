package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.Conversion
import dev.josueamaya.pourline.grinder.ConversionReason
import dev.josueamaya.pourline.grinder.ConverterRow
import dev.josueamaya.pourline.grinder.GrindFix
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntFormItem
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntSelectTrigger
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.history.label
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.tour.TourHost
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor

/** El valor de "Sin método" en el desplegable. */
private const val NO_METHOD = -1

/** Los métodos que se pueden elegir: todos menos "Otro", que equivale a "Sin método" (solo anclas). */
private val Methods = BrewMethod.entries.filter { it != BrewMethod.OTHER }

/**
 * El convertidor suelto (§3.7): molino de origen, método y ajuste, y una fila por cada molino mío con su equivalente.
 *
 * @param onOpenGrinders abre Ajustes → Molinos (el enlace del estado vacío).
 * @param onFix completa los rangos del molino de la fila o lo duplica.
 */
@Composable
fun GrindConverterScreen(
  viewModel: GrindConverterViewModel,
  onBack: () -> Unit,
  onOpenGrinders: () -> Unit,
  onFix: (GrindFix, Long) -> Unit,
) {
  val state by viewModel.ui.collectAsStateWithLifecycle()
  var pickerOpen by rememberSaveable { mutableStateOf(false) }
  val texts = rememberGrindTexts()

  TourHost(Tours.converter, ready = state != null)
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.converter_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    val ui = state ?: return@Scaffold
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base).testTag("converter"),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      AntSelectTrigger(
        value = ui.source?.displayName,
        onClick = { pickerOpen = true },
        modifier = Modifier.fillMaxWidth().tourAnchor("converter_source"),
        label = stringResource(R.string.converter_source),
        placeholder = stringResource(R.string.converter_source_placeholder),
        testTag = "converter_source",
      )
      val noMethod = stringResource(R.string.converter_no_method)
      AntSelect(
        options = listOf(AntSelectOption(NO_METHOD, noMethod)) + Methods.map { AntSelectOption(it.ordinal, it.label()) },
        selected = ui.method?.ordinal ?: NO_METHOD,
        onSelect = { id -> viewModel.setMethod(Methods.firstOrNull { it.ordinal == id }) },
        label = stringResource(R.string.converter_method),
        modifier = Modifier.fillMaxWidth(),
        testTag = "converter_method",
      )
      if (ui.source != null) {
        AntFormItem(label = stringResource(R.string.converter_value)) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GrindInput(
              value = ui.value,
              grinder = ui.source,
              onChange = viewModel::setValue,
              start = startValue(ui.source, ui.method),
              testTag = "converter_value",
            )
          }
        }
      }
      AntText(stringResource(R.string.converter_results), strong = true)
      when {
        ui.owned.isEmpty() -> AntEmpty(
          stringResource(R.string.converter_empty),
          modifier = Modifier.testTag("converter_empty"),
          icon = AntIcons.Grinder,
          action = {
            AntButton(
              text = stringResource(R.string.converter_empty_action),
              onClick = onOpenGrinders,
              modifier = Modifier.testTag("converter_empty_action"),
              type = AntButtonType.Link,
            )
          },
        )
        ui.source == null -> Hint(stringResource(R.string.converter_pick_source), "converter_hint_source")
        ui.value == null -> Hint(stringResource(R.string.converter_pick_value), "converter_hint_value")
        ui.rows.isEmpty() -> Hint(stringResource(R.string.converter_no_others), "converter_no_others")
        else -> {
          AntCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            ui.rows.forEachIndexed { i, row -> ConverterRowItem(row, texts, onFix, Modifier.tourAnchor("converter_row", enabled = i == 0)) }
          }
          GrindAttribution(
            listOfNotNull(ui.source) + ui.rows.map { it.grinder },
            Modifier.fillMaxWidth(),
            testTag = "converter_attribution",
          )
        }
      }
    }
  }

  if (pickerOpen) {
    val ui = state
    GrinderPicker(
      owned = ui?.pickerOwned.orEmpty(),
      catalog = ui?.pickerCatalog.orEmpty(),
      selectedId = ui?.source?.id,
      onSelect = viewModel::setSource,
      onClose = { pickerOpen = false },
    )
  }
}

@Composable
private fun Hint(text: String, tag: String) {
  AntText(text, Modifier.fillMaxWidth().testTag(tag), type = AntTextType.Secondary)
}

@Composable
private fun ConverterRowItem(row: ConverterRow, texts: GrindTexts, onFix: (GrindFix, Long) -> Unit, modifier: Modifier = Modifier) {
  val grinder = row.grinder
  AntListItem(
    title = grinder.displayName,
    modifier = modifier.testTag("converter_row_${grinder.id}"),
    extra = {
      Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        when (val conversion = row.conversion) {
          Conversion.NoEquivalent -> {
            AntText(stringResource(R.string.grind_card_no_equivalent), Modifier.testTag("converter_value_${grinder.id}"), type = AntTextType.Warning)
            row.fix?.let { fix ->
              AntButton(
                text = stringResource(if (fix == GrindFix.COMPLETE_RANGES) R.string.grind_card_fix_complete else R.string.grind_card_fix_duplicate),
                onClick = { onFix(fix, grinder.id) },
                modifier = Modifier.testTag("converter_fix_${grinder.id}"),
                type = AntButtonType.Link,
              )
            }
          }
          is Conversion.Exact -> AntText(
            GrindFormat.format(conversion.value, grinder, texts),
            Modifier.testTag("converter_value_${grinder.id}"),
            strong = true,
          )
          is Conversion.Approximate -> {
            val rounded = ConversionReason.ROUNDED in conversion.reasons
            AntText(
              (if (rounded) "≈ " else "") + GrindFormat.format(conversion.value, grinder, texts),
              Modifier.testTag("converter_value_${grinder.id}"),
              strong = true,
            )
            ConversionTags(conversion.reasons, suggested = false)
          }
        }
      }
    },
  )
}
