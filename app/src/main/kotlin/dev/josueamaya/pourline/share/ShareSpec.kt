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

/** Alto de las imágenes de receta con muchos pasos: 360 × 640 dp, es decir 1080 × 1920 px (9:16). */
val SHARE_SIZE_TALL = DpSize(360.dp, 640.dp)

/** Lado del QR de la receta, zona de silencio incluida: 432 px a [SHARE_DENSITY] (D9). */
val SHARE_QR_SIZE = 144.dp

/** Alto fijo de la fila de un paso en la tarjeta de receta: así el tamaño solo depende del número de pasos (D10). */
val RECIPE_STEP_ROW_HEIGHT = 16.dp

/** Pasos que caben en la tarjeta de 4:5 junto a la cabecera y el QR; a partir del siguiente se usa 9:16 (D10). */
const val RECIPE_CARD_COMPACT_MAX_STAGES = 10

/** 4:5 hasta [RECIPE_CARD_COMPACT_MAX_STAGES] pasos y 9:16 a partir del siguiente. Todos los pasos se muestran siempre. */
fun recipeCardSize(stageCount: Int): DpSize = if (stageCount <= RECIPE_CARD_COMPACT_MAX_STAGES) SHARE_SIZE else SHARE_SIZE_TALL
