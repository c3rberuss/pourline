package dev.josueamaya.pourline.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.formatGuideAmount
import dev.josueamaya.pourline.ui.theme.AntTheme

/** En qué punto está cada paso de la lista. */
enum class StepState { DONE, CURRENT, PENDING }

/**
 * El estado de cada uno de los [stageCount] pasos: con la receta terminada ([finished]) todos están hechos; sin paso en
 * curso ([currentIndex] `null`) todos están pendientes; si no, los anteriores al actual están hechos y los siguientes
 * pendientes.
 */
internal fun stepStates(stageCount: Int, currentIndex: Int?, finished: Boolean): List<StepState> = List(stageCount) { i ->
  when {
    finished -> StepState.DONE
    currentIndex == null -> StepState.PENDING
    i < currentIndex -> StepState.DONE
    i == currentIndex -> StepState.CURRENT
    else -> StepState.PENDING
  }
}

private val RowHeight = 40.dp
private val IconSize = 20.dp

/**
 * Los pasos de la receta con su estado, el icono del tipo, el nombre, el agua (solo en Verter) y la duración. Tiene
 * scroll propio y se desplaza solo al paso en curso.
 */
@Composable
fun StepList(stages: List<TargetStage>, states: List<StepState>, unit: WeightUnit, modifier: Modifier = Modifier) {
  val listState = rememberLazyListState()
  val current = states.indexOf(StepState.CURRENT)
  LaunchedEffect(current) {
    if (current >= 0) listState.animateScrollToItem(current)
  }
  LazyColumn(modifier.testTag("session_steps"), state = listState) {
    itemsIndexed(stages) { index, stage ->
      StepRow(stage, states.getOrElse(index) { StepState.PENDING }, unit)
    }
  }
}

@Composable
private fun StepRow(stage: TargetStage, state: StepState, unit: WeightUnit) {
  val colors = AntTheme.colors
  val typeLabel = stringResource(stepTypeLabel(stage.type))
  val name = stage.name?.takeIf { it.isNotBlank() } ?: typeLabel
  val textType = if (state == StepState.PENDING) AntTextType.Secondary else AntTextType.Default
  Row(
    Modifier
      .fillMaxWidth()
      .height(RowHeight)
      .then(if (state == StepState.CURRENT) Modifier.background(colors.primary.bg, RoundedCornerShape(AntTheme.radius.base)) else Modifier)
      .padding(horizontal = AntTheme.spacing.xs)
      .testTag("session_step_$state".lowercase())
      .semantics { contentDescription = "$typeLabel, $name" },
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StateIcon(state)
    Icon(stepTypeIcon(stage.type), contentDescription = null, modifier = Modifier.size(IconSize), tint = colors.textSecondary)
    AntText(name, Modifier.weight(1f), type = textType, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (stage.type == StageType.POUR) {
      AntText(formatGuideAmount(stage.targetWaterG, unit), type = textType, textAlign = TextAlign.End)
    }
    AntText(
      formatClock(stage.endMs - stage.startMs),
      Modifier.padding(start = AntTheme.spacing.xxs),
      type = AntTextType.Secondary,
      textAlign = TextAlign.End,
    )
  }
}

@Composable
private fun StateIcon(state: StepState) {
  val colors = AntTheme.colors
  Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
    when (state) {
      StepState.DONE -> Icon(AntIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize), tint = colors.success.base)
      StepState.CURRENT -> Icon(AntIcons.Right, contentDescription = null, modifier = Modifier.size(IconSize), tint = colors.primary.base)
      StepState.PENDING -> Box(Modifier.size(8.dp).background(colors.fill, RoundedCornerShape(percent = 50)))
    }
  }
}
