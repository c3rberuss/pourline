package dev.josueamaya.pourline.brew

/** Avisa al usuario de que empieza una etapa (vibración y sonido). Lo implementa la capa Android; en los tests, un falso. */
interface StageNotifier {
  fun stageStarted(stage: TargetStage)

  /** Un segundo de la cuenta regresiva previa a la preparación: [remainingS] > 0 es un tic y 0 es la salida. */
  fun countdownTick(remainingS: Int) = Unit

  /** Un segundo de la cuenta atrás hacia el siguiente paso ([remainingS] = 5..1); al llegar a 0 suena [stageStarted]. */
  fun nextStepTick(remainingS: Int) = Unit

  /** Libera los recursos nativos. Se llama desde `ScaleViewModel.onCleared()`. */
  fun release()
}
