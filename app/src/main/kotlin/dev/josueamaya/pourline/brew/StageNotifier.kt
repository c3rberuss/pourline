package dev.josueamaya.pourline.brew

/** Avisa al usuario de que empieza una etapa (vibración y sonido). Lo implementa la capa Android; en los tests, un falso. */
interface StageNotifier {
  fun stageStarted(stage: TargetStage)

  /** Un segundo de la cuenta regresiva previa a la preparación: [remainingS] > 0 es un tic y 0 es la salida. */
  fun countdownTick(remainingS: Int) = Unit

  /** Libera los recursos nativos. Se llama desde `ScaleViewModel.onCleared()`. */
  fun release()
}
