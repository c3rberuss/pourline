package dev.dotscale.ui.onboarding

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTitle
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme

/**
 * La elección del primer arranque: Básico o Experto. [onChoose] recibe el modo elegido ("Saltar" elige Básico);
 * quien la use guarda el modo y marca el primer arranque como hecho.
 */
@Composable
fun OnboardingScreen(onChoose: (ExperienceMode) -> Unit, modifier: Modifier = Modifier) {
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
        onClick = { onChoose(ExperienceMode.BASIC) },
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
  DotScaleTheme(darkTheme = false) { OnboardingScreen(onChoose = {}) }
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
  DotScaleTheme(darkTheme = true) { OnboardingScreen(onChoose = {}) }
}
