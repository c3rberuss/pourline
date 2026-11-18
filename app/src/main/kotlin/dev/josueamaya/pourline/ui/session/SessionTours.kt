package dev.josueamaya.pourline.ui.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.ui.tour.Tour
import dev.josueamaya.pourline.ui.tour.Tours

/**
 * El recorrido que toca a la sesión según su estado (D27): el resumen al terminar, el de la sesión sin báscula cuando
 * está lista y el de los pasos previos con báscula. Sin ninguno de ellos (durante el vertido, o una sesión restaurada
 * sin preparación), `null`.
 */
internal fun sessionTour(ui: ScaleUiState): Tour? = when (ui.brew.phase) {
  is BrewPhase.Finished -> Tours.summary
  BrewPhase.Armed -> when {
    ui.scaleless -> Tours.sessionScaleless
    ui.prep != null -> Tours.sessionPrep
    else -> null
  }
  is BrewPhase.Active -> null
}

/** Nunca durante la cuenta regresiva ni el vertido: un recorrido abierto se cierra sin marcar al dejar de cumplirse (D24). */
internal fun sessionTourEnabled(ui: ScaleUiState): Boolean = ui.countdownS == null && ui.brew.phase !is BrewPhase.Active
