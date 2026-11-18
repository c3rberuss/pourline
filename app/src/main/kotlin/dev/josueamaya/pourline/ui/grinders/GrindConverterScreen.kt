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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntFormItem
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntSelectTrigger
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * El convertidor suelto (§3.7): molino de origen y ajuste, y una fila por cada molino mío con su equivalente. Si el origen
 * es mío, cada fila se puede corregir con un par de calibración (§3.3.1 de la enmienda 1).
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
  val draft by viewModel.editor.draft.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val updatedText = stringResource(R.string.grind_calibration_updated)
  LaunchedEffect(viewModel) {
    viewModel.editor.events.collect { event ->
      when (event) {
        CalibrationEvent.Updated -> snackbar.showAntMessage(updatedText, AntStatus.Success)
      }
    }
  }
  var pickerOpen by rememberSaveable { mutableStateOf(false) }
  val texts = rememberGrindTexts()

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    snackbarHost = { AntMessageHost(snackbar) },
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
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.converter_source),
        placeholder = stringResource(R.string.converter_source_placeholder),
        testTag = "converter_source",
      )
      if (ui.source != null) {
        AntFormItem(label = stringResource(R.string.converter_value)) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GrindInput(
              value = ui.value,
              grinder = ui.source,
              onChange = viewModel::setValue,
              start = startValue(ui.source, null),
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
            ui.rows.forEach { row -> ConverterRowItem(row, texts, onFix, onCorrect = { viewModel.correct(row) }) }
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

  val sheet = draft
  val source = state?.source
  if (sheet != null && source != null) {
    CalibrationSheet(
      draft = sheet,
      grinder = source,
      others = state?.owned.orEmpty().filter { it.id != source.id },
      texts = texts,
      onChange = viewModel.editor::update,
      onSave = viewModel.editor::save,
      onReplace = viewModel.editor::replace,
      onDismiss = viewModel.editor::dismiss,
    )
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

/**
 * Una fila: el valor equivalente y, debajo, "Corregir" o "Añadir equivalencia" si el origen es mío (D52) y el arreglo
 * de "Sin equivalencia". El arreglo `ADD_CALIBRATION` solo se ofrece cuando no hay botón, que ya hace lo mismo.
 */
@Composable
private fun ConverterRowItem(row: ConverterRow, texts: GrindTexts, onFix: (GrindFix, Long) -> Unit, onCorrect: () -> Unit) {
  val grinder = row.grinder
  val noEquivalent = row.conversion == Conversion.NoEquivalent
  AntListItem(
    title = grinder.displayName,
    modifier = Modifier.testTag("converter_row_${grinder.id}"),
    extra = {
      Column(horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
          when (val conversion = row.conversion) {
            Conversion.NoEquivalent -> AntText(
              stringResource(R.string.grind_card_no_equivalent),
              Modifier.testTag("converter_value_${grinder.id}"),
              type = AntTextType.Warning,
            )
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
        if (row.correctable) {
          AntButton(
            text = stringResource(if (noEquivalent) R.string.grind_calibration_add else R.string.converter_correct),
            onClick = onCorrect,
            modifier = Modifier.testTag("converter_correct_${grinder.id}"),
            type = AntButtonType.Link,
          )
        }
        row.fix?.takeUnless { it.fix == GrindFix.ADD_CALIBRATION && row.correctable }?.let { target ->
          AntButton(
            text = stringResource(target.fix.linkText()),
            onClick = { onFix(target.fix, target.grinderId) },
            modifier = Modifier.testTag("converter_fix_${grinder.id}"),
            type = AntButtonType.Link,
          )
        }
      }
    },
  )
}
