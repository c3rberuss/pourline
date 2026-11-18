# SPEC-008: Recorrido guiado del primer uso

| Campo | Valor |
|---|---|
| Estado | Implementado; textos aprobados por el usuario el 2026-11-17; pendiente de code-review. Plan: [`plan-recorrido-guiado.md`](plan-recorrido-guiado.md) |
| Fecha | 2026-11-17 |
| Rama | `feat/guided-tour`, creada desde `develop` (`1015b12`) |
| Deriva de | Petición del usuario: que quien abra la app la conozca sin explicación externa |
| Módulos afectados | `:app`: `ui/tour` (nuevo), `settings`, `ui/onboarding`, `Navigation.kt`, todas las pantallas con recorrido (§3.7) |
| Specs relacionados | [SPEC-005](spec-navegacion-modos.md) (pestañas, modos y primer arranque), [SPEC-006](spec-molinos.md), [SPEC-007](spec-sin-bascula.md) |

## 1. Resumen y Contexto

- **Problema:**
  - La app tiene cinco pestañas, unas veinte pantallas, dos modos (Básico y Experto) y funciones
    que no se descubren a simple vista: el simulador de báscula, la sesión sin báscula, la lectura
    de etiquetas, la conversión de molienda, el generador 4:6 y la comparación de preparaciones.
  - El primer arranque actual (`OnboardingScreen`) solo pregunta el modo y el molino; no explica
    cómo se usa la app.
- **Solución:**
  - Un **recorrido guiado con resaltado sobre la UI real**:
    - un velo oscurece la pantalla;
    - deja visible el elemento que se explica;
    - un globo de texto muestra el título, la explicación, el contador "n/N" y los botones
      Anterior, Siguiente (o Listo) y Saltar.
  - Un **recorrido general** en Preparar, al terminar el primer arranque. Explica Preparar y
    presenta cada pestaña señalándola en la barra.
  - **Mini-recorridos por pantalla**: las cinco pestañas más las pantallas con funciones no
    obvias (§3.7). Cada uno aparece la primera vez que se abre la pantalla.
  - **Lo visto se guarda por paso, no por pantalla.** Al abrir una pantalla se muestran solo los
    pasos que aún no se vieron y que ahora son visibles. Así:
    - al pasar a Experto, cada pantalla presenta solo lo que se desbloqueó;
    - un paso omitido por lista vacía aparece cuando la lista tiene contenido;
    - los pasos que se añadan en el futuro se presentan solos.
  - **"Ver recorrido" en Ajustes** borra todo lo visto y lleva a Preparar.
- **Éxito:** cuando un usuario entra a la app o a una pantalla con recorrido por primera vez, ve
  su recorrido. Tras completarlo o saltarlo no vuelve a verlo, salvo pasos nuevos o "Ver
  recorrido".
- **Alcance (In):**
  - Componente de resaltado y globo (`ui/tour`), registro de anclas y lógica de selección de pasos.
  - Persistencia de los pasos vistos y reset desde Ajustes.
  - Los recorridos y pasos de §3.7, con sus textos en español (`values`) e inglés (`values-en`).
  - Integración con la navegación, los modos y la sesión (con báscula y sin báscula).
  - Los usuarios que ya pasaron el primer arranque también ven los recorridos. La app aún no se
    distribuye.
- **Alcance (Out):**
  - Cambios en el primer arranque (modo y molino) más allá de dar paso al recorrido general.
  - Un carrusel de bienvenida a pantalla completa.
  - Que tocar el elemento resaltado ejecute su acción o avance el recorrido.
  - Recorridos dentro de hojas inferiores o modales: `ConnectionSheet`, `TargetPicker`,
    `CoffeePicker`, `SessionOptionsSheet`, `DialLegendSheet`.
  - Recorridos de Molinos (lista, catálogo, detalle y edición), de "Elegir preparaciones a
    comparar" y de editar una preparación.
  - Un recorrido durante el vertido.
  - Telemetría o analítica de uso.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario nuevo, al terminar el primer arranque quiero que la app me señale cómo
  conectar la báscula (o usar el simulador o la sesión sin báscula), elegir receta y café y
  empezar, para preparar mi primer café sin ayuda.
- **US-2:** Como usuario, la primera vez que abro una pestaña o una pantalla clave, quiero una
  explicación breve de sus funciones sobre los elementos reales.
- **US-3:** Como usuario que pasa a modo Experto, quiero que cada pantalla me muestre solo lo que
  se desbloqueó, sin repetir lo que ya vi.
- **US-4:** Como usuario con prisa, quiero saltar un recorrido sin que eso apague los de otras
  pantallas.
- **US-5:** Como usuario, quiero volver a ver todos los recorridos desde Ajustes.
- **US-6:** Como usuario que va a preparar café, no quiero que un recorrido me tape la sesión
  mientras viertes ni durante la cuenta regresiva.

## 3. Especificación Técnica

### 3.1 Anclas

- Nuevo `Modifier.tourAnchor(id: String, enabled: Boolean = true)` en `ui/tour`. Registra los
  límites del elemento en la raíz (`onGloballyPositioned` / `boundsInRoot`) en un
  `TourAnchorRegistry` provisto por un `CompositionLocal`. Al salir de la composición, el ancla
  se desregistra. Con `enabled = false` no hace nada: así una sola fila de una lista, la primera,
  lleva el ancla (§3.7). Fuera de la navegación (pantallas compuestas sueltas, previews) no hace
  nada.
- El registro se limpia por pantalla: cada entrada de la navegación tiene el suyo
  (`TourScreen`), y dos pantallas de la pila no comparten anclas. La barra de pestañas tiene otro
  (`TourBar`), para los pasos que la señalan.
- **Anclas dentro de una `LazyColumn`:** una lista perezosa no compone lo que no se ve, así que
  un ancla fuera de pantalla no estaría registrada. `TourLazyAnchor(id, state, present)` declara
  el ancla como presente mientras `present`, aunque su elemento no esté compuesto, y el
  recorrido la busca desplazando la lista (hasta 30 pasos del 80 % del alto) antes de dibujar el
  hueco. Si tras ello no tiene límites válidos, el paso se trata como ausente (§4). Las cuatro
  pantallas con lista (Cafés, detalle de café, Recetas e Historial) elevan su `LazyListState`.
- Convención: el `id` del ancla es el `testTag` existente del elemento cuando lo tiene (§3.7).
  Cuando no lo tiene, se crea un `testTag` y un ancla con el mismo nombre, por ejemplo la tarjeta
  de dosis de Preparar.
- Hoy la app no tiene ningún registro de posiciones (`onGloballyPositioned`, `boundsInRoot`) ni
  componente de globo o tooltip; ambos son nuevos.

### 3.2 Modelo de pasos

```kotlin
data class TourStep(
  val id: String,              // único en toda la app, p. ej. "prepare.scale"
  val anchor: String?,         // id de ancla; null = paso centrado sin resaltado
  @StringRes val title: Int,
  @StringRes val body: Int,
  val expertOnly: Boolean = false,
)

data class Tour(val id: String, val steps: List<TourStep>)
```

- Cada recorrido de §3.7 es un `Tour` declarado en código, en el orden de la tabla.
- Lógica pura, sin Android y probada en JVM. Ejemplo de firma:
  `pendingSteps(tour, seen: Set<String>, mode: ExperienceMode, anchors: Set<String>): List<TourStep>`.
- Devuelve, en orden, los pasos que cumplen las tres condiciones:
  - no están en `seen`;
  - no son `expertOnly`, o el modo es Experto;
  - tienen el ancla presente en `anchors`, o no tienen ancla (`anchor == null`).
- Un paso centrado sin ancla solo se muestra si el recorrido tiene, además, al menos un paso con
  ancla pendiente. Así una bienvenida o una explicación general no aparece suelta en una visita
  posterior.
- El contador "n/N" se calcula sobre la lista pendiente de esa apertura, no sobre el recorrido
  completo.

### 3.3 Persistencia

- Nueva clase `TourSettings` en `settings/`, en el mismo archivo `"settings"`
  (`BrewSettings.PREFS_NAME`) que `ExperienceSettings`. Una sola instancia por proceso, en
  `PourlineApp`, expuesta en `AppDependencies`.
- Clave `tour_seen`: `Set<String>` con los `TourStep.id` vistos. Expone
  `seen: StateFlow<Set<String>>`, `markSeen(ids: Collection<String>)` y `reset()`.
- Es independiente de `onboarding_done`: las instalaciones existentes empiezan con el conjunto
  vacío y ven los recorridos.
- Un id guardado que ya no existe en ningún recorrido se ignora.
- Para los tests instrumentados, `TestAppDependencies` gana un ayudante análogo a
  `skipOnboarding()`, por ejemplo `skipTours()`, que marca todos los pasos como vistos. Lo usan
  los tests existentes que no prueban el recorrido.

### 3.4 Overlay y globo

- `TourOverlay` en `ui/tour`. Se dibuja encima del contenido de la pantalla y por encima de la
  barra de pestañas, para poder resaltar sus elementos.
  - Velo semitransparente que cubre toda la pantalla, con un hueco redondeado (margen de unos
    8 dp) sobre los límites del ancla. El recorte usa la técnica de
    `ui/components/AntStatus.kt`: `CompositingStrategy.Offscreen` + `BlendMode.Clear`.
  - Globo con estilo Ant, con los colores y espaciados de `AntTheme`, en claro y en oscuro:
    - título y texto;
    - contador "n/N";
    - botones Saltar (`AntButtonType.Link`), Anterior (desde el paso 2) y Siguiente (en el
      último paso, "Listo").
  - El globo va debajo del hueco si hay espacio y encima si no, con una flecha hacia el elemento,
    y siempre dentro de la zona segura (`WindowInsets.safeDrawing`). Un paso sin ancla muestra el
    globo centrado y sin hueco.
- testTags: `tour_overlay` (el velo), `tour_balloon` (el globo), `tour_title`, `tour_body`,
  `tour_counter`, `tour_next`, `tour_prev`, `tour_skip`.
- El globo mide como máximo 360 dp. Los botones están en un `FlowRow` y bajan de línea con
  pantallas estrechas o letra grande.
- **Interacción:** solo lectura.
  - Los toques sobre el velo y sobre el elemento resaltado no llegan a la app.
  - Solo responden los botones del globo.
  - "Atrás" del sistema retrocede un paso. En el primer paso equivale a Saltar (§3.5), aviso
    incluido.
- **Accesibilidad:**
  - TalkBack lee el título y el texto del globo al cambiar de paso.
  - Los botones tienen nombre accesible.
  - El velo no es enfocable.
  - El globo es un panel (`paneTitle`) y su título y texto son una región viva. El contenido de
    debajo se oculta a los servicios de accesibilidad mientras hay un paso en pantalla.

### 3.5 Ciclo de vida de un recorrido

- **Disparo:** al mostrarse la pantalla superior de la pestaña activa (o una pantalla sin barra,
  como Sesión o Editar receta), se evalúa su `Tour` con `pendingSteps`.
  - Solo se evalúa cuando las anclas se han estabilizado. Criterio orientativo: unos 300 ms sin
    cambios en el registro tras la primera composición; el plan fija el mecanismo. Así una lista
    que carga no deja pasos fuera por llegar tarde.
  - Si la lista no está vacía, empieza el recorrido.
- **Un solo recorrido a la vez:** el de la pantalla visible. No se evalúa con una hoja inferior
  o un modal abiertos.
- **Siguiente y Listo:** marcan como visto el paso actual (`markSeen`) y avanzan; Listo cierra.
- **Saltar** (el botón, o "Atrás" en el primer paso):
  - marca como vistos todos los pasos de la lista pendiente de esa apertura y cierra;
  - no toca los pasos invisibles (por modo o por ancla ausente) ni otros recorridos;
  - muestra un aviso breve no bloqueante, con el estilo de `AntMessage`: "Puedes volver a verlo
    en Ajustes > Ver recorrido" (`tour_skipped_hint`). Sale en cada salto, sin acción;
  - el host del aviso vive en la capa del recorrido y va arriba y centrado, para que se vea en
    cualquier pantalla y no choque con los avisos de cada pantalla, que van abajo.
- **Ancla que desaparece con el paso en pantalla:** el paso se salta sin marcarse como visto.
  Ocurre, por ejemplo, si la báscula se desconecta y la tarjeta cambia de estado.
- **Ancla fuera de la vista:** si el elemento está dentro de un contenedor desplazable, la
  pantalla se desplaza hasta él (`bringIntoViewRequester` o equivalente) antes de dibujar el
  hueco.
- **Rotación y muerte del proceso:** los pasos de la apertura y el índice del paso actual se
  guardan con `rememberSaveable`. Lo ya marcado como visto persiste en `TourSettings`. En los dos
  casos se restaura el mismo estado guardado, con el mismo "n/N", y no se vuelve a evaluar. Como
  Siguiente marca el paso antes de avanzar, ese paso es el primero sin marcar, salvo que el
  usuario hubiera retrocedido con Anterior: entonces se restaura el paso al que retrocedió.
- **Cambio de pestaña o navegación:** con el recorrido abierto no puede ocurrir, porque el velo
  bloquea los toques. "Atrás" retrocede pasos (ver §3.4).

### 3.6 Integración

- **Primer arranque:** al terminar `OnboardingScreen` se muestra `TabsNavigation` en Preparar, y
  la evaluación normal dispara el recorrido general. No hace falta lógica especial.
- **Modos:** el modo se lee de `LocalExperienceMode`. Al pasar de Básico a Experto, la siguiente
  apertura de cada pantalla muestra solo sus pasos `expertOnly` pendientes. Esto cubre el aviso
  de lo que se desbloqueó.
- **"Ver recorrido" en Ajustes** (`settings_tour_replay`):
  - llama a `TourSettings.reset()`;
  - cierra Ajustes y vuelve a la raíz de Preparar, donde se dispara el recorrido general;
  - las demás pantallas vuelven a mostrar el suyo en su siguiente apertura.
- **Sesión:**
  - El recorrido de Sesión con báscula se evalúa solo en los pasos previos (`PrepPanel`, fase
    distinta de `Active` y `Finished`).
  - El de Sesión sin báscula se evalúa solo en el estado listo (`scaleless_ready`, fase `Armed`).
  - El de Resumen se evalúa solo con `session_summary` en pantalla (fase `Finished`).
  - Los de Sesión usan `session_close` y `session_options`, que están en la barra superior. Esas
    dos anclas solo se registran fuera de `Active` y sin cuenta regresiva, para que el vertido
    no actualice el registro (§4, rendimiento).
  - Ninguno se evalúa ni se muestra mientras `session_countdown` está visible. Si la cuenta
    regresiva empieza con un recorrido abierto (no debería, porque el velo bloquea los botones),
    el recorrido se cierra sin marcar sus pasos pendientes.
  - Si la preparación pasa a `Active` con el recorrido abierto (con báscula, al detectar el
    vertido), el recorrido se cierra. Los pasos no vistos quedan pendientes para la siguiente
    sesión.

### 3.7 Recorridos y pasos

Notación:
- "E" significa que el paso es `expertOnly`.
- "centrado" es un paso sin ancla.
- Los textos son un borrador de intención. El plan los redacta finales en `values` y
  `values-en`, ajustados al comportamiento real.
- Las anclas son los testTags existentes, salvo las marcadas "(nueva)".
- La implementación cambió algunas anclas del borrador. Las que difieren están anotadas en cada
  tabla con "(era `…`)" y el motivo en una nota debajo de ella.
- Las anclas de fila (`coffee_row`, `history_row`, `recipe_builtin_tag`, `history_row_scaleless`,
  `converter_row`) se ponen solo en la primera fila que cumple, para que cada id sea único.

**`prepare`: General, en Preparar.** Se ve tras el primer arranque y tras "Ver recorrido".

| Paso | Ancla | Contenido |
|---|---|---|
| `prepare.welcome` | centrado | Bienvenida: Pourline guía cada preparación con la báscula y te enseña lo básico en un minuto. |
| `prepare.scale` | `scale_card` | Conecta la báscula aquí; sin una a mano, prueba el simulador. |
| `prepare.recipe` | `prepare_recipe` | Elige una receta para que la sesión te guíe por etapas, o prepara libre. |
| `prepare.coffee` | `prepare_coffee` | Asocia el café que preparas; su bitácora y su stock se actualizan solos. |
| `prepare.dose` | `prepare_dose_card` (nueva) | La dosis y el ratio dan el agua; con receta vienen de ella. |
| `prepare.grind` | `grind_card` | La molienda para tu molino; al cambiar de molino, Pourline convierte el ajuste. |
| `prepare.grind_ruler` (E) | `grind_card_ruler` | La regla muestra dónde cae el ajuste dentro del rango del molino. |
| `prepare.start` | `prepare_start` | Con la báscula lista, Empezar abre la sesión guiada. |
| `prepare.start_scaleless` | `prepare_start_scaleless` | Sin báscula, sigue la receta solo con el reloj. |
| `prepare.settings` | `prepare_settings` | Ajustes: modo, tema, molinos, avisos y cuenta regresiva. |
| `prepare.tab_coffees` | `tab_coffees` | Cafés: tus bolsas, con foto de la etiqueta, tueste y stock. |
| `prepare.tab_recipes` | `tab_recipes` | Recetas: las incluidas y las tuyas, por etapas. |
| `prepare.tab_history` | `tab_history` | Historial: cada preparación, con su valoración y sus datos. |
| `prepare.tab_tools` | `tab_tools` | Herramientas: calculadora de ratio, 4:6 y conversor de molienda. |

**`session_prep`: Sesión con báscula, en los pasos previos.**

| Paso | Ancla | Contenido |
|---|---|---|
| `session_prep.stepper` | `prep_stepper` | Antes de verter: tarar, pesar el café, tarar de nuevo y verter. |
| `session_prep.weight` | `prep_weight` | El peso en vivo de la báscula. |
| `session_prep.actions` | `prep_tare` | Tara desde aquí o en la báscula; cada paso se puede saltar. |
| `session_prep.timer` | centrado | El reloj empieza solo al detectar el vertido, o con "Empezar timer" tras la cuenta regresiva. Durante el vertido, el dial muestra el agua, el tiempo y la etapa. |
| `session_prep.options` | `session_options` | Opciones: unidad, reiniciar y, con el simulador, vertidos simulados. |
| `session_prep.close` | `session_close` | Sal de la sesión cuando quieras. |

**`session_scaleless`: Sesión sin báscula, en el estado listo.**

| Paso | Ancla | Contenido |
|---|---|---|
| `session_scaleless.plan` | `scaleless_plan_water` | El plan: dosis, ratio y agua objetivo. Solo existe sin receta: con receta el paso queda pendiente hasta la primera sesión sin báscula y sin receta. |
| `session_scaleless.start` | `scaleless_start` | Empieza cuando estés listo; la cuenta regresiva se configura en Ajustes. |
| `session_scaleless.dial` | centrado | Sin báscula, el dial muestra el tiempo de cada paso y avisa al cambiar de paso, también con la pantalla apagada. |
| `session_scaleless.close` | `session_close` | Sal de la sesión cuando quieras. |

Las anclas `session_close` y `session_options` solo se registran fuera de `Active` y sin cuenta regresiva (§3.6).

**`summary`: Resumen, al terminar una preparación.**

| Paso | Ancla | Contenido |
|---|---|---|
| `summary.rating` | `summary_details` (era `summary_rating`) | Valora la preparación y anota lo que notaste. |
| `summary.details` | `summary_details` | Completa el nombre, el método, la temperatura y las notas de cata. |
| `summary.share` | `summary_share` | Comparte una imagen de la preparación. |
| `summary.expert` (E) | `summary_expert`, solo con báscula | Flujo, estabilidad y ajuste a la curva objetivo. |

`summary_rating` solo existe cuando la preparación guardada ya tiene valoración, así que no sirve de ancla al abrir el resumen: `rating` y `details` resaltan el mismo botón con dos textos. Sin báscula, `summary_expert` solo muestra el motivo de fin, y el paso queda pendiente hasta un resumen con báscula.

**`coffees`: Cafés.**

| Paso | Ancla | Contenido |
|---|---|---|
| `coffees.add` | `coffees_add` | Agrega una bolsa; con una foto de la etiqueta, Pourline rellena los datos. |
| `coffees.search` | `coffees_search` | Busca por nombre, tostador u origen. |
| `coffees.tabs` | `coffees_tabs` | Los cafés terminados se archivan aquí. |
| `coffees.row` | `coffee_row` (la primera) | Abre un café para ver los días desde el tueste, el stock y su bitácora. |

**`coffee_edit`: Alta y edición de café.**

| Paso | Ancla | Contenido |
|---|---|---|
| `coffee_edit.photos` | `coffee_photo_slot_1` | Fotografía el frente y el reverso de la bolsa. |
| `coffee_edit.read_label` | `coffee_edit_read_label` | Lee la etiqueta y rellena los campos; revisa lo marcado como leído. |
| `coffee_edit.save` | `coffee_edit_save` | Guarda el café. |

**`coffee_detail`: Detalle de café.**

| Paso | Ancla | Contenido |
|---|---|---|
| `coffee_detail.status` | `coffee_detail_status` (era `coffee_detail_days`) | Días desde el tueste y café que queda en la bolsa. |
| `coffee_detail.log` | `coffee_detail_log` (nueva), en la cabecera de la bitácora | Las preparaciones hechas con este café. |
| `coffee_detail.new_bag` | `coffee_detail_new_bag` | Empieza una bolsa nueva del mismo café, o archívalo al terminarlo. |
| `coffee_detail.share` | `coffee_detail_share` | Comparte una ficha del café. |

**`recipes`: Recetas.**

| Paso | Ancla | Contenido |
|---|---|---|
| `recipes.builtin` | `recipe_builtin_tag` (la primera) | Recetas incluidas: úsalas tal cual o duplícalas para ajustarlas. |
| `recipes.active` | `recipe_active_tag` | La receta elegida en Preparar. Recetas la marca con la etiqueta "Activa" (antes solo salía en el selector de Preparar); `RecipeRow` la ancla solo si recibe `activeAnchor`, porque la misma etiqueta existe en otras listas. |
| `recipes.add` | `recipes_add` | Crea tu propia receta por etapas. |

**`recipe_edit`: Editar receta.**

| Paso | Ancla | Contenido |
|---|---|---|
| `recipe_edit.method` | `recipe_edit_method` | El método de preparación. |
| `recipe_edit.stage` | `recipe_stage_0` (era `recipe_stage_1`) | Cada etapa es un vertido, un removido o una espera, con su tiempo. Es la primera etapa: los índices van desde 0. |
| `recipe_edit.add_stage` | `recipe_add_stage` | Añade etapas. |
| `recipe_edit.totals` | `recipe_edit_totals` | El agua, el ratio y la duración totales. |
| `recipe_edit.preview` (E) | `recipe_edit_preview` | La curva objetivo que seguirá la sesión. |

**`history`: Historial.**

| Paso | Ancla | Contenido |
|---|---|---|
| `history.filters` | `history_filter_coffee` | Filtra por café y por método. |
| `history.progress` (E) | `history_progress` (nueva), en la tarjeta de progreso | Cómo evoluciona tu estabilidad. |
| `history.row` | `history_row` (la primera) | Abre una preparación para ver sus datos. |
| `history.scaleless` | `history_row_scaleless` (ya existía, de SPEC-007), en la etiqueta "Sin báscula" de la primera fila sin báscula | Hechas sin báscula: sin curva y fuera de las comparaciones. |

**`brew_detail`: Detalle de preparación.**

| Paso | Ancla | Contenido |
|---|---|---|
| `brew_detail.chart` (E) | `brew_detail_chart` (nueva), en la tarjeta de la gráfica real y en la de la curva objetivo (`brew_detail_target_chart`); solo se compone una | La curva real del vertido (o la objetivo, si fue sin báscula). |
| `brew_detail.details` | `brew_detail_details` | Molienda y otros detalles de la preparación. |
| `brew_detail.edit` | `brew_detail_edit` | Edita la valoración, las notas, el método y la temperatura; sin báscula, también la dosis y el agua. |
| `brew_detail.compare` (E) | `brew_detail_compare` | Compárala con otra preparación. |
| `brew_detail.save_recipe` (E) | `brew_detail_save_recipe` | Conviértela en receta. |
| `brew_detail.share` | `brew_detail_share` | Comparte una imagen. |

**`compare`: Comparar.** Solo se llega en Experto.

| Paso | Ancla | Contenido |
|---|---|---|
| `compare.chart` | `compare_chart_water` | Las curvas de agua de las dos preparaciones, superpuestas. |
| `compare.legend` | `compare_legend` | Qué color es cada una. |
| `compare.table` | `compare_table` | Las diferencias, dato a dato. |

**`tools`: Herramientas.**

| Paso | Ancla | Contenido |
|---|---|---|
| `tools.ratio` | `tools_ratio` | Calcula café, ratio y agua. |
| `tools.four_six` | `tools_four_six` | Genera una receta 4:6 según el sabor y la intensidad. |
| `tools.converter` | `tools_converter` | Convierte un ajuste de molienda entre molinos. |

**`ratio_calculator`: Calculadora de ratio.**

| Paso | Ancla | Contenido |
|---|---|---|
| `ratio_calculator.fields` | `ratio_dose` (era `ratio_coffee`, que es el nombre de la cadena) | Los tres campos se editan: con el café o el ratio se recalcula el agua; con el agua, el café. |
| `ratio_calculator.validation` | `ratio_water` | Si un campo no vale, se marca y el resto se calcula con los otros dos. |

**`four_six`: 4:6.**

| Paso | Ancla | Contenido |
|---|---|---|
| `four_six.dose` | `four_six_dose` | La dosis y el ratio. |
| `four_six.sliders` | `four_six_levels` (nueva), una `Column` que envuelve los dos sliders de sabor e intensidad | El sabor reparte el primer 40 % (más dulce o más brillante); la intensidad decide en cuántos vertidos va el 60 %. |
| `four_six.pours` | `four_six_pour_0` (era `four_six_pour_1`) | Cada vertido con su tiempo y su peso acumulado. |
| `four_six.save` | `four_six_save` | Guárdalo como receta. |

**`converter`: Conversor de molienda.**

| Paso | Ancla | Contenido |
|---|---|---|
| `converter.source` | `converter_source` | El molino y el ajuste de partida. |
| `converter.rows` | `converter_row` (la primera fila; su `testTag` sigue siendo `converter_row_<id>`) | El equivalente en tus otros molinos. |

**`settings`: Ajustes.**

| Paso | Ancla | Contenido |
|---|---|---|
| `settings.mode` | `settings_mode` | Básico o Experto: Experto muestra gráficas, estabilidad y comparaciones. |
| `settings.grinders` | `settings_grinders` | Tus molinos y el predeterminado. |
| `settings.theme` | `settings_theme` | Claro, oscuro o el del sistema. |
| `settings.alerts` | `settings_stage_vibration` | Vibración y sonido al cambiar de etapa. |
| `settings.countdown` | `settings_countdown` | Segundos antes de que arranque el reloj. |
| `settings.replay` | `settings_tour_replay` (nueva) | Vuelve a ver todos los recorridos. |

### 3.8 Textos

- Por cada paso, dos recursos: `tour_<recorrido>_<paso>_title` y `tour_<recorrido>_<paso>_body`,
  en `values/strings.xml` (español) y `values-en/strings.xml`.
- Recursos comunes:
  - `tour_next` ("Siguiente"), `tour_prev` ("Anterior"), `tour_done` ("Listo") y
    `tour_skip` ("Saltar");
  - `tour_counter` ("%1$d/%2$d");
  - `tour_skipped_hint` ("Puedes volver a verlo en Ajustes > Ver recorrido");
  - `settings_tour_replay` ("Ver recorrido") con su texto de ayuda.
- Sin emojis. Frases cortas: el cuerpo, como máximo dos frases.
- La redacción final está en `values/strings.xml` y `values-en/strings.xml`, y en el §2.7 del
  plan. El usuario la aprobó sobre las capturas el 2026-11-17 (D34 del plan).
- `settings_tour_replay_hint` es el texto de ayuda de la fila "Ver recorrido".

### 3.9 Flujo de ejecución

1. Se abre la pantalla y sus elementos con ancla se registran en `TourAnchorRegistry`.
2. Cuando las anclas se estabilizan, se calcula `pendingSteps(tour, seen, mode, anchors)`.
3. Si hay pasos pendientes, se muestra `TourOverlay` con el primero, desplazando la pantalla si
   hace falta.
4. Siguiente marca el paso como visto y avanza. Saltar marca toda la lista, cierra y avisa de
   "Ver recorrido". Atrás retrocede o, en el primer paso, actúa como Saltar.
5. "Ver recorrido" vacía `tour_seen` y vuelve a Preparar, donde se repite desde el paso 1.

## 4. Casos Límite y Manejo de Errores

- La lista de una pantalla está vacía (Cafés, Recetas o Historial sin filas) -> los pasos
  anclados a filas se omiten y no se marcan; aparecen en una apertura posterior, cuando haya
  filas.
- Un paso centrado es el único pendiente de un recorrido -> no se muestra (§3.2).
- Modo Básico -> los pasos E no se muestran ni se marcan. Al pasar a Experto -> la siguiente
  apertura de cada pantalla muestra solo sus pasos E pendientes.
- El ancla desaparece con su paso en pantalla (cambio de estado de la báscula, recarga de una
  lista) -> se avanza al siguiente paso pendiente sin marcar el actual. Si no queda ninguno, el
  recorrido se cierra.
- El ancla está fuera de la vista dentro de un contenedor desplazable -> se desplaza hasta ella.
  Si tras el desplazamiento sigue sin límites válidos -> el paso se trata como ausente.
- Se rota la pantalla con un recorrido abierto -> continúa en el mismo paso, con el hueco y el
  globo recolocados. En horizontal, el globo puede ir a un lado o encima del elemento si no cabe
  debajo; nunca fuera de la zona segura.
- El proceso muere con un recorrido abierto -> al volver, los pasos ya marcados no se repiten y
  el recorrido sigue en el mismo paso y con el mismo "n/N" (§3.5): es el primer paso sin marcar,
  o el que el usuario había retrocedido con Anterior.
- "Atrás" en el primer paso -> equivale a Saltar: marca la lista pendiente, cierra y muestra el
  aviso de "Ver recorrido".
- Una hoja inferior o un modal está abierto al abrir la pantalla (por ejemplo, restaurado tras
  rotar) -> no se evalúa el recorrido hasta que se cierre.
- Sesión: cuenta regresiva visible -> no hay recorrido. La preparación pasa a `Active` -> el
  recorrido se cierra sin marcar los pendientes. La sesión se restaura sin preparación abierta
  (D44 de SPEC-005) -> la pantalla se retira y no se evalúa su recorrido.
- Al pulsar Empezar se pide el permiso de notificaciones -> el recorrido de Sesión se evalúa
  cuando la pantalla de Sesión es la visible, no mientras el diálogo del sistema está encima.
- Un `tour_seen` con ids que ya no existen -> se ignoran.
- Preferencias corruptas o ilegibles -> se tratan como conjunto vacío: el usuario vuelve a ver
  los recorridos y no hay error.
- Rendimiento: el registro de anclas no debe recomponer las pantallas en cada frame: nada en
  composición lee los límites, y se escriben solo si cambian. En la sesión, `session_close` y
  `session_options` (la barra superior se compone también en `Active`) solo se registran fuera de
  `Active` y sin cuenta regresiva, para no afectar al umbral de `SessionPerformanceTest`.
- `summary_expert` solo es ancla con báscula, y `scaleless_plan_water` solo existe sin receta: en
  esos casos el paso queda pendiente, no se marca.

## 5. Criterios de Aceptación (Definition of Done)

- [x] Tests JVM de `pendingSteps`:
  - vistos frente a no vistos;
  - Básico frente a Experto;
  - anclas ausentes;
  - paso centrado solitario;
  - orden y contador.
- [x] Tests JVM de la semántica de Saltar (solo los pasos visibles de esa apertura) y de `reset`.
- [x] Test instrumentado: tras el primer arranque aparece el recorrido general en Preparar
      (`tour_overlay`, contador "1/N"). Al completarlo y reiniciar la actividad, no reaparece.
- [x] Test instrumentado:
  - Saltar cierra el general;
  - muestra el aviso `tour_skipped_hint`;
  - el recorrido de Cafés aparece igualmente al abrir la pestaña.
- [x] Test instrumentado:
  - Anterior retrocede;
  - "Atrás" en el primer paso cierra el recorrido y muestra el aviso;
  - la siguiente apertura de la pantalla no lo vuelve a mostrar.
- [x] Test instrumentado: con el general completado en Básico, pasar a Experto y volver a
      Preparar muestra solo `prepare.grind_ruler`.
- [x] Test instrumentado: "Ver recorrido" en Ajustes vuelve a Preparar y muestra el general desde
      el paso 1.
- [x] Test instrumentado: el recorrido de Sesión sin báscula aparece en el estado listo y no
      aparece mientras `session_countdown` está visible.
- [x] Test instrumentado: mientras el globo está visible, un toque sobre el elemento resaltado no
      ejecuta su acción (por ejemplo, `prepare_recipe` no abre `target_picker`).
- [x] Cada recorrido de §3.7 tiene sus textos en `values` y `values-en`; una comprobación en test
      verifica que todos los `TourStep` apuntan a recursos existentes en ambos idiomas y que los
      ids de paso son únicos.
- [x] Los tests instrumentados existentes pasan, con `skipTours()` en su setup donde haga falta.
      Las excepciones son los fallos previos conocidos ajenos a este spec (`CoffeeDetailScreenTest`
      por idioma y `SessionPerformanceTest` intermitente).
- [x] Verificación manual en el emulador con `adb` (`uiautomator dump`, `input tap`,
      `screencap`), sin ARTEMIS: cada recorrido de §3.7 se ve en claro y en oscuro, con el globo
      dentro de la pantalla, en vertical y en un caso en horizontal.
- [x] Con TalkBack, el título y el texto del globo se anuncian al cambiar de paso.
      Verificado en el emulador: al abrirse el recorrido TalkBack anuncia el título del panel
      ("Welcome to Pourline" / "Your scale") y el árbol de accesibilidad solo contiene el globo.
      El usuario completó la escucha (cambio de paso, foco inicial en el globo) en su teléfono
      físico el 2026-11-17 (D35).
- [ ] `SessionPerformanceTest` no empeora respecto a `develop` (`1015b12`).
      Medido contra el commit previo a los recorridos (`a652d16`): por medianas no empeora, pero
      el emulador iba muy cargado y el p95 es ruidoso; hay que repetirlo en un equipo libre.

## 6. Preguntas abiertas

- Redacción final de cada paso, ajustada al comportamiento real de cada pantalla. La redactó el
  plan (§2.7) y quedó en `values` y `values-en`. **Resuelta:** el usuario la aprobó sobre las
  capturas de la verificación el 2026-11-17 (D34 del plan).
- Resueltas por el plan: las anclas se consideran estabilizadas tras 300 ms sin altas ni bajas
  (D9), y las anclas de una `LazyColumn` se declaran con `TourLazyAnchor` y se buscan
  desplazando la lista (D10).

## 7. Alternativas descartadas

- **Carrusel de bienvenida a pantalla completa:** más simple, pero no señala los elementos reales.
- **Que tocar el elemento resaltado avance o ejecute su acción:** más interactivo, con más casos
  raros; se eligió solo lectura.
- **Guardar lo visto por pantalla:** no permite presentar lo de Experto ni los pasos nuevos sin
  lógica adicional.
- **Saltar que apague todos los recorridos:** el usuario prefirió que solo cierre el actual.
- **Recorrido de Sesión durante el vertido o solo en el resumen:** estorba el vertido o llega
  tarde; se eligió antes de empezar.
- **Recorrido general por las cinco pestañas cambiando de pestaña solo:** se eligió Preparar más
  la barra; cada pestaña da su detalle al abrirla.
