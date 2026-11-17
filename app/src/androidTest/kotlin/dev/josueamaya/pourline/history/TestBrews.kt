package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.BrewEffect
import dev.josueamaya.pourline.brew.BrewFit
import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.FinishedBrew
import dev.josueamaya.pourline.brew.PlannedAmounts
import dev.josueamaya.pourline.brew.Pour
import dev.josueamaya.pourline.brew.TimedSession
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.summarize
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.recipe.BuiltInRecipes
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import dev.josueamaya.pourline.protocol.WeightUnit
import java.util.Random

/** Preparaciones sintéticas y deterministas para los tests instrumentados. */
object TestBrews {

  /**
   * Una preparación de [totalMs] con tres vertidos (de 3 g/s y ruido de ±0,05 g, para no sobrestimar la
   * compresión). Las puntuaciones oscilan entre 60 y 95 salvo en la rampa de cada vertido.
   */
  fun finished(
    totalMs: Long = 240_000,
    seed: Long = 1,
    doseG: Double? = 20.0,
    reason: FinishReason = FinishReason.MANUAL,
    scored: Boolean = true,
    t0MonoMs: Long = 0,
  ): FinishedBrew {
    val random = Random(seed)
    val stepMs = 100L
    val pourLengthMs = totalMs / 5
    val starts = listOf(0L, totalMs * 2 / 5, totalMs * 3 / 5 + 5_000)
    val samples = ArrayList<BrewSample>()
    val pours = ArrayList<Pour>()
    var water = 0.0
    var pourStartWater = 0.0
    var t = 0L
    while (t <= totalMs) {
      val pourIndex = starts.indexOfFirst { t >= it && t < it + pourLengthMs }
      val pouring = pourIndex >= 0
      if (pouring && t == starts[pourIndex]) pourStartWater = water
      val flow = if (pouring) 3.0 + random.nextGaussian() * 0.2 else 0.0
      water += flow * stepMs / 1000.0
      val stability = if (scored && pouring && t - starts[pourIndex] >= 1_000) 60 + random.nextInt(36) else null
      samples += BrewSample(t, water + random.nextGaussian() * 0.05, flow, stability, valid = true)
      if (pouring && t + stepMs >= starts[pourIndex] + pourLengthMs) {
        pours += Pour(pours.size + 1, starts[pourIndex], t, water - pourStartWater)
      }
      t += stepMs
    }
    val summary = summarize(samples, pours, totalMs, reason, doseG)
    return FinishedBrew(t0MonoMs, samples, summary, doseG)
  }

  fun context(
    startedAtEpochMs: Long = 1_700_000_000_000,
    unit: WeightUnit = WeightUnit.GRAMS,
    simulated: Boolean = false,
    model: ScaleModel? = ScaleModel.DOT,
    target: UsedTarget? = null,
    details: BrewDetails? = null,
    coffee: UsedCoffee? = null,
    scaleless: Boolean = false,
  ) = BrewContext(startedAtEpochMs, unit, model, simulated, target, details, coffee, scaleless)

  /** Una preparación sin báscula, como la produce la sesión cronometrada: sin muestras ni vertidos. */
  fun scalelessFinished(
    totalTimeMs: Long = 115_000,
    amounts: PlannedAmounts = PlannedAmounts(doseG = 15.0, waterG = 250.0),
    t0MonoMs: Long = 0,
  ): FinishedBrew {
    val session = TimedSession()
    session.arm(amounts)
    session.start(t0MonoMs)
    return (session.finish(t0MonoMs + totalTimeMs).single() as BrewEffect.Finished).brew
  }

  /** El contexto de [scalelessFinished]: gramos, sin modelo y no simulada. */
  fun scalelessContext(
    startedAtEpochMs: Long = 1_700_000_000_000,
    target: UsedTarget? = null,
    details: BrewDetails? = null,
    coffee: UsedCoffee? = null,
  ) = context(startedAtEpochMs, WeightUnit.GRAMS, simulated = false, model = null, target = target, details = details, coffee = coffee, scaleless = true)

  /** Un café determinista para los tests de catálogo. */
  fun coffeeDraft(
    name: String = "Etiopía Guji",
    roaster: String? = "Café de Altura",
    roastDate: java.time.LocalDate? = java.time.LocalDate.of(2026, 11, 4),
    bagWeightG: Double? = 250.0,
    photo1: String? = null,
    photo2: String? = null,
  ) = dev.josueamaya.pourline.coffee.CoffeeDraft(
    name = name,
    roaster = roaster,
    originCountry = "Etiopía",
    originRegion = "Guji",
    varieties = listOf("Heirloom"),
    process = dev.josueamaya.pourline.coffee.CoffeeProcess.NATURAL,
    altitudeMinM = 1800,
    altitudeMaxM = 2000,
    roastLevel = dev.josueamaya.pourline.coffee.RoastLevel.LIGHT,
    roastDate = roastDate,
    bagWeightG = bagWeightG,
    price = 18.5,
    photo1 = photo1,
    photo2 = photo2,
  )

  /** Una referencia determinista: la "V60 clásica" de fábrica, con el ajuste que se indique. */
  fun usedTarget(
    type: ReferenceType = ReferenceType.RECIPE,
    id: Long = 1,
    name: String = "V60 clásica",
    score: Int? = 86,
    factor: Double = 1.0,
  ): UsedTarget {
    val curve = TargetCurve.fromRecipe(BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, FakeRecipeTexts())).scaled(factor)
    return UsedTarget(type, id, name, curve, BrewFit(score, curve.finalWaterG, curve.endMs))
  }
}
