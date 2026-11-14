# SPEC-005: Navegación por pestañas, sesión guiada y modos Básico/Experto

| Campo | Valor |
|---|---|
| Estado | Aprobado, pendiente de plan |
| Fecha | 2026-11-14 |
| Rama | `feat/navigation-modes`, creada desde `feat/coffee-catalog` (SPEC-004 aún sin fusionar) |
| Deriva de | Uso real de la app tras SPEC-001 a SPEC-004 |
| Módulos afectados | `:app`: navegación (`Navigation.kt`, `NavigationKeys.kt`), `DotScaleApp`, `settings`, `recipe`, `brew`, `history` (migración), `ui/scan`, `ui/scale`, `ui/recipes`, `ui/history`, `ui/settings`, `ui/components` |
| Specs relacionados | [SPEC-001](spec-grafica-vertido.md), [SPEC-002](spec-historial.md), [SPEC-003](spec-curva-objetivo.md), [SPEC-004](spec-catalogo-cafes.md) |

## 1. Resumen y Contexto

- **Problema:**
  - **La navegación son iconos sin texto en el topbar.**
    - Scan tiene cinco: Cafés, Recetas, Historial, Ajustes y Tema.
    - Scale tiene batería, Cafés, Historial, Ajustes y Tema, y le falta Recetas.
    - No hay una estructura clara de secciones.
  - **La app arranca en la lista de dispositivos BLE** (`ScanScreen`). Es una pantalla técnica que no
    invita a usar la app.
  - **La pantalla Scale es un scroll largo de tarjetas:** conexión, peso, controles, gráfica con
    objetivo y café, resumen, dosis, unidad y simulación.
    - Con la gráfica visible, los gramos vertidos y los pasos de la receta no se ven completos.
    - El orden de las tarjetas no sigue el orden en que se prepara un café.
  - **Un usuario no experto se abruma.** Ve gráficas, estabilidad, puntuación de ajuste,
    comparaciones y muchas cifras que no entiende.
- **Solución:**
  - **Barra inferior con cuatro pestañas:** Preparar, Cafés, Recetas e Historial. Cada pestaña
    tiene su propia pila. Ajustes se abre desde el topbar de Preparar.
  - **Preparar:**
    - una tarjeta de báscula con reconexión automática a la última;
    - la receta, el café, y la dosis y el ratio editables;
    - un botón "Empezar".
  - **Sesión inmersiva** a pantalla completa, sin barra inferior:
    - pasos previos guiados (tara, dosis, vertido);
    - un dial circular con el agua y el progreso, y un timer grande;
    - el paso actual, el siguiente y la lista de pasos.
  - **Modo Básico y modo Experto.** Se elige en el primer arranque, en Ajustes y en el menú ⋯ de la
    sesión. Básico oculta gráficas y métricas técnicas. Los datos se graban siempre completos.
  - **Pasos de receta con tipo** (Verter, Remover, Esperar) e instrucción de texto. Las esperas
    implícitas de las recetas existentes pasan a ser pasos explícitos.
- **Éxito:**
  - Un usuario nuevo, en modo Básico, llega desde abrir la app hasta terminar y guardar un café
    **sin pasar por Ajustes** (verificable con la báscula simulada, §5).
  - Durante la sesión, el peso, el timer, el paso actual y el siguiente están visibles **a la vez
    sin hacer scroll**, en ambos modos, en un teléfono de 6,1" y 1080 × 2400.
  - Cualquier sección principal está a **un toque** desde cualquier raíz de pestaña.
  - Una sesión simulada de 2 minutos no pierde frames de forma visible (criterio medido, §5).
- **Alcance (In):**
  - Navegación con barra inferior de 4 pestañas y pila por pestaña.
  - Pantalla Preparar, que sustituye a `ScanScreen` como inicio, y hoja inferior de conexión.
  - Reconexión automática a la última báscula real.
  - Sesión inmersiva con pasos previos guiados, dial, lista de pasos, menú ⋯ y diálogo de salida.
  - Rediseño del resumen final.
  - Modo Básico o Experto: elección en el primer arranque, preferencia en Ajustes, interruptor en
    el menú ⋯ y reglas de visibilidad en sesión, resumen, historial y recetas.
  - Pasos de receta con tipo e instrucción; migración de la base de datos v3 → v4; instrucciones
    de las recetas predefinidas en es y en.
  - Dosis y ratio por defecto editables en Preparar.
  - Las acciones del topbar (Tema, accesos a secciones) pasan a la barra inferior o a Ajustes.
- **Alcance (Out):**
  - Mantener la conexión o grabar la sesión en segundo plano (servicio en primer plano). Sigue
    como hoy: pantalla encendida durante la sesión y sin garantías en segundo plano.
  - Rediseño visual del resto de la app. Listas, formularios, detalle y ajustes siguen con los
    componentes y tokens Ant actuales; solo se rediseñan Preparar, la sesión y el resumen.
  - Drawer o menú lateral.
  - Cambios en el protocolo BLE, en el cálculo de estabilidad o en la métrica de ajuste.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario nuevo, quiero que la app me pregunte la primera vez si prefiero una
  experiencia básica o experta, para no ver opciones que no entiendo.
- **US-2:** Como usuario, quiero que la app se conecte sola a mi báscula al abrirla, para empezar a
  preparar sin pasar por una lista de dispositivos.
- **US-3:** Como usuario básico, quiero que la app me guíe paso a paso (tarar, añadir el café,
  verter), para preparar un café sin saber de antemano el orden.
- **US-4:** Como usuario que sigue una receta, quiero ver a la vez el agua vertida, el tiempo, el
  paso actual con su instrucción y el siguiente paso, para no tener que hacer scroll mientras
  vierto.
- **US-5:** Como usuario experto, quiero ver la gráfica en vivo, la estabilidad y el ajuste a la
  curva sin perder de vista el peso, para seguir afinando mis preparaciones.
- **US-6:** Como usuario, quiero cambiar de modo en plena sesión desde el menú ⋯, para ver la
  gráfica solo cuando la necesito.
- **US-7:** Como usuario, quiero navegar entre Preparar, Cafés, Recetas e Historial con una barra
  inferior que recuerde dónde estaba en cada sección.
- **US-8:** Como usuario básico, quiero ver mi historial y mis recetas sin gráficas, con los datos
  que entiendo (dosis, agua, ratio, tiempo, café, valoración).
- **US-9:** Como autor de recetas, quiero añadir pasos de tipo Remover o Esperar y escribir una
  instrucción en cada paso, para que la sesión me diga qué hacer.
- **US-10:** Como usuario que pasa de Básico a Experto, quiero ver las gráficas y métricas de mis
  preparaciones anteriores, porque se grabaron completas.

## 3. Especificación Técnica

### 3.1 Navegación

- **Estructura:** una barra inferior con cuatro destinos, en este orden y con icono y texto:

  | Pestaña | Raíz | Pantallas que se apilan dentro |
  |---|---|---|
  | Preparar | `Prepare` (nueva) | `Settings` |
  | Cafés | `Coffees` | `CoffeeDetail`, `CoffeeEdit`, `BrewDetail` |
  | Recetas | `Recipes` | `RecipeEdit` |
  | Historial | `History` | `BrewDetail`, `BrewEdit`, `ComparePick`, `BrewCompare`, `CoffeeDetail`, `RecipeEdit` (desde "Guardar como receta") |

- **Pila por pestaña:**
  - Cada pestaña conserva su propia pila de `NavKey` (Navigation 3, `rememberNavBackStack` por
    pestaña) y su estado guardado al cambiar de pestaña.
  - Tocar la pestaña activa vuelve a su raíz.
  - "Atrás" en una pantalla apilada vuelve a la anterior de la misma pestaña.
  - "Atrás" en la raíz de Cafés, Recetas o Historial selecciona Preparar. Desde la raíz de Preparar,
    sale de la app.
  - Los accesos cruzados se abren **dentro de la pestaña actual**. Por ejemplo, el café de una
    preparación se abre en la pila de Historial y el detalle de una preparación en la de Cafés.
- **Barra visible u oculta:**
  - Visible en las raíces y en las pantallas apiladas de lista y detalle.
  - Oculta en la sesión (§3.4), en el primer arranque (§3.2) y en los editores (`CoffeeEdit`,
    `RecipeEdit`, `BrewEdit`), para que el teclado y el botón Guardar no compitan con ella.
- **Topbar:**
  - Se quitan los iconos de sección de `ScanScreen` y de `ScaleTopBar`.
  - `ThemeModeButton` pasa a Ajustes como una opción más (Sistema, Claro, Oscuro).
  - El topbar de Preparar tiene el título y un icono de Ajustes.
- **Claves de navegación:**
  - `Scan` y `Scale` dejan de ser destinos: su función la cubren `Prepare`, la hoja de conexión
    (§3.3) y la sesión (§3.4).
  - Se añaden `Prepare`, `Session` y `Onboarding` (nombres orientativos).
  - Las demás claves de `NavigationKeys.kt` se conservan.

### 3.2 Modo Básico/Experto y primer arranque

- **Preferencias nuevas** en el `SharedPreferences "settings"` que ya comparten `BrewSettings`,
  `ThemeSettings` y `TargetSettings`. Una instancia por proceso en `DotScaleApp`, expuesta como
  `StateFlow`.

  | Clave | Tipo | Defecto | Uso |
  |---|---|---|---|
  | `experience_mode` | `"BASIC"` \| `"EXPERT"` | `"BASIC"` | Modo activo |
  | `onboarding_done` | Boolean | `false` | Si ya se mostró la elección de modo |
  | `last_scale_address` | String? | sin valor | MAC de la última báscula real conectada |
  | `last_scale_name` | String? | sin valor | Su nombre, para la tarjeta de Preparar |
  | `default_dose_g` | Float | 15,0 | Dosis de Preparar sin receta |
  | `default_ratio` | Float | 16,0 | Ratio 1:N de Preparar sin receta |

- **Primer arranque:**
  - Con `onboarding_done == false`, la app abre una pantalla "¿Cómo preparas tu café?" con dos
    tarjetas:
    - **Básico:** "Te guiamos paso a paso. Solo lo esencial."
    - **Experto:** "Gráficas, estabilidad del flujo y comparación de preparaciones."
  - Elegir una tarjeta guarda el modo y `onboarding_done = true` y abre Preparar.
  - "Saltar" deja `BASIC` y marca `onboarding_done = true`.
- **Cambiar de modo:**
  - En **Ajustes**, con un `AntSegmented` "Básico / Experto".
  - En el **menú ⋯ de la sesión**, con un switch "Modo experto".
  - Las dos vías escriben la misma preferencia. El cambio se aplica al instante en todas las
    pantallas y **no reinicia** la sesión en curso.
- **Los datos no dependen del modo:** muestras, vertidos, estabilidad, curva objetivo y métrica de
  ajuste se calculan y guardan igual en los dos modos. El modo solo afecta a lo que se muestra.
- **Reglas de visibilidad.** Se concentran en una función pura (por ejemplo,
  `ExperienceMode.shows(feature)`) para poder probarlas sin interfaz.

  | Área | Básico | Experto (además) |
  |---|---|---|
  | Sesión | Dial con el agua vertida y el aro de progreso; timer; ratio en vivo; flujo en g/s solo como número; pasos previos guiados; paso actual con su instrucción, cuenta atrás del siguiente y lista de pasos | Segunda página del dial con la gráfica en vivo (`BrewChart`, con curva objetivo); indicador de estabilidad; desvío en gramos contra la curva; elegir una preparación guardada como objetivo (pestaña Historial de `TargetPicker`) |
  | Resumen final | Tiempo total, agua, dosis, ratio, café, valoración, notas y compartir | Flujo medio y máximo, estabilidad (`StabilitySection`), ajuste a la curva (`FitBlock`) y motivo de fin |
  | Historial | Lista y detalle sin gráfica: fecha, receta, café, dosis, agua, ratio, tiempo, valoración y notas | Gráfica del detalle, tarjeta de ajuste, gráfica de progreso de estabilidad (`StabilityProgressChart`), "Comparar" y "Guardar como receta" |
  | Recetas | Elegir, seguir, crear y editar pasos (tipo, agua, tiempo, instrucción) | Vista previa de la curva objetivo en el editor |
  | Cafés | Completo, igual que hoy | Igual |
  | Ajustes | Modo, tema, auto-finalizar, vibración y sonido de etapas | "Restaurar recetas predefinidas" |

- La **báscula simulada** aparece en la hoja de conexión en los dos modos, porque todavía no hay
  báscula física para probar.

### 3.3 Conexión y pantalla Preparar

- **Gestor de conexión a nivel de app:**
  - Hoy, la `ScaleConnection` y la `BrewSession` viven en el `ScaleViewModel` de la entrada `Scale`
    de la navegación, y salir de esa pantalla las destruye.
  - Pasan a un objeto único en `DotScaleApp` (nombre orientativo: `ScaleSessionManager`) que tiene:
    - la conexión activa;
    - la `BrewSession`;
    - el objetivo y el café elegidos;
    - la lógica que hoy está en `ScaleViewModel`.
  - Preparar y la sesión lo observan. Cambiar de pestaña **no** desconecta.
  - Sus dependencias se inyectan como hoy (conexión, `BrewSaver`, `ReferenceStore`,
    `TargetReferences`, `StageNotifier`, `CoffeeStore`, `ScaleCoffees`, relojes), para conservar
    los tests de `ScaleViewModel`.
- **Reconexión automática:**
  - Al abrir la app, si hay `last_scale_address` y los permisos están dados, el gestor intenta
    conectar a esa MAC.
  - La última báscula solo se recuerda cuando una conexión **real** llega a `Ready`.
  - La simulada nunca se recuerda ni se reconecta sola.
- **Tarjeta de báscula en Preparar.** Muestra un estado con su acción:

  | Estado | Texto | Acción |
  |---|---|---|
  | Sin permisos | "Necesitamos permiso de Bluetooth" | "Dar permisos" |
  | Bluetooth apagado | "Bluetooth apagado" | "Activar" |
  | Sin báscula recordada | "Conecta tu báscula" | Abre la hoja de conexión |
  | Conectando (`ConnectionState.Connecting`) | "Conectando a {nombre}…" con el paso actual | Ninguna |
  | Conectada (`Ready`) | "{nombre} conectada" con la batería (`BatteryTag`) | Menú: "Desconectar", "Cambiar báscula" |
  | Falló o se desconectó | "No se pudo conectar a {nombre}" | "Reintentar", "Cambiar báscula" |

- **Hoja de conexión:**
  - Es una hoja inferior con el contenido actual de `DevicesCard`: escaneo, "Mostrar todos" y la
    lista con RSSI.
  - Debajo tiene el botón "Usar báscula simulada".
  - Elegir un dispositivo conecta y cierra la hoja.
- **Configuración de la preparación** (debajo de la tarjeta):
  - **Receta:** abre el `TargetPicker` actual. En Básico solo aparece la pestaña Recetas. "Sin
    receta" es una opción válida.
  - **Café:** abre el `CoffeePicker` actual.
  - **Dosis y agua:**
    - Sin receta, se editan la dosis y el ratio (defecto 15 g y 1:16) y el agua objetivo se
      calcula como `dosis × ratio`.
    - Con receta, se muestran la dosis y el agua de la receta, escaladas como hoy (`factor` de
      `ActiveTarget`).
    - Los valores editados sin receta se guardan en `default_dose_g` y `default_ratio`.
  - **"Empezar":** botón principal grande. Está activo solo con la báscula en `Ready` y abre la
    sesión.

### 3.4 Sesión inmersiva

- **Pantalla completa sin barra inferior.** El topbar tiene:
  - **X:** salir (ver más abajo).
  - **Título:** el nombre de la receta, o "Preparación libre".
  - **⋯:** un menú con "Modo experto" (switch), "Unidad" (g/oz, lo que hoy está en `UnitCard`) y
    "Reiniciar".
- **Pasos previos guiados.** Es una máquina de estados pura y probable sin Android (nombre
  orientativo: `PrepFlow`):

  | Paso | Instrucción | Avanza cuando | Acción manual |
  |---|---|---|---|
  | 1. Tara | "Coloca el filtro y la jarra y tara" | Se recibe la confirmación de tara de la báscula | "Tarar" y "Saltar" |
  | 2. Dosis | "Añade el café molido" ({dosis objetivo} si hay) | El peso es > 0 y se mantiene estable; se captura como dosis (`captureDose`) | "Capturar ahora" y "Saltar (sin dosis)" |
  | 3. Tara para verter | "Tara otra vez y empieza a verter" | Tara confirmada | "Tarar" y "Saltar" |
  | 4. Vertido | La sesión arma la `BrewSession` (`Armed`); el timer arranca solo al detectar agua (`Active`, efecto `StartTimer`), como hoy | — | "Empezar timer" |

  - El criterio de "estable" del paso 2 reutiliza el de la báscula, si lo envía, o el de la
    estabilidad de peso que ya calcula el código. El plan lo fija.
  - Si la dosis ya estaba capturada antes de abrir la sesión, el paso 2 se da por hecho.
- **Vertido (fase `Active`):**
  - **Timer grande** arriba (`m:ss`).
  - **Dial circular** en el centro:
    - Dentro: el agua vertida en grande y, debajo, el ratio en vivo y el flujo en g/s (solo
      número, en los dos modos).
    - El aro marca el progreso `agua / agua objetivo`. El agua objetivo es la de la receta
      escalada o `dosis × ratio`, y el aro se satura al 100 %.
    - Con receta, el aro se divide en segmentos por paso.
  - **Paso actual:** nombre, tipo con icono, instrucción y, en los pasos Verter, el agua objetivo
    acumulada.
  - **Siguiente paso:** "Siguiente: {tipo} {nombre} en {cuenta atrás}", con la cuenta atrás de 5 s
    que ya calcula `GuideState.countdownS`.
  - **Lista de pasos:**
    - Debajo, con scroll propio si no cabe.
    - Cada paso muestra su estado (hecho, en curso, pendiente), icono de tipo, nombre, agua (solo
      en Verter) y duración.
  - **Botón principal único**, abajo y centrado: "Terminar" durante `Active`.
  - **En Experto:**
    - El dial tiene una **segunda página deslizable** (indicador de dos puntos) con la gráfica en
      vivo y la curva objetivo.
    - Bajo el dial aparecen el indicador de estabilidad y el desvío en gramos.
    - La primera página es la misma en los dos modos.
  - Los avisos de etapa (vibración y sonido de `AndroidStageNotifier`) suenan al empezar **cada
    paso**, de cualquier tipo.
- **Resumen final (fase `Finished`):**
  - Sustituye al dial en la misma pantalla y lista los datos según §3.2.
  - Tiene los botones "Añadir detalles" (abre `BrewEdit`), "Compartir" y "Listo".
  - "Listo" cierra la sesión y vuelve a Preparar.
- **Salir con la X:**
  - Antes de `Active` (pasos previos o `Armed` sin agua), sale a Preparar sin preguntar.
  - Durante `Active`, abre un diálogo con tres opciones:
    - **"Terminar y guardar":** equivale a `finish` (`FinishReason.MANUAL`) y muestra el resumen.
    - **"Descartar":** termina la sesión **sin guardar** la preparación y vuelve a Preparar. Hoy no
      existe: el plan define si se evita el guardado o se borra lo guardado.
    - **"Seguir preparando":** cierra el diálogo.
  - En `Finished`, sale a Preparar. La preparación ya está guardada.
  - El botón "Atrás" del sistema se comporta igual que la X.
- **Desconexión durante la sesión:**
  - Aparece un aviso dentro de la sesión con "Reintentar".
  - La `BrewSession` se cierra y se guarda como hoy (`onDisconnected`, `FinishReason.DISCONNECTED`).
- **Simulación:** con la báscula simulada, los controles de `SimulationCard` (añadir peso, vertidos
  simulados, seguir el objetivo) se ofrecen en el menú ⋯ o en una hoja. Dejan de ser una tarjeta en
  el flujo principal.
- **Pantalla encendida** durante toda la sesión, como hoy (`KeepScreenOn`).

### 3.5 Pasos de receta con tipo e instrucción

- **Modelo de dominio** (`dev.dotscale.recipe.RecipeStage`), con dos campos nuevos:
  - `type: StageType`, con valores `POUR` (Verter), `SWIRL` (Remover) y `WAIT` (Esperar);
  - `instruction: String?`, texto libre de hasta 200 caracteres.
- **Semántica:**
  - Se conserva `startMs` absoluto por paso, como hoy.
  - `POUR`: `targetWaterG` es el agua acumulada al terminar, como hoy.
  - `SWIRL` y `WAIT`:
    - no añaden agua;
    - su `targetWaterG` se guarda igual al acumulado del paso anterior, para que la columna siga sin
      nulos;
    - el editor no lo pide.
  - `Recipe.totalWaterG` pasa a ser el `targetWaterG` del último paso `POUR`.
- **Entidad Room** (`RecipeStageEntity`): columnas nuevas `type TEXT NOT NULL DEFAULT 'POUR'` e
  `instruction TEXT`.
- **Migración v3 → v4** (`MIGRATION_3_4` en `Migrations.kt`, con el esquema exportado en
  `app/schemas/.../4.json`):
  - Los pasos existentes quedan como `POUR` y sin instrucción.
  - Por cada hueco entre el fin de un paso y el inicio del siguiente (`stage[i+1].startMs >
    stage[i].endMs`), se inserta un paso `WAIT` con:
    - `startMs = stage[i].endMs`;
    - `durationMs = stage[i+1].startMs - stage[i].endMs`;
    - el agua acumulada de `stage[i]`.
  - Se renumera `position`.
  - Un hueco inicial (el primer paso empieza después de 0) también se convierte en `WAIT` desde 0.
- **Curva objetivo:**
  - `TargetCurve.fromRecipe` genera una rampa solo en los pasos `POUR` y una meseta en `SWIRL` y
    `WAIT`.
  - Para una receta migrada, los puntos de la curva son **idénticos** a los de antes de migrar, así
    que la métrica de ajuste no cambia.
  - Las etapas de la guía (`TargetStage`, `GuideState`) incluyen todos los pasos para mostrar
    "actual" y "siguiente".
- **Validación** (`RecipeValidator`):
  - `MAX_STAGES` sube de 10 a **20**, porque una receta de 10 vertidos con 9 huecos migra a 19
    pasos.
  - Debe haber al menos un paso `POUR`.
  - `targetWaterG` creciente solo entre pasos `POUR`.
  - Duración de 1 a 120 s para todos los tipos.
  - Instrucción de 200 caracteres como máximo.
- **Editor de recetas** (`RecipeEditForm`):
  - Cada paso tiene un selector de tipo con icono, inicio, duración, agua (solo en Verter), nombre
    e instrucción.
  - "Añadir paso" propone como inicio el fin del paso anterior.
- **Recetas predefinidas** (`BuiltInRecipes`):
  - Se redefinen con pasos `WAIT` explícitos e instrucciones traducidas en `values` y `values-en`.
  - `BuiltInRecipeSeeder` y "Restaurar recetas predefinidas" usan la nueva definición.
  - La curva de las predefinidas es la misma que hoy.

### 3.6 Historial y recetas en modo Básico

- **Detalle de preparación (`BrewDetailScreen`) en Básico:**
  - Oculta la tarjeta de la gráfica, `FitCard`, "Comparar" y "Guardar como receta".
  - Muestra una tarjeta de datos: fecha, receta, café (enlace), dosis, agua, ratio, tiempo total,
    valoración y notas.
- **Historial (`HistoryScreen`) en Básico:**
  - Oculta la tarjeta de progreso de estabilidad.
  - Las filas (`HistoryRow`) no muestran la estabilidad.
- **Recetas en Básico:** el editor oculta la vista previa de la curva. Lo demás se mantiene.

### 3.7 Flujo de ejecución (camino principal)

1. Se abre la app.
   - Si `onboarding_done == false`, se muestra la elección de modo y se guarda.
   - Si no, se abre Preparar.
2. El gestor intenta reconectar a `last_scale_address`. La tarjeta muestra "Conectando…" y después
   "{nombre} conectada".
3. El usuario elige la receta y el café (opcionales) y ajusta la dosis si no hay receta. Pulsa
   "Empezar".
4. Se abre la sesión inmersiva, sin barra inferior, y recorre los pasos previos: Tara → Dosis
   (captura) → Tara → Vertido (`Armed`).
5. Al detectar agua, la `BrewSession` pasa a `Active` y arranca el timer. El dial, el paso actual,
   el siguiente y la lista se actualizan con cada lectura de peso.
6. La sesión termina por inactividad, por duración máxima, con "Terminar" o con "Terminar y
   guardar". Se guarda la preparación (como hoy) y se muestra el resumen según el modo.
7. "Listo" vuelve a Preparar, con la báscula todavía conectada.

## 4. Casos Límite y Manejo de Errores

- **Permisos BLE denegados:** la tarjeta de Preparar muestra "Dar permisos". Las demás pestañas
  funcionan. No hay reconexión automática hasta que se conceden.
- **Bluetooth apagado:** la tarjeta muestra "Activar". La reconexión se reintenta al volver a
  `Ready` el adaptador o al volver a la app.
- **La reconexión automática falla** (báscula apagada o fuera de alcance):
  - La tarjeta pasa a "No se pudo conectar" con "Reintentar" y "Cambiar báscula".
  - No hay reintentos en bucle en segundo plano.
- **La última báscula fue la simulada:** no se reconecta. La tarjeta muestra "Conecta tu báscula".
- **"Empezar" sin báscula en `Ready`:** el botón está deshabilitado y la tarjeta explica por qué.
- **Desconexión durante los pasos previos:** la sesión muestra el aviso con "Reintentar". La
  `BrewSession` en `Armed` se limpia (`run.clear()`, como hoy) y no se guarda nada.
- **Desconexión durante `Active`:** se cierra y se guarda con `FinishReason.DISCONNECTED`, como
  hoy. Se muestra el resumen.
- **La dosis no se estabiliza:** "Capturar ahora" captura el peso actual. "Saltar (sin dosis)" sigue
  sin dosis; el ratio en vivo muestra "—" y, sin receta, el aro usa la dosis configurada en Preparar.
- **Receta con dosis distinta de la capturada:** se escala como hoy (`ActiveTarget.factor`).
- **Cambio de modo en plena sesión:** solo cambia la visibilidad. No reinicia el timer ni la
  `BrewSession` ni los pasos. Si se pasa a Básico estando en la página de la gráfica, el dial vuelve
  a la primera página.
- **Cambiar de pestaña con una conexión activa:** la conexión y el objetivo elegido se mantienen. La
  sesión inmersiva no se puede dejar sin pasar por la X, porque no tiene barra inferior.
- **"Atrás" del sistema en la sesión:** igual que la X (diálogo en `Active`).
- **Proceso muerto durante la sesión** (segundo plano, fuera de alcance): al volver, la app abre
  Preparar sin sesión. La preparación en curso se pierde, como hoy.
- **Receta borrada mientras está elegida:** se aplica el error actual `TargetRemoved` y Preparar
  queda en "Sin receta".
- **Migración v3 → v4 con datos corruptos** (pasos solapados): no se insertan esperas en huecos
  negativos y los pasos se conservan como `POUR`. La migración nunca falla por esa causa.
- **Receta migrada que supera 20 pasos:** no puede pasar (máximo 10 + 10 huecos = 20 con un hueco
  inicial). Si pasara, la receta se conserva y el editor exige reducirla al guardar.
- **Rotación y recreación de la actividad:** el estado de la sesión vive en el gestor de la app,
  así que la sesión sigue. La pila de cada pestaña se restaura con el estado guardado.

## 5. Criterios de Aceptación (Definition of Done)

### Navegación y modo
- [ ] La app arranca en Preparar (o en la elección de modo la primera vez), nunca en la lista BLE.
- [ ] La barra inferior muestra Preparar, Cafés, Recetas e Historial con icono y texto.
- [ ] Cada pestaña conserva su pila; tocar la pestaña activa vuelve a su raíz.
- [ ] "Atrás" en la raíz de Cafés, Recetas o Historial selecciona Preparar; desde Preparar sale.
- [ ] Ningún topbar tiene iconos de sección; el tema se cambia en Ajustes.
- [ ] La elección de modo del primer arranque guarda el modo; "Saltar" deja Básico; no se vuelve a
      mostrar.
- [ ] El modo se cambia desde Ajustes y desde el menú ⋯ de la sesión, y se aplica al instante sin
      reiniciar la sesión.
- [ ] Las reglas de §3.2 se cumplen en sesión, resumen, historial, detalle, recetas y ajustes, en
      ambos modos.
- [ ] Una preparación hecha en Básico muestra la gráfica, la estabilidad y el ajuste al pasar a
      Experto.

### Conexión y Preparar
- [ ] Con una báscula real recordada, la app intenta reconectar al abrir; la simulada no se
      recuerda.
- [ ] La tarjeta de báscula muestra cada estado de §3.3 con su acción.
- [ ] La hoja de conexión ofrece escaneo, "Mostrar todos" y "Usar báscula simulada".
- [ ] Cambiar de pestaña no desconecta la báscula.
- [ ] Sin receta, el agua objetivo es `dosis × ratio`, con 15 g y 1:16 por defecto y persistidos al
      editarlos.

### Sesión
- [ ] La sesión oculta la barra inferior.
- [ ] Los pasos previos avanzan solos (tara confirmada, dosis estable) y cada uno se puede saltar.
- [ ] Durante el vertido, el timer, el agua, el paso actual y el siguiente se ven a la vez sin
      scroll en 1080 × 2400, en ambos modos.
- [ ] En Experto, la segunda página del dial muestra la gráfica en vivo con la curva objetivo.
- [ ] La X en `Active` abre el diálogo de tres opciones; "Descartar" no deja la preparación en el
      historial; antes de `Active` sale sin preguntar.
- [ ] Los avisos de etapa suenan y vibran al empezar cada paso, de cualquier tipo.

### Recetas
- [ ] El editor permite elegir el tipo de paso y escribir la instrucción; el agua solo se pide en
      Verter.
- [ ] La sesión muestra la instrucción del paso actual y el tipo con icono en la lista.
- [ ] Las recetas predefinidas traen pasos Esperar e instrucciones en es y en.

### Pruebas
- [ ] **Unitarios (JVM):**
  - reglas de visibilidad por modo;
  - máquina de estados `PrepFlow` (avances, "Saltar", desconexión);
  - agua objetivo desde la dosis y el ratio;
  - `TargetCurve.fromRecipe` con `SWIRL` y `WAIT`;
  - `RecipeValidator` con tipos y el nuevo límite;
  - gestor de conexión (reconexión, no recordar la simulada).
- [ ] **Migración v3 → v4** en `HistoryMigrationTest` con `MigrationTestHelper`:
  - los pasos pasan a `POUR`;
  - los huecos se convierten en `WAIT`, incluido el inicial;
  - los solapes no fallan;
  - la curva de cada receta migrada es idéntica punto a punto.
- [ ] **Instrumentados (Compose, báscula simulada):**
  - pestañas y pilas;
  - primer arranque;
  - sesión completa en Básico (sin gráfica ni estabilidad) y en Experto (con ellas);
  - interruptor del menú ⋯;
  - diálogo de la X;
  - detalle del historial sin gráfica en Básico.
- [ ] **Recorrido del usuario nuevo:** un test instrumentado va de la elección de Básico hasta tener
      una preparación guardada en el historial sin abrir Ajustes.
- [ ] **Rendimiento:** un test de rendimiento de la sesión (en la línea de `CoffeePerformanceTest`)
      con un vertido simulado de 2 min sin frames perdidos de forma visible, medido con
      `FrameMetrics` o `JankStats`. El plan fija el umbral y el resultado queda anotado en él.
- [ ] Los tests existentes que dependen de los iconos del topbar o de `Scan` y `Scale` se adaptan
      (`ScaleTopBarTest`, `SettingsNavigationTest`, tags `scan_open_coffees` y `scale_open_coffees`)
      y pasan.
- [ ] Capturas en claro y oscuro de Preparar, la sesión en Básico, la sesión en Experto y el
      resumen, hechas en el emulador con la báscula simulada (con tests instrumentados y `adb`, sin
      ARTEMIS).
- [ ] Todas las cadenas nuevas existen en `values` (es) y `values-en`.
- [ ] El módulo `:app` compila y pasa lint sin avisos nuevos.

## 6. Preguntas abiertas

- **"Descartar":** ¿se evita el guardado al cerrar la `BrewSession` o se borra la preparación ya
  guardada? Lo decide el plan; el criterio visible es que no quede en el historial.
- **`TargetCurveCodec`:** la curva guardada en el historial (SPEC-003) ¿debe guardar el tipo de cada
  etapa para mostrar los pasos en el detalle, o basta con las etapas de vertido? Si cambia el
  formato, el codec necesita una versión nueva compatible con lo ya guardado.
- **Criterio de peso estable** del paso Dosis: ¿basta la bandera de estabilidad de la báscula
  (pendiente de verificar con la DOT física, ver la memoria del proyecto) o se calcula en la app?
- **Dial en 1080 × 2400 con 10 o más pasos:** la lista de pasos tiene scroll propio. Se valida en la
  verificación manual que el paso actual y el siguiente nunca quedan ocultos.

## 7. Alternativas descartadas

- **Barra inferior de 3 pestañas más drawer** (Bitácora = Historial + Cafés): se descartó por tener
  dos lugares donde buscar.
- **Solo drawer:** esconde las secciones principales.
- **Barra inferior visible durante la sesión** (como en la referencia 1): quita espacio al dial y a
  la lista de pasos.
- **Interruptor de modo en el topbar de Preparar:** se prefirió el menú ⋯ de la sesión, que es donde
  se nota el cambio.
- **Lista BLE embebida en Preparar sin reconexión automática:** mantiene la fricción de elegir la
  báscula en cada uso.
- **Rediseño visual completo:** demasiado alcance; se limita a Preparar, la sesión y el resumen.
- **Solo instrucción de texto, sin tipos de paso:** se perdían Remover y Esperar como pasos visibles
  con icono.
- **Mostrar los huecos como espera calculada:** dos conceptos para lo mismo; se migran a pasos
  `WAIT` explícitos.
- **Servicio en primer plano para la sesión:** fuera de alcance; posible spec futuro.
