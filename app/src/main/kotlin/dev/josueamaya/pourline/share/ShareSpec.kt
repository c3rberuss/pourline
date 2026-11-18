package dev.josueamaya.pourline.share

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Las cuatro imágenes miden 360 × 450 dp, es decir 1080 × 1350 px (4:5) a [SHARE_DENSITY] (D1). En la tarjeta de receta es
 * el tamaño mínimo: el ancho es fijo y el alto crece con el contenido (enmienda 1, D33).
 */
val SHARE_SIZE = DpSize(360.dp, 450.dp)

/** Densidad fija con la que se dibujan, sea cual sea el teléfono. */
const val SHARE_DENSITY = 3f

/** Alto mínimo de la gráfica en las imágenes que la llevan (D2): 432 px. */
val SHARE_CHART_MIN_HEIGHT = 144.dp

/** Lado de la foto del café, en dp (336 px a [SHARE_DENSITY]). */
const val SHARE_PHOTO_DP = 112

/** Lado de un módulo del QR de la receta: 2 dp, es decir 6 px enteros a [SHARE_DENSITY] (enmienda 1, D30). */
val SHARE_QR_MODULE = 2.dp

/** Lado de la caja del QR de [modules] módulos con la zona de silencio: `(modules + 8) × 6` px a [SHARE_DENSITY]. */
fun qrBoxSize(modules: Int): Dp = SHARE_QR_MODULE * (modules + 2 * QR_QUIET_ZONE)

/** Alto fijo de la fila de un paso en la tarjeta de receta: así el alto solo depende del número de pasos (D10). */
val RECIPE_STEP_ROW_HEIGHT = 16.dp
