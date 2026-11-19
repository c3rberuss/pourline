package dev.josueamaya.pourline.tools

import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.RecipeValidator
import dev.josueamaya.pourline.recipe.StageType
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Dulzor ↔ acidez: qué parte del primer 40 % del agua lleva el 1.er vertido. Un primer vertido corto deja la taza
 * más dulce y uno largo, más ácida. Con 300 g de agua, el 40 % son 120 g: 40/80, 50/70, 60/60, 70/50 y 80/40.
 */
enum class FourSixTaste(val firstShare: Double) {
  SWEET(1.0 / 3),
  MEDIUM_SWEET(5.0 / 12),
  BALANCED(0.5),
  MEDIUM_BRIGHT(7.0 / 12),
  BRIGHT(2.0 / 3),
}

/** Suave ↔ intensa: en cuántos vertidos iguales se reparte el 60 % restante del agua. Más vertidos, más intensidad. */
enum class FourSixStrength(val pours: Int) {
  LIGHTEST(1),
  LIGHT(2),
  STANDARD(3),
  STRONG(4),
  STRONGEST(5),
}

/**
 * Los ritmos de vertido que ofrece el generador, en gramos por segundo; [CUSTOM] es el que escribe el usuario. Ver
 * [FourSixParams.pourRateGPerS].
 */
enum class FourSixPace(val gramsPerSecond: Double?) {
  NORMAL(6.0),
  SLOW(4.0),
  SLOWEST(3.0),
  CUSTOM(null),
}

/** Lo que elige el usuario. Va en la clave de navegación del editor de recetas ("Guardar como receta"). */
@Serializable
data class FourSixParams(
  val doseG: Double,
  val ratio: Double,
  val taste: FourSixTaste = FourSixTaste.BALANCED,
  val strength: FourSixStrength = FourSixStrength.STANDARD,
  /**
   * Ritmo de vertido. Cada vertido dura lo que se tarda en verter sus gramos a este ritmo, y nunca menos que 60 g a
   * este ritmo, para que un ritmo más lento se note también en vertidos pequeños. El inicio de cada vertido no cambia
   * (cada 45 s): la espera es lo que queda hasta el siguiente.
   */
  val pourRateGPerS: Double = FourSix.DEFAULT_POUR_RATE_G_PER_S,
)

/** A qué parte del método pertenece un vertido: el 40 % del sabor o el 60 % de la intensidad. */
enum class FourSixPhase { TASTE, STRENGTH }

/** Un vertido: cuándo empieza, cuánto dura, cuánta agua añade y hasta qué peso acumulado llega. */
data class FourSixPour(
  val phase: FourSixPhase,
  val startMs: Long,
  val durationMs: Long,
  val pourG: Double,
  val totalG: Double,
) {
  val endMs: Long get() = startMs + durationMs
}

data class FourSixPlan(
  val params: FourSixParams,
  val pours: List<FourSixPour>,
  /** Desde el primer vertido hasta retirar el filtro. */
  val totalTimeMs: Long,
) {
  val totalWaterG: Double get() = pours.last().totalG
}

/**
 * El método 4:6 de Tetsu Kasuya. El agua total (café × ratio) se divide en un 40 % que define el sabor, en dos
 * vertidos ([FourSixTaste]), y un 60 % que define la intensidad, en uno a cinco vertidos iguales ([FourSixStrength]).
 * Cada vertido empieza 45 s después del anterior y el filtro se retira 30 s después del último: con 20 g a 1:15,
 * equilibrado e intensidad media, son los cinco vertidos de 60 g de la receta original, en 3:30. La duración de cada
 * vertido sale de [FourSixParams.pourRateGPerS]; si un vertido lento no deja al menos 10 s de espera, los siguientes se retrasan.
 *
 * Los pesos acumulados se redondean al gramo; cada vertido es la diferencia entre dos acumulados.
 */
object FourSix {
  const val MIN_DOSE_G = RecipeValidator.MIN_DOSE_G
  const val MAX_DOSE_G = RecipeValidator.MAX_DOSE_G
  const val MIN_RATIO = 10.0
  const val MAX_RATIO = 20.0
  const val MIN_POUR_RATE_G_PER_S = 2.0
  const val MAX_POUR_RATE_G_PER_S = 15.0

  /** 60 g en 10 s, como los vertidos de la receta predefinida. */
  const val DEFAULT_POUR_RATE_G_PER_S = 6.0
  const val MAX_WATER_G = RecipeValidator.MAX_WATER_G
  const val DEFAULT_RATIO = 15.0
  const val TASTE_SHARE = 0.4
  const val WATER_TEMP_C = 92.0

  /** Separación habitual entre el inicio de dos vertidos. */
  const val POUR_INTERVAL_MS = 45_000L

  /** Del inicio del último vertido a retirar el filtro. */
  const val DRAIN_MS = 30_000L

  /** Ningún vertido dura menos de lo que se tarda en verter estos gramos al ritmo elegido (10 s al ritmo normal). */
  const val MIN_POUR_G = 60.0
  private const val MAX_POUR_MS = RecipeValidator.MAX_DURATION_MS

  /** Con vertidos muy grandes (dosis altas), lo mínimo que se espera entre el final de uno y el siguiente. */
  private const val MIN_REST_MS = 10_000L

  /** Lo mínimo que se deja drenar tras el final del último vertido. */
  private const val MIN_DRAIN_AFTER_POUR_MS = 15_000L

  /** El plan de [params], o `null` si la dosis, el ratio o el agua total se salen de lo que admite una receta. */
  fun plan(params: FourSixParams): FourSixPlan? {
    if (!isValidDose(params.doseG) || !isValidRatio(params.ratio) || !isValidPourRate(params.pourRateGPerS)) return null
    val waterG = (params.doseG * params.ratio).roundToLong().toDouble()
    if (waterG > MAX_WATER_G) return null

    val n = params.strength.pours
    val shares = listOf(TASTE_SHARE * params.taste.firstShare, TASTE_SHARE) +
      (1..n).map { k -> TASTE_SHARE + (1 - TASTE_SHARE) * k / n }
    val totals = shares.mapIndexed { i, share -> if (i == shares.lastIndex) waterG else (waterG * share).roundToLong().toDouble() }

    var startMs = 0L
    val pours = totals.mapIndexed { i, totalG ->
      val pourG = totalG - (totals.getOrNull(i - 1) ?: 0.0)
      val durationMs = pourDurationMs(pourG, params.pourRateGPerS)
      val pour = FourSixPour(
        phase = if (i < 2) FourSixPhase.TASTE else FourSixPhase.STRENGTH,
        startMs = startMs,
        durationMs = durationMs,
        pourG = pourG,
        totalG = totalG,
      )
      startMs += maxOf(POUR_INTERVAL_MS, durationMs + MIN_REST_MS)
      pour
    }
    val last = pours.last()
    return FourSixPlan(params, pours, last.startMs + maxOf(DRAIN_MS, last.durationMs + MIN_DRAIN_AFTER_POUR_MS))
  }

  fun isValidDose(doseG: Double): Boolean = doseG.isFinite() && doseG in MIN_DOSE_G..MAX_DOSE_G

  fun isValidRatio(ratio: Double): Boolean = ratio.isFinite() && ratio in MIN_RATIO..MAX_RATIO

  fun isValidPourRate(gramsPerSecond: Double): Boolean =
    gramsPerSecond.isFinite() && gramsPerSecond in MIN_POUR_RATE_G_PER_S..MAX_POUR_RATE_G_PER_S

  /**
   * La receta del plan: cada vertido es un paso `POUR` y entre vertidos va un `WAIT` hasta el siguiente. Un último
   * `WAIT` cubre el drenado, así que la receta dura lo mismo que [FourSixPlan.totalTimeMs].
   */
  fun recipe(plan: FourSixPlan, texts: FourSixTexts): Recipe {
    val stages = ArrayList<RecipeStage>(plan.pours.size * 2)
    plan.pours.forEachIndexed { i, pour ->
      val isLast = i == plan.pours.lastIndex
      stages += RecipeStage(
        name = texts.pourName(i),
        startMs = pour.startMs,
        targetWaterG = pour.totalG,
        durationMs = pour.durationMs,
        type = StageType.POUR,
        instruction = texts.pourInstruction(pour.phase, isLast),
      )
      val waitEndMs = if (isLast) plan.totalTimeMs else plan.pours[i + 1].startMs
      stages += RecipeStage(
        name = null,
        startMs = pour.endMs,
        targetWaterG = pour.totalG,
        durationMs = waitEndMs - pour.endMs,
        type = StageType.WAIT,
        instruction = if (isLast) texts.drainInstruction() else texts.waitInstruction(),
      )
    }
    return Recipe(
      name = texts.recipeName(plan.params.taste, plan.params.strength),
      method = BrewMethod.V60,
      waterTempC = WATER_TEMP_C,
      notes = texts.notes(),
      doseG = plan.params.doseG,
      stages = stages,
    )
  }

  /** Cuánto dura verter [pourG] a [gramsPerSecond], en segundos enteros, entre 60 g a ese ritmo y 2 min. */
  fun pourDurationMs(pourG: Double, gramsPerSecond: Double): Long =
    (ceil(maxOf(pourG, MIN_POUR_G) / gramsPerSecond).toLong() * 1_000L).coerceAtMost(MAX_POUR_MS)
}

/** Textos de las recetas que genera [FourSix]. Lo implementa `ResourcesFourSixTexts`; en los tests, un falso. */
interface FourSixTexts {
  /** "4:6 · Dulce · Intensa". */
  fun recipeName(taste: FourSixTaste, strength: FourSixStrength): String

  /** "1.er vertido", con [index] desde 0. */
  fun pourName(index: Int): String

  fun pourInstruction(phase: FourSixPhase, last: Boolean): String

  fun waitInstruction(): String

  fun drainInstruction(): String

  fun notes(): String
}
