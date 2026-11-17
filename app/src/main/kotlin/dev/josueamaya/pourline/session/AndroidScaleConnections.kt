package dev.josueamaya.pourline.session

import android.content.Context
import dev.josueamaya.pourline.ble.BleScaleConnection
import dev.josueamaya.pourline.scale.ScaleConnection
import dev.josueamaya.pourline.sim.SimulatedScaleConnection

/** Fábrica de producción: BLE para una báscula real y la simulación para la simulada. */
class AndroidScaleConnections(context: Context) : ScaleConnectionFactory {
  private val appContext = context.applicationContext

  override fun create(target: ScaleTarget): ScaleConnection = when (target) {
    is ScaleTarget.Real -> BleScaleConnection(appContext, target.address)
    is ScaleTarget.Simulated -> SimulatedScaleConnection()
  }
}
