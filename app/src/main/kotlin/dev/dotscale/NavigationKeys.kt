package dev.dotscale

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Scan : NavKey

/** @property address MAC de la báscula, o `null` para la simulada. */
@Serializable data class Scale(val address: String?, val name: String) : NavKey

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
