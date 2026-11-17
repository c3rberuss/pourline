package dev.josueamaya.pourline.share

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Las cuatro imágenes miden 360 × 450 dp, es decir 1080 × 1350 px (4:5) a [SHARE_DENSITY] (D1). */
val SHARE_SIZE = DpSize(360.dp, 450.dp)

/** Densidad fija con la que se dibujan, sea cual sea el teléfono. */
const val SHARE_DENSITY = 3f

/** Alto mínimo de la gráfica en las imágenes que la llevan (D2): 432 px. */
val SHARE_CHART_MIN_HEIGHT = 144.dp

/** Lado de la foto del café, en dp (336 px a [SHARE_DENSITY]). */
const val SHARE_PHOTO_DP = 112
