package dev.josueamaya.pourline.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.scale.guideGrams
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * La sesión sin báscula lista para empezar (SPEC-007 §3.4), en lugar de los pasos previos. Con receta, el primer paso y la
 * lista de pasos; sin ella, la dosis, el ratio y el agua del plan. Después, "Empezar timer".
 */
@Composable
internal fun ScalelessReadyPanel(ui: ScaleUiState, onStart: () -> Unit) {
  val stages = ui.target?.curve?.stages
  Column(
    Modifier.fillMaxSize().padding(AntTheme.spacing.base).testTag("scaleless_ready"),
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
  ) {
    if (stages != null) {
      CurrentStep(ui)
      StepList(
        stages = stages,
        states = stepStates(stages.size, currentIndex = null, finished = false),
        unit = WeightUnit.GRAMS,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    } else {
      PlanCard(ui)
      Box(Modifier.weight(1f))
    }
    AntButton(
      text = stringResource(R.string.prep_start_timer),
      onClick = onStart,
      modifier = Modifier.fillMaxWidth().testTag("scaleless_start"),
      type = AntButtonType.Primary,
      size = AntSize.Large,
    )
  }
}

/** Sin receta: la dosis, el ratio y el agua del plan. */
@Composable
private fun PlanCard(ui: ScaleUiState) {
  val dose = ui.plannedDoseG
  val ratio = ui.plan?.ratio
  val water = ui.targetWaterG
  val empty = stringResource(R.string.scale_ratio_empty)
  AntCard(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth()) {
      AntStatistic(
        value = dose?.let { formatNumber(it, WeightUnit.GRAMS) } ?: empty,
        title = stringResource(R.string.summary_dose),
        suffix = dose?.let { WeightUnit.GRAMS.symbol },
        modifier = Modifier.weight(1f).testTag("scaleless_plan_dose"),
      )
      AntStatistic(
        value = ratio?.let { stringResource(R.string.scale_ratio_value, it) } ?: empty,
        title = stringResource(R.string.scale_ratio),
        modifier = Modifier.weight(1f).testTag("scaleless_plan_ratio"),
      )
      AntStatistic(
        value = water?.let { formatNumber(it, WeightUnit.GRAMS) } ?: empty,
        title = stringResource(R.string.brew_summary_total_water),
        suffix = water?.let { WeightUnit.GRAMS.symbol },
        modifier = Modifier.weight(1f).testTag("scaleless_plan_water"),
      )
    }
  }
}

/**
 * La sesión sin báscula en curso (SPEC-007 §3.4). Con receta: el dial con el reloj y la línea de acción, "paso n/N · quedan
 * m:ss", el aviso de pasos completados, el paso actual, el siguiente y la lista. Sin receta: el dial con el reloj y el agua
 * objetivo. Nada de flujo, estabilidad, desviación ni gráfica, en ningún modo.
 */
@Composable
internal fun ScalelessActivePanel(ui: ScaleUiState, onFinish: () -> Unit) {
  val target = ui.target
  val guide = ui.guide
  val elapsed = ui.elapsedMs ?: 0L
  val clock = formatClock(elapsed)
  val view = timedStepView(guide, target?.curve, elapsed)
  val done = guide?.allStagesDone == true
  val stage = guide?.stage
  val action = when {
    target == null -> stringResource(R.string.scaleless_water_goal, ui.targetWaterG?.let(::guideGrams).orEmpty())
    done -> stringResource(R.string.session_steps_done)
    stage == null -> stringResource(R.string.session_step_waiting)
    else -> stageAction(stage)
  }
  val fraction = when {
    target == null -> 0f
    done -> 1f
    else -> view?.fraction ?: 0f
  }

  BoxWithConstraints(Modifier.fillMaxSize().testTag("scaleless_active")) {
    val dialSize: Dp = minOf(maxWidth * 0.62f, maxHeight * 0.34f, MaxDial).coerceAtLeast(MinDial)
    Column(
      Modifier.fillMaxSize().padding(horizontal = AntTheme.spacing.base, vertical = AntTheme.spacing.xs),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(Modifier.fillMaxWidth().height(dialSize), contentAlignment = Alignment.Center) {
        TimedDial(
          fraction = fraction,
          clock = clock,
          action = action,
          description = stringResource(R.string.scaleless_dial_description, clock, action),
          modifier = Modifier.size(dialSize),
          actionTag = if (target == null) "scaleless_water_goal" else "scaleless_action",
        )
      }
      if (target != null) {
        view?.let {
          AntText(
            stringResource(R.string.scaleless_step_progress, it.number, it.count, formatRemaining(it.remainingMs)),
            Modifier.fillMaxWidth().testTag("scaleless_step_progress"),
            type = AntTextType.Secondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
          )
        }
        if (done) {
          AntAlert(
            message = stringResource(R.string.scaleless_done_hint),
            modifier = Modifier.fillMaxWidth().testTag("scaleless_done_hint").semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
        CurrentStep(ui)
        NextStep(ui, elapsed)
        val stages = target.curve.stages
        StepList(
          stages = stages,
          states = stepStates(stages.size, guide?.stage?.index, done),
          unit = WeightUnit.GRAMS,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        )
      } else {
        Box(Modifier.weight(1f))
      }
      AntButton(
        text = stringResource(R.string.session_finish),
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().testTag("session_finish"),
        type = AntButtonType.Primary,
        size = AntSize.Large,
      )
    }
  }
}

/** La línea de acción de un paso: "Vierte hasta X g" en un vertido; en un remover o una espera, su instrucción o su tipo. */
@Composable
private fun stageAction(stage: TargetStage): String = when (stage.type) {
  StageType.POUR -> stringResource(R.string.scaleless_pour_to, guideGrams(stage.targetWaterG))
  StageType.SWIRL, StageType.WAIT -> stage.instruction?.takeIf { it.isNotBlank() } ?: stringResource(stepTypeLabel(stage.type))
}
