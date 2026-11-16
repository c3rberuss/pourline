package dev.dotscale

import androidx.navigation3.runtime.NavKey
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

/** `id == null` crea una receta; `fromBrewId` la prellena desde una preparación ("Guardar como receta"). */
@Serializable data class RecipeEdit(val id: Long? = null, val fromBrewId: Long? = null) : NavKey

/** Elegir la preparación B para compararla con A. Al elegir, se sustituye por [BrewCompare] en la pila. */
@Serializable data class ComparePick(val aId: Long) : NavKey

@Serializable data class BrewCompare(val aId: Long, val bId: Long) : NavKey

@Serializable data object Coffees : NavKey

@Serializable data class CoffeeDetail(val id: Long) : NavKey

/** `id == null` crea un café; `fromCoffeeId` prellena una bolsa nueva a partir de otro ("Nueva bolsa"). */
@Serializable data class CoffeeEdit(val id: Long? = null, val fromCoffeeId: Long? = null) : NavKey
