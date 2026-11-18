package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.ConversionReason
import dev.josueamaya.pourline.grinder.ConverterArgs
import dev.josueamaya.pourline.grinder.GrindCardState
import dev.josueamaya.pourline.grinder.GrindFix
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindOutcome
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTitle
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * La tarjeta de molienda de Preparar y del paso 2 (§3.7): la línea de la receta, "Moler con", el ajuste del molino
 * elegido (convertido, "sugerido", "aproximado", "≈" o "al límite del molino"), la regla en Experto y la fuente.
 *
 * @param onChoose elige el molino de "Moler con".
 * @param onAddGrinder abre Ajustes → Molinos ("Añade tu molino…").
 * @param onFix completa los rangos de un molino, lo duplica o añade otra equivalencia (el id es el del molino al que se aplica).
 * @param onOpenConverter abre el convertidor relleno; `null` si no hay atajo.
 */
@Composable
fun GrindCard(
  state: GrindCardState,
  mode: ExperienceMode,
  onChoose: (Long) -> Unit,
  onAddGrinder: () -> Unit,
  onFix: (GrindFix, Long) -> Unit,
  onOpenConverter: ((ConverterArgs) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val texts = rememberGrindTexts()
  val chosen = state.chosen
  AntCard(modifier.fillMaxWidth().testTag("grind_card"), title = stringResource(R.string.grind_card_title)) {
    SourceLine(state)
    if (state.owned.isNotEmpty()) {
      AntSelect(
        options = state.owned.map { AntSelectOption(it.id, it.displayName) },
        selected = chosen?.id,
        onSelect = onChoose,
        label = stringResource(R.string.grind_card_grind_with),
        modifier = Modifier.fillMaxWidth(),
        testTag = "grind_card_grinder",
      )
    }
    when (val outcome = state.outcome) {
      is GrindOutcome.Value -> if (chosen != null) {
        val approximate = ConversionReason.ROUNDED in outcome.reasons
        AntTitle(
          (if (approximate) "≈ " else "") + GrindFormat.format(outcome.value, chosen, texts),
          Modifier.fillMaxWidth().testTag("grind_card_value"),
          level = 2,
          textAlign = TextAlign.Center,
        )
        Marks(outcome)
        if (mode.shows(ExpertFeature.GRIND_RULER)) {
          GrindRuler(
            chosen,
            state.source?.method,
            outcome.value,
            showAnchors = true,
            modifier = Modifier.fillMaxWidth(),
            testTag = "grind_card_ruler",
          )
        }
      }
      GrindOutcome.NoSetting -> Unit
      is GrindOutcome.NoEquivalent -> {
        AntText(
          stringResource(R.string.grind_card_no_equivalent),
          Modifier.fillMaxWidth().testTag("grind_card_value"),
          type = AntTextType.Warning,
        )
        if (chosen != null) {
          AntButton(
            text = stringResource(outcome.fix.linkText()),
            onClick = { onFix(outcome.fix, outcome.grinderId) },
            modifier = Modifier.testTag("grind_card_fix"),
            type = AntButtonType.Link,
          )
        }
      }
      GrindOutcome.Orphan -> AntText(
        stringResource(R.string.grind_card_orphan),
        Modifier.fillMaxWidth().testTag("grind_card_orphan"),
        type = AntTextType.Secondary,
      )
      GrindOutcome.NoReference -> AntText(
        stringResource(R.string.grind_card_no_reference),
        Modifier.fillMaxWidth().testTag("grind_card_no_reference"),
        type = AntTextType.Secondary,
      )
      GrindOutcome.NoGrinder -> Unit
    }
    if (state.owned.isEmpty()) {
      AntButton(
        text = stringResource(R.string.grind_card_add),
        onClick = onAddGrinder,
        modifier = Modifier.testTag("grind_card_add"),
        type = AntButtonType.Link,
      )
    }
    val converter = state.converter
    if (onOpenConverter != null && converter != null && state.owned.isNotEmpty()) {
      ConverterLink(converter, onOpenConverter)
    }
    GrindAttribution(state.attribution, Modifier.fillMaxWidth(), testTag = "grind_card_attribution")
  }
}

/** "Receta: Chestnut S3 · 30 clics", la copia con "molino eliminado" o "La receta no indica molienda". */
@Composable
private fun SourceLine(state: GrindCardState) {
  val source = state.source ?: return
  val setting = source.setting?.takeUnless { it.isEmpty }
  val texts = rememberGrindTexts()
  val live = state.sourceGrinder
  Row(
    Modifier.fillMaxWidth().testTag("grind_card_source").semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    when {
      setting == null -> AntText(stringResource(R.string.grind_card_recipe_none), type = AntTextType.Secondary)
      live != null -> AntText(
        stringResource(R.string.grind_card_recipe, GrindFormat.label(live, setting.value, texts)),
        type = AntTextType.Secondary,
      )
      else -> {
        AntText(setting.label.orEmpty(), type = AntTextType.Secondary)
        AntTag(stringResource(R.string.grinder_tag_deleted), color = AntTagColor.Warning)
      }
    }
  }
}

@Composable
private fun Marks(outcome: GrindOutcome.Value) {
  ConversionTags(outcome.reasons, outcome.suggested, Modifier.fillMaxWidth().testTag("grind_card_marks"), centered = true)
}

/** Las marcas de un resultado: "sugerido", "según tu calibración", "aproximado" (por anclas) y "al límite del molino". */
@Composable
internal fun ConversionTags(
  reasons: Set<ConversionReason>,
  suggested: Boolean,
  modifier: Modifier = Modifier,
  centered: Boolean = false,
) {
  val marks = buildList {
    if (suggested) add(stringResource(R.string.grind_card_suggested) to AntTagColor.Primary)
    if (ConversionReason.CALIBRATED in reasons) add(stringResource(R.string.grind_card_calibrated) to AntTagColor.Success)
    if (ConversionReason.ANCHORS in reasons) add(stringResource(R.string.grind_card_approximate) to AntTagColor.Default)
    if (ConversionReason.AT_LIMIT in reasons) add(stringResource(R.string.grind_card_at_limit) to AntTagColor.Warning)
  }
  if (marks.isEmpty()) return
  FlowRow(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs, if (centered) Alignment.CenterHorizontally else Alignment.Start),
  ) {
    marks.forEach { (text, color) -> AntTag(text, color = color) }
  }
}

/** El atajo "Ver en mis otros molinos" (§3.7). */
@Composable
private fun ConverterLink(args: ConverterArgs, onOpen: (ConverterArgs) -> Unit) {
  AntButton(
    text = stringResource(R.string.grind_card_converter),
    onClick = { onOpen(args) },
    modifier = Modifier.testTag("grind_card_converter"),
    type = AntButtonType.Link,
  )
}
