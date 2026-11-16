package dev.dotscale.ui.grinders

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
import dev.dotscale.R
import dev.dotscale.grinder.ConversionReason
import dev.dotscale.grinder.ConverterArgs
import dev.dotscale.grinder.GrindCardState
import dev.dotscale.grinder.GrindFix
import dev.dotscale.grinder.GrindFormat
import dev.dotscale.grinder.GrindOutcome
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.settings.ExpertFeature
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTitle
import dev.dotscale.ui.theme.AntTheme

/**
 * La tarjeta de molienda de Preparar y del paso 2 (§3.7): la línea de la receta, "Moler con", el ajuste del molino
 * elegido (convertido, "sugerido", "aproximado", "≈" o "al límite del molino"), la regla en Experto y la fuente.
 *
 * @param onChoose elige el molino de "Moler con".
 * @param onAddGrinder abre Ajustes → Molinos ("Añade tu molino…").
 * @param onFix completa los rangos del molino elegido o lo duplica (el id es el del molino elegido).
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
            text = stringResource(if (outcome.fix == GrindFix.COMPLETE_RANGES) R.string.grind_card_fix_complete else R.string.grind_card_fix_duplicate),
            onClick = { onFix(outcome.fix, chosen.id) },
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
  val marks = buildList {
    if (outcome.suggested) add(stringResource(R.string.grind_card_suggested) to AntTagColor.Primary)
    if (ConversionReason.ANCHORS in outcome.reasons) add(stringResource(R.string.grind_card_approximate) to AntTagColor.Default)
    if (ConversionReason.AT_LIMIT in outcome.reasons) add(stringResource(R.string.grind_card_at_limit) to AntTagColor.Warning)
  }
  if (marks.isEmpty()) return
  FlowRow(
    Modifier.fillMaxWidth().testTag("grind_card_marks"),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs, Alignment.CenterHorizontally),
  ) {
    marks.forEach { (text, color) -> AntTag(text, color = color) }
  }
}

/** El atajo "Ver en mis otros molinos"; lo activa F8. */
@Composable
private fun ConverterLink(args: ConverterArgs, onOpen: (ConverterArgs) -> Unit) {
  AntButton(
    text = stringResource(R.string.grind_card_converter),
    onClick = { onOpen(args) },
    modifier = Modifier.testTag("grind_card_converter"),
    type = AntButtonType.Link,
  )
}
