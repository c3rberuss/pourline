package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.protocol.WeightUnit

/** 1 oz = 28,349523125 g. */
const val GRAMS_PER_OUNCE = 28.349523125

fun toGrams(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.OUNCES) value * GRAMS_PER_OUNCE else value

fun fromGrams(valueG: Double, unit: WeightUnit): Double = if (unit == WeightUnit.OUNCES) valueG / GRAMS_PER_OUNCE else valueG
