package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.scale.ScaleConnection

/** A qué báscula se conecta el gestor: una real por BLE o la simulada. */
sealed interface ScaleTarget {
  val name: String

  data class Real(val address: String, override val name: String) : ScaleTarget

  data class Simulated(override val name: String) : ScaleTarget
}

/** Crea la conexión de un destino. La real es `BleScaleConnection`; en los tests, un falso. */
fun interface ScaleConnectionFactory {
  fun create(target: ScaleTarget): ScaleConnection
}
