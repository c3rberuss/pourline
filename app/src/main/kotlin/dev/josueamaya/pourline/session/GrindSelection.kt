package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrindCardState
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.GrindSource
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.grindCardState
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import dev.josueamaya.pourline.settings.ReferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** El ajuste de molienda que se guarda en la preparación; el gestor lo congela al pasar a `Active` (D23). */
fun interface SessionGrind {
  fun current(): GrindSetting?
}

/** Sin molienda: lo que hay antes de que la app conecte la selección. */
object NoSessionGrind : SessionGrind {
  override fun current(): GrindSetting? = null
}

/** De dónde saca [GrindSelection] mis molinos y un molino por id. Lo implementa [RepositorySessionGrinders]. */
interface SessionGrinders {
  fun owned(): Flow<List<Grinder>>

  fun grinder(id: Long?): Flow<Grinder?>

  /** Los pares de calibración del molino [grinderId] (enmienda 1). */
  fun calibrations(grinderId: Long): Flow<List<GrindCalibration>>
}

class RepositorySessionGrinders(private val repository: GrinderRepository) : SessionGrinders {
  override fun owned(): Flow<List<Grinder>> = repository.owned()

  override fun grinder(id: Long?): Flow<Grinder?> = repository.grinder(id)

  override fun calibrations(grinderId: Long): Flow<List<GrindCalibration>> = repository.calibrations(grinderId)
}

/** Sin molinos: el valor por defecto para quien no los necesita. */
object NoSessionGrinders : SessionGrinders {
  override fun owned(): Flow<List<Grinder>> = flowOf(emptyList())

  override fun grinder(id: Long?): Flow<Grinder?> = flowOf(null)

  override fun calibrations(grinderId: Long): Flow<List<GrindCalibration>> = flowOf(emptyList())
}

/**
 * La selección de "Moler con" y el estado de la tarjeta de molienda (D23). Vive fuera del gestor: reacciona a la
 * referencia activa, a mis molinos, al predeterminado y a la elección del usuario, y no toca el camino caliente de la
 * sesión. La elección se guarda junto con la referencia: al cambiar de receta vuelve a la preselección del §3.7.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrindSelection(
  targets: Flow<ActiveTarget?>,
  private val grinders: SessionGrinders,
  defaults: DefaultGrinderStore,
  private val texts: GrindTexts,
  scope: CoroutineScope,
) : SessionGrind {
  /** La referencia con su origen de molienda, para ligar la elección a la receta. */
  private data class Source(val reference: ReferenceKey?, val grind: GrindSource?)

  /** La elección de "Moler con", ligada a la referencia con la que se hizo. */
  private data class Choice(val reference: ReferenceKey?, val grinderId: Long)

  private val latest = MutableStateFlow(Source(null, null))
  private val choice = MutableStateFlow<Choice?>(null)

  val state: StateFlow<GrindCardState> = targets
    .map { target -> Source(target?.key, target?.let { GrindSource(it.prefill.method, it.sourceGrind) }) }
    .distinctUntilChanged()
    .flatMapLatest { src ->
      latest.value = src
      // Al cambiar de receta la elección se olvida y vuelve la preselección (§3.7).
      if (choice.value?.reference != src.reference) choice.value = null
      val sourceId = src.grind?.setting?.grinderId
      combine(
        grinders.grinder(sourceId),
        grinders.owned(),
        defaults.defaultGrinderId,
        choice,
        // Los pares solo se emiten cuando cambia la tabla, no con cada lectura de la báscula (D50).
        if (sourceId == null) flowOf(emptyList()) else grinders.calibrations(sourceId),
      ) { sourceGrinder, owned, defaultId, picked, calibrations ->
        val chosenId = picked?.takeIf { it.reference == src.reference }?.grinderId
        grindCardState(src.grind, sourceGrinder, owned, chosenId, defaultId, calibrations)
      }
    }
    .distinctUntilChanged()
    .stateIn(scope, SharingStarted.Eagerly, grindCardState(null, null, emptyList(), null, defaults.defaultGrinderId.value))

  /** Elige el molino de "Moler con" para la referencia activa. */
  fun choose(grinderId: Long) {
    choice.value = Choice(latest.value.reference, grinderId)
  }

  override fun current(): GrindSetting? = state.value.snapshot(texts)
}
