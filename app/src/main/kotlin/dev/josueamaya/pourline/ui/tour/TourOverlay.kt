package dev.josueamaya.pourline.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.settings.TourStore
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.LocalModalPresence
import dev.josueamaya.pourline.ui.components.ModalPresence
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.min

/** Cuánto se agranda el hueco sobre el ancla. */
private val HoleMargin = 8.dp

/** Separación entre el hueco y el globo, y alto de la flecha. */
private val BalloonGap = 8.dp
private val ArrowHeight = 8.dp
private val ArrowWidth = 16.dp

/** Ancho máximo del globo. */
private val BalloonMaxWidth = 360.dp

/**
 * La raíz de los recorridos guiados: provee el [TourCoordinator] y el registro de hojas y modales abiertos, y dibuja la
 * capa del recorrido encima de [content] (la navegación con su barra de pestañas), para poder resaltar también las
 * pestañas (D12). Mientras hay un paso en pantalla, [content] se oculta a los servicios de accesibilidad.
 */
@Composable
fun TourRoot(store: TourStore, content: @Composable () -> Unit) {
  val scope = rememberCoroutineScope()
  val hint = rememberUpdatedState(stringResource(R.string.tour_skipped_hint))
  val coordinator = remember(store) { TourCoordinator(store, scope) { hint.value } }
  val modals = remember { ModalPresence() }
  CompositionLocalProvider(LocalTourCoordinator provides coordinator, LocalModalPresence provides modals) {
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().semantics { if (coordinator.presentation != null) hideFromAccessibility() }) { content() }
      TourLayer(coordinator)
    }
  }
}

/** Marca el contenido como la barra de pestañas: sus anclas van al registro de la raíz, no al de una pantalla (D26). */
@Composable
fun TourBar(content: @Composable () -> Unit) {
  val coordinator = LocalTourCoordinator.current
  CompositionLocalProvider(LocalTourAnchors provides coordinator?.barAnchors, content = content)
}

/** El paso en pantalla (velo, hueco y globo) y el host del aviso de Saltar. */
@Composable
internal fun TourLayer(coordinator: TourCoordinator) {
  Box(Modifier.fillMaxSize()) {
    coordinator.presentation?.let { TourSpotlight(it) }
    AntMessageHost(
      coordinator.messages,
      Modifier
        .align(Alignment.TopCenter)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    )
  }
}

/** Dónde quedó el globo, para que el dibujo del velo pinte la flecha. */
@Stable
private class PlacementHolder {
  var placement by mutableStateOf<BalloonPlacement?>(null)
}

/**
 * El velo con un hueco redondeado sobre el ancla y el globo con la explicación (D14 a D16). El velo se traga los toques,
 * y "Atrás" del sistema retrocede un paso (D18). Los límites del ancla se leen en el layout y en el dibujo, no en la
 * composición: un movimiento solo recoloca y redibuja la capa.
 */
@Composable
private fun TourSpotlight(presentation: TourPresentation) {
  val current by rememberUpdatedState(presentation)
  BackHandler { current.onAction(TourAction.Back) }

  val colors = AntTheme.colors
  val density = LocalDensity.current
  val insets = WindowInsets.safeDrawing
  val holder = remember { PlacementHolder() }
  var origin by remember { mutableStateOf(Offset.Zero) }
  val margin = with(density) { HoleMargin.toPx() }
  val arrowHeight = with(density) { ArrowHeight.toPx() }
  val arrowWidth = with(density) { ArrowWidth.toPx() }
  val radius = with(density) { (AntTheme.radius.lg + HoleMargin).toPx() }
  val sideMargin = AntTheme.spacing.base
  val arrowInset = AntTheme.radius.lg + ArrowWidth / 2
  val scrim = colors.bgMask
  val arrowColor = colors.bgElevated

  Layout(
    content = { Balloon(presentation) },
    modifier = Modifier
      .fillMaxSize()
      .testTag("tour_overlay")
      .onGloballyPositioned { origin = it.positionInRoot() }
      // Se tragan los toques que no recogen los botones del globo (hijos, que los reciben antes en el pase Main).
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent().changes.forEach { it.consume() }
        }
      }
      .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
      .drawBehind {
        drawRect(scrim)
        val hole = spotlight(current.bounds(), origin, size, margin)
        if (hole != null) {
          drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(radius), blendMode = BlendMode.Clear)
        }
        val placement = holder.placement
        val arrow = placement?.arrow
        if (placement != null && arrow != null) drawArrow(arrow, placement.side, arrowWidth, arrowHeight, arrowColor)
      },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val safe = Rect(
      insets.getLeft(this, layoutDirection).toFloat(),
      insets.getTop(this).toFloat(),
      (width - insets.getRight(this, layoutDirection)).toFloat(),
      (height - insets.getBottom(this)).toFloat(),
    )
    val balloonWidth = min((safe.width - 2 * sideMargin.roundToPx()).toInt(), BalloonMaxWidth.roundToPx()).coerceAtLeast(0)
    val balloon = measurables.single().measure(Constraints(minWidth = balloonWidth, maxWidth = balloonWidth, maxHeight = safe.height.toInt().coerceAtLeast(0)))
    layout(width, height) {
      val hole = spotlight(current.bounds(), origin, Size(width.toFloat(), height.toFloat()), margin)
      // El globo no se pega a los lados de la pantalla: la misma separación con la que se calcula su ancho.
      val sideMarginPx = sideMargin.toPx()
      val placement = placeBalloon(
        hole = hole,
        balloon = Size(balloon.width.toFloat(), balloon.height.toFloat()),
        safe = Rect(safe.left + sideMarginPx, safe.top, safe.right - sideMarginPx, safe.bottom),
        gap = BalloonGap.toPx(),
        arrowInset = arrowInset.toPx(),
      )
      if (holder.placement != placement) holder.placement = placement
      balloon.place(placement.offset.round())
    }
  }
}

/** El hueco: los límites del ancla agrandados [margin], pasados a las coordenadas de la capa y recortados a ella; `null` si no hay. */
private fun spotlight(bounds: Rect?, origin: Offset, size: Size, margin: Float): Rect? {
  if (bounds == null) return null
  val hole = bounds.translate(-origin.x, -origin.y).inflate(margin).intersect(Rect(Offset.Zero, size))
  return if (hole.width > 0f && hole.height > 0f) hole else null
}

/** Un triángulo del color del globo, con la base en el borde del globo ([base]) y la punta hacia el hueco. */
private fun DrawScope.drawArrow(base: Offset, side: BalloonSide, width: Float, height: Float, color: Color) {
  val half = width / 2
  val path = Path().apply {
    when (side) {
      BalloonSide.BELOW -> { moveTo(base.x - half, base.y); lineTo(base.x + half, base.y); lineTo(base.x, base.y - height) }
      BalloonSide.ABOVE -> { moveTo(base.x - half, base.y); lineTo(base.x + half, base.y); lineTo(base.x, base.y + height) }
      BalloonSide.END -> { moveTo(base.x, base.y - half); lineTo(base.x, base.y + half); lineTo(base.x - height, base.y) }
      BalloonSide.START -> { moveTo(base.x, base.y - half); lineTo(base.x, base.y + half); lineTo(base.x + height, base.y) }
      BalloonSide.CENTER -> return
    }
    close()
  }
  drawPath(path, color)
}

/** La tarjeta del globo: título y contador, texto y los botones Saltar, Anterior y Siguiente o Listo (D15 y D17). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Balloon(presentation: TourPresentation) {
  val colors = AntTheme.colors
  val spacing = AntTheme.spacing
  val shape = RoundedCornerShape(AntTheme.radius.lg)
  val title = stringResource(presentation.step.title)
  val isLast = presentation.position == presentation.count

  Column(
    Modifier
      .testTag("tour_balloon")
      .shadow(6.dp, shape)
      .background(colors.bgElevated, shape)
      .padding(spacing.base)
      .semantics { paneTitle = title },
    verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    // El título y el texto se anuncian juntos cuando cambia el paso.
    Column(
      Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
      verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Top) {
        Text(
          title,
          Modifier.weight(1f).testTag("tour_title").semantics { heading() },
          color = colors.text,
          style = AntTheme.typography.heading5,
        )
        Text(
          stringResource(R.string.tour_counter, presentation.position, presentation.count),
          Modifier.testTag("tour_counter"),
          color = colors.textSecondary,
          style = AntTheme.typography.textSM,
        )
      }
      Text(
        stringResource(presentation.step.body),
        Modifier.testTag("tour_body"),
        color = colors.textSecondary,
        style = AntTheme.typography.text,
      )
    }
    FlowRow(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing.xs),
      verticalArrangement = Arrangement.spacedBy(spacing.xs),
      itemVerticalAlignment = Alignment.CenterVertically,
    ) {
      AntButton(
        stringResource(R.string.tour_skip),
        { presentation.onAction(TourAction.Skip) },
        Modifier.testTag("tour_skip"),
        type = AntButtonType.Link,
        size = AntSize.Small,
      )
      Spacer(Modifier.weight(1f))
      if (presentation.position > 1) {
        AntButton(
          stringResource(R.string.tour_prev),
          { presentation.onAction(TourAction.Prev) },
          Modifier.testTag("tour_prev"),
          size = AntSize.Small,
        )
      }
      AntButton(
        stringResource(if (isLast) R.string.tour_done else R.string.tour_next),
        { presentation.onAction(TourAction.Next) },
        Modifier.testTag("tour_next"),
        type = AntButtonType.Primary,
        size = AntSize.Small,
      )
    }
  }
}
