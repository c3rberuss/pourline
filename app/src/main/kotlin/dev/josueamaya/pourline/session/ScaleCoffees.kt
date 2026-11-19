package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.PickedCoffee
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * De dónde saca `ScaleViewModel` el café elegido y las altas nuevas (D32). Lo implementa
 * [RepositoryScaleCoffees]; en los tests, un falso.
 */
interface ScaleCoffees {
  /** El café [id], o `null` si no existe o está archivado: un café así no se preselecciona (§4 del spec). */
  fun coffee(id: Long?): Flow<PickedCoffee?>

  /** El id de cada alta de café (D23). */
  val created: Flow<Long>
}

class RepositoryScaleCoffees(private val repository: CoffeeRepository) : ScaleCoffees {
  override fun coffee(id: Long?): Flow<PickedCoffee?> =
    if (id == null) {
      flowOf(null)
    } else {
      repository.pickable()
        .map { list -> list.firstOrNull { it.id == id }?.let { PickedCoffee(it.id, it.name, it.roaster, it.roastDate) } }
        .distinctUntilChanged()
    }

  override val created: Flow<Long> = repository.created
}

/** Sin catálogo: nunca hay café. Es el valor por defecto de `ScaleViewModel`. */
object NoScaleCoffees : ScaleCoffees {
  override fun coffee(id: Long?): Flow<PickedCoffee?> = flowOf(null)

  override val created: Flow<Long> = emptyFlow()
}
