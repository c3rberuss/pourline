package dev.dotscale.ui.onboarding

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.coffee.fold
import dev.dotscale.grinder.Grinder
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntListItem
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTitle
import dev.dotscale.ui.grinders.displayName
import dev.dotscale.ui.grinders.rememberGrindTexts
import dev.dotscale.ui.grinders.unitsSummary
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme

/**
 * El primer arranque (D6): el paso 1 elige Básico o Experto y el paso 2, opcional, pregunta "¿Qué molino usas?". "Saltar"
 * del paso 1 termina en Básico sin preguntar por el molino; en el paso 2, "Omitir" termina sin molino y elegir uno lo
 * termina con él. [onFinish] recibe el modo y el molino elegido (o `null`); quien la use lo guarda y marca el primer
 * arranque como hecho. El paso se guarda con `rememberSaveable`.
 *
 * @param catalog el catálogo de molinos con el que preguntar; `null` mientras carga.
 */
@Composable
fun OnboardingScreen(
  catalog: List<Grinder>?,
  onFinish: (mode: ExperienceMode, grinderId: Long?) -> Unit,
  modifier: Modifier = Modifier,
) {
  // 0 = el modo; 1 = el molino. El modo elegido se guarda por nombre para sobrevivir a la rotación.
  var step by rememberSaveable { mutableIntStateOf(0) }
  var modeName by rememberSaveable { mutableStateOf(ExperienceMode.BASIC.name) }
  val mode = ExperienceMode.valueOf(modeName)
  if (step == 0) {
    ModeStep(
      onChoose = {
        modeName = it.name
        step = 1
      },
      onSkip = { onFinish(ExperienceMode.BASIC, null) },
      modifier = modifier,
    )
  } else {
    BackHandler { step = 0 }
    GrinderStep(
      catalog = catalog,
      onBack = { step = 0 },
      onSkip = { onFinish(mode, null) },
      onChoose = { onFinish(mode, it) },
      modifier = modifier,
    )
  }
}

@Composable
private fun ModeStep(onChoose: (ExperienceMode) -> Unit, onSkip: () -> Unit, modifier: Modifier) {
  val spacing = AntTheme.spacing
  Column(
    modifier
      .fillMaxSize()
      .background(AntTheme.colors.bgLayout)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = spacing.base),
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      AntButton(
        text = stringResource(R.string.onboarding_skip),
        onClick = onSkip,
        modifier = Modifier.testTag("onboarding_skip"),
        type = AntButtonType.Link,
      )
    }
    AntTitle(stringResource(R.string.onboarding_title), level = 3)
    AntText(stringResource(R.string.onboarding_hint), type = AntTextType.Secondary)
    Spacer(Modifier.height(spacing.xs))
    ModeCard(
      icon = AntIcons.Dripper,
      title = stringResource(R.string.mode_basic),
      description = stringResource(R.string.mode_basic_hint),
      tag = "onboarding_basic",
      onClick = { onChoose(ExperienceMode.BASIC) },
    )
    ModeCard(
      icon = AntIcons.Chart,
      title = stringResource(R.string.mode_expert),
      description = stringResource(R.string.mode_expert_hint),
      tag = "onboarding_expert",
      onClick = { onChoose(ExperienceMode.EXPERT) },
    )
  }
}

/** "¿Qué molino usas?": el catálogo con buscador; elegir uno termina el onboarding con él. */
@Composable
private fun GrinderStep(
  catalog: List<Grinder>?,
  onBack: () -> Unit,
  onSkip: () -> Unit,
  onChoose: (Long) -> Unit,
  modifier: Modifier,
) {
  val spacing = AntTheme.spacing
  var query by rememberSaveable { mutableStateOf("") }
  val texts = rememberGrindTexts()
  val needle = fold(query.trim())
  val visible = remember(catalog, needle) {
    catalog.orEmpty().filter { needle.isEmpty() || fold(it.displayName).contains(needle) }
  }
  Column(
    modifier
      .fillMaxSize()
      .background(AntTheme.colors.bgLayout)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = spacing.base),
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      AntIconButton(
        AntIcons.ArrowLeft,
        contentDescription = stringResource(R.string.scale_back),
        onClick = onBack,
        modifier = Modifier.testTag("onboarding_grinder_back"),
      )
      AntButton(
        text = stringResource(R.string.onboarding_grinder_skip),
        onClick = onSkip,
        modifier = Modifier.testTag("onboarding_grinder_skip"),
        type = AntButtonType.Link,
      )
    }
    AntTitle(stringResource(R.string.onboarding_grinder_title), level = 3)
    AntText(stringResource(R.string.onboarding_grinder_hint), type = AntTextType.Secondary)
    AntInput(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().testTag("onboarding_grinder_search"),
      placeholder = stringResource(R.string.grinder_catalog_search_hint),
      prefix = { Icon(AntIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
    )
    when {
      catalog == null -> Box(Modifier.fillMaxWidth().padding(spacing.xl), contentAlignment = Alignment.Center) {
        AntSpin(Modifier.testTag("onboarding_grinder_loading"))
      }
      visible.isEmpty() -> AntEmpty(
        if (catalog.isEmpty()) stringResource(R.string.onboarding_grinder_empty) else stringResource(R.string.grinder_catalog_empty_search, query.trim()),
        modifier = Modifier.testTag("onboarding_grinder_empty"),
        icon = AntIcons.Grinder,
      )
      else -> LazyColumn(Modifier.weight(1f).testTag("onboarding_grinder_list")) {
        items(visible, key = { it.id }) { grinder ->
          AntListItem(
            title = grinder.displayName,
            description = grinder.unitsSummary(texts),
            modifier = Modifier.testTag("onboarding_grinder_${grinder.catalogKey ?: grinder.id}"),
            onClick = { onChoose(grinder.id) },
          )
        }
        item(key = "footer") {
          AntText(
            stringResource(R.string.onboarding_grinder_footer),
            Modifier.fillMaxWidth().padding(vertical = spacing.base),
            type = AntTextType.Secondary,
          )
        }
      }
    }
  }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, description: String, tag: String, onClick: () -> Unit) {
  val colors = AntTheme.colors
  AntCard(Modifier.fillMaxWidth().testTag(tag), onClick = onClick) {
    Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(48.dp).background(colors.primary.bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = colors.primary.base)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntTitle(title, level = 4)
        AntText(description, type = AntTextType.Secondary)
      }
    }
  }
}

@Preview(name = "Primer arranque · Claro", showBackground = true, heightDp = 640)
@Composable
private fun OnboardingLightPreview() {
  DotScaleTheme(darkTheme = false) { OnboardingScreen(catalog = emptyList(), onFinish = { _, _ -> }) }
}

@Preview(
  name = "Primer arranque · Oscuro",
  showBackground = true,
  backgroundColor = 0xFF000000,
  heightDp = 640,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OnboardingDarkPreview() {
  DotScaleTheme(darkTheme = true) { OnboardingScreen(catalog = emptyList(), onFinish = { _, _ -> }) }
}
