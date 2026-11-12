package dev.dotscale

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Scan : NavKey

/** @property address MAC de la báscula, o `null` para la simulada. */
@Serializable data class Scale(val address: String?, val name: String) : NavKey

@Serializable data object Settings : NavKey
