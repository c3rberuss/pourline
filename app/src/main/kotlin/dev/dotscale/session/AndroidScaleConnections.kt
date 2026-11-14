package dev.dotscale.session

import android.content.Context
import dev.dotscale.ble.BleScaleConnection
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.sim.SimulatedScaleConnection

/** Fábrica de producción: BLE para una báscula real y la simulación para la simulada. */
class AndroidScaleConnections(context: Context) : ScaleConnectionFactory {
  private val appContext = context.applicationContext

  override fun create(target: ScaleTarget): ScaleConnection = when (target) {
    is ScaleTarget.Real -> BleScaleConnection(appContext, target.address)
    is ScaleTarget.Simulated -> SimulatedScaleConnection()
  }
}
