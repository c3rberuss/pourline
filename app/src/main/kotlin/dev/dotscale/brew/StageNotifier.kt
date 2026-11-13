package dev.dotscale.brew

/** Avisa al usuario de que empieza una etapa (vibración y sonido). Lo implementa la capa Android; en los tests, un falso. */
interface StageNotifier {
  fun stageStarted(stage: TargetStage)

  /** Libera los recursos nativos. Se llama desde `ScaleViewModel.onCleared()`. */
  fun release()
}
