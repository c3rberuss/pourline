package dev.josueamaya.pourline

import androidx.navigation3.runtime.NavKey
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.tools.FourSixParams
import kotlinx.serialization.Serializable

/** Raíz de la pestaña Preparar: la tarjeta de báscula y la configuración de la preparación. */
@Serializable data object Prepare : NavKey

/** La sesión guiada de preparación: pasos previos, vertido y resumen. Sin barra inferior. */
@Serializable data object Session : NavKey

@Serializable data object Settings : NavKey

@Serializable data object History : NavKey

@Serializable data class BrewDetail(val id: Long) : NavKey

@Serializable data class BrewEdit(val id: Long) : NavKey

@Serializable data object Recipes : NavKey

/**
 * `id == null` crea una receta; `fromBrewId` la prellena desde una preparación y `fourSix`, desde el generador 4:6
 * (los dos son "Guardar como receta").
 */
@Serializable data class RecipeEdit(val id: Long? = null, val fromBrewId: Long? = null, val fourSix: FourSixParams? = null) : NavKey

/** Elegir la preparación B para compararla con A. Al elegir, se sustituye por [BrewCompare] en la pila. */
@Serializable data class ComparePick(val aId: Long) : NavKey

@Serializable data class BrewCompare(val aId: Long, val bId: Long) : NavKey

@Serializable data object Coffees : NavKey

@Serializable data class CoffeeDetail(val id: Long) : NavKey

/** `id == null` crea un café; `fromCoffeeId` prellena una bolsa nueva a partir de otro ("Nueva bolsa"). */
@Serializable data class CoffeeEdit(val id: Long? = null, val fromCoffeeId: Long? = null) : NavKey

/** Ajustes → Molinos: mis molinos. */
@Serializable data object Grinders : NavKey

/** Ajustes → Molinos → Añadir molino: el catálogo con búsqueda. */
@Serializable data object GrinderCatalog : NavKey

@Serializable data class GrinderDetail(val id: Long) : NavKey

/**
 * `id == null` crea un molino propio; `fromId` abre una copia para personalizar ("Duplicar", D22);
 * `openRanges` abre la pestaña de rangos.
 */
@Serializable data class GrinderEdit(val id: Long? = null, val fromId: Long? = null, val openRanges: Boolean = false) : NavKey

/** El convertidor suelto (SPEC-006); los argumentos lo abren ya relleno con el origen, el método y el ajuste. */
@Serializable data class GrindConverter(val sourceId: Long? = null, val method: BrewMethod? = null, val value: Double? = null) : NavKey

/** Raíz de la pestaña Herramientas: la lista de herramientas. */
@Serializable data object Tools : NavKey

@Serializable data object RatioTool : NavKey

@Serializable data object FourSixTool : NavKey
