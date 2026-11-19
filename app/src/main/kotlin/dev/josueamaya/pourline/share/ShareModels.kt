package dev.josueamaya.pourline.share

import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import java.time.LocalDate

/**
 * Lo que lleva la imagen de un café. Sin precio, peso de bolsa, stock ni fecha de apertura (§3.7 del spec): esos
 * datos ni siquiera existen aquí, así que ninguna tarjeta puede dibujarlos.
 */
data class CoffeeShareData(
  val name: String,
  val roaster: String?,
  /** Nombre del archivo de la foto 1; la imagen ya decodificada viaja en [ShareRequest.Coffee]. */
  val photo: String?,
  val country: String?,
  val region: String?,
  val farm: String?,
  val altitudeMinM: Int?,
  val altitudeMaxM: Int?,
  val varieties: List<String>,
  val process: CoffeeProcess?,
  val processOther: String?,
  val roastLevel: RoastLevel?,
  val roastDate: LocalDate?,
  /** Días desde el tueste hasta hoy; `null` sin fecha o con un tueste posterior. */
  val daysSinceRoast: Int?,
  val roasterNotes: String?,
)

/** Copia de [CoffeeEntity] sin los datos que no se comparten. */
fun CoffeeEntity.toShareData(daysSinceRoast: Int?) = CoffeeShareData(
  name = name,
  roaster = roaster,
  photo = photo1,
  country = originCountry,
  region = originRegion,
  farm = farm,
  altitudeMinM = altitudeMinM,
  altitudeMaxM = altitudeMaxM,
  varieties = varieties,
  process = process,
  processOther = processOther,
  roastLevel = roastLevel,
  roastDate = roastDate,
  daysSinceRoast = daysSinceRoast,
  roasterNotes = roasterNotes,
)

/** Lado de una preparación en una comparación; da el color del punto del pie. */
enum class ShareSide { A, B }

/**
 * Una línea del pie de las imágenes de gráficas. [days] son los días desde el tueste hasta la preparación: uno, o
 * dos si la comparación usa el mismo café en dos días distintos. [side] es `null` en las imágenes de una sola
 * preparación y cuando la línea cubre las dos de la comparación.
 */
data class ShareFooterLine(val name: String, val roaster: String?, val days: List<Int>, val side: ShareSide?)

/** El café vinculado de una preparación, tal como lo necesita el pie. */
data class FooterCoffee(val id: Long, val name: String, val roaster: String?, val daysSinceRoast: Int?)

/** El pie de una imagen de una sola preparación: una línea sin lado con su café, o ninguna sin café vinculado. */
fun footerLines(coffee: FooterCoffee?): List<ShareFooterLine> =
  if (coffee == null) emptyList() else listOf(ShareFooterLine(coffee.name, coffee.roaster, listOfNotNull(coffee.daysSinceRoast), side = null))

/**
 * El pie de la comparación (D4). Sin ninguna preparación con café vinculado, no hay pie; con el mismo café en las
 * dos, una línea sin lado con los dos días; con uno solo vinculado, una línea con su lado; con dos cafés
 * distintos, una línea por preparación. Una preparación con un texto antiguo y sin vínculo llega como `null`.
 */
fun footerLines(a: FooterCoffee?, b: FooterCoffee?): List<ShareFooterLine> = when {
  a == null && b == null -> emptyList()
  a != null && b != null && a.id == b.id ->
    listOf(ShareFooterLine(a.name, a.roaster, listOfNotNull(a.daysSinceRoast, b.daysSinceRoast).distinct(), side = null))
  else -> listOfNotNull(
    a?.let { ShareFooterLine(it.name, it.roaster, listOfNotNull(it.daysSinceRoast), ShareSide.A) },
    b?.let { ShareFooterLine(it.name, it.roaster, listOfNotNull(it.daysSinceRoast), ShareSide.B) },
  )
}

/**
 * El resumen en Scale y el detalle de una preparación. [subtitle] y [fitScore] van solo si hay dato; [notes] solo en
 * el detalle. [curveError] es una curva ilegible: en el hueco de la gráfica va el texto de error.
 *
 * Con [scaleless] (SPEC-007) no hay curva real ni estabilidad: la imagen lleva la marca "Sin báscula" y, en lugar de la
 * gráfica, la dosis, el agua, el ratio y el tiempo de lo planeado. [doseG] es la dosis del plan, en gramos (`null` si no
 * la había); [summary] aporta el agua, el ratio y el tiempo.
 */
data class BrewShareData(
  val samples: List<BrewSample>,
  val summary: BrewSummary,
  val unit: WeightUnit,
  val target: TargetCurve?,
  val title: String,
  val subtitle: String?,
  val fitScore: Int?,
  val rating: Int?,
  val notes: String?,
  val footer: List<ShareFooterLine>,
  val curveError: Boolean = false,
  val scaleless: Boolean = false,
  val doseG: Double? = null,
)

/** La comparación de A y B. [titleA] y [titleB] son "nombre · fecha y hora" (o solo la fecha y hora). */
data class CompareShareData(
  val a: BrewRecord,
  val b: BrewRecord,
  val titleA: String,
  val titleB: String,
  val footer: List<ShareFooterLine>,
)

/**
 * Lo que lleva la imagen de una receta. [grindText] es el texto de la molienda tal como lo muestra el editor (o la
 * etiqueta del molino eliminado) y [qr] la matriz del QR; sin [qr] la tarjeta no lleva QR ni su texto. Las notas y las
 * instrucciones de los pasos viajan en [recipe] pero la tarjeta no las dibuja.
 */
data class RecipeShareData(val recipe: Recipe, val grindText: String?, val unit: WeightUnit, val qr: QrMatrix?)

/** Qué se comparte: da el nombre del archivo. */
sealed interface ShareTarget {
  data class Coffee(val id: Long) : ShareTarget

  data class Brew(val id: Long) : ShareTarget

  data class Compare(val aId: Long, val bId: Long) : ShareTarget

  data class Recipe(val id: Long) : ShareTarget

  /** El resumen de la sesión recién terminada (no tiene por qué estar guardada). */
  data object Summary : ShareTarget
}

/** `coffee-<id>.png`, `brew-<id>.png`, `compare-<a>-<b>.png`, `recipe-<id>.png` y `summary.png`. */
fun shareFileName(target: ShareTarget): String = when (target) {
  is ShareTarget.Coffee -> "coffee-${target.id}.png"
  is ShareTarget.Brew -> "brew-${target.id}.png"
  is ShareTarget.Compare -> "compare-${target.aId}-${target.bId}.png"
  is ShareTarget.Recipe -> "recipe-${target.id}.png"
  ShareTarget.Summary -> "summary.png"
}
