# SPEC-005: Navegación por pestañas, sesión guiada y modos Básico/Experto

| Campo | Valor |
|---|---|
| Estado | Aprobado. Plan aprobado: [`plan-navegacion-modos.md`](plan-navegacion-modos.md) (sus decisiones D1 a D45 están en el §3.8 de este spec) |
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
  - La simulada nunca se recuerda ni se reconecta sola. Usarla no borra la báscula real recordada,
    que se sigue reconectando (plan D28).
- **Tarjeta de báscula en Preparar.** Muestra un estado con su acción:

  | Estado | Texto | Acción |
  |---|---|---|
  | Sin permisos | "Necesitamos permiso de Bluetooth" | "Dar permisos" y el enlace "Usar báscula simulada" |
  | Bluetooth apagado | "Bluetooth apagado" | "Activar" y el enlace "Usar báscula simulada" |
  | Sin báscula recordada | "Conecta tu báscula" | Abre la hoja de conexión |
  | Conectando (`ConnectionState.Connecting`) | "Conectando a {nombre}…" con el paso actual | Ninguna |
  | Conectada (`Ready`) | "{nombre} conectada" con la batería (`BatteryTag`) | Menú: "Desconectar", "Cambiar báscula" |
  | Falló | "No se pudo conectar a {nombre}" | "Reintentar", "Cambiar báscula" |
  | Desconectada (por el usuario o por pérdida) | "{nombre} desconectada" | "Conectar", "Cambiar báscula" |

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
    "Reiniciar". "Reiniciar":
    - antes de `Active`, vuelve al paso 1 y olvida la dosis capturada;
    - en `Active`, está deshabilitado (la X ya ofrece terminar o descartar);
    - en `Finished`, empieza otra sesión con la misma receta y el mismo café.
- **Pasos previos guiados.** Es una máquina de estados pura y probable sin Android (nombre
  orientativo: `PrepFlow`):

  | Paso | Instrucción | Avanza cuando | Acción manual |
  |---|---|---|---|
  | 1. Tara | "Coloca el filtro y la jarra y tara" | Tara confirmada (ver abajo) | "Tarar" y "Saltar" |
  | 2. Dosis | "Añade el café molido" ({dosis objetivo} si hay) | El peso es estable (ver abajo); se captura como dosis, sin tarar | "Capturar ahora" y "Saltar (sin dosis)" |
  | 3. Tara para verter | "Tara otra vez y empieza a verter" | Tara confirmada | "Tarar" y "Saltar" |
  | 4. Vertido | La sesión arma la `BrewSession` (`Armed`); el timer arranca solo al detectar agua (`Active`, efecto `StartTimer`), como hoy | — | "Empezar timer" |

  - **Peso estable (paso 2):**
    - Lo calcula la app, porque el protocolo de la DOT no trae una bandera de peso estable y el
      código solo calcula la estabilidad del flujo.
    - Cuenta como estable una ventana de 2 s con una variación de 0,3 g como máximo y un peso de al
      menos el 50 % de la dosis objetivo.
    - Antes de la tara del paso 3, una nueva lectura estable de hasta el 150 % de la dosis objetivo
      sustituye a la anterior.
    - Los valores exactos están en el plan (D17).
  - **Tara confirmada (pasos 1 y 3).** Vale cualquiera de estas tres señales, para no depender solo de
    una trama BLE sin verificar (plan D19):
    - la trama de tara de la báscula;
    - el peso que queda en cero tras pulsar "Tarar" en la app;
    - el peso que pasa de ≥ 1 g a cero sin tara enviada (botón físico).
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
  - Duración de 1 a 120 s para `POUR` y `SWIRL`, y de 1 a 600 s para `WAIT`, porque la migración
    puede crear esperas de más de 120 s.
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

### 3.8 Decisiones de implementación (D1 a D45)

Las decisiones que el plan ([`plan-navegacion-modos.md`](plan-navegacion-modos.md)) tomó donde este spec dejaba
huecos o lo ampliaba. D9, D12, D13, D17, D19, D24, D27, D37 y D40 amplían o concretan el spec; las demás son de
implementación. Los números de las fases (F0 a F8) y los apartados "§" citados son los del plan.

#### Diseño visual

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Barra inferior | `AntTabBar` (nuevo, F0.2): 56 dp más el inset de navegación, fondo `bgContainer`, línea superior en `split`; icono de 24 dp y texto `textSM`; la pestaña activa en `primary.base`, las demás en `textSecondary`. Iconos: `Dripper` (Preparar, nuevo), `Coffee`, `Recipe` y `History`. | Es el patrón del `Tabs` inferior de móvil sobre los tokens Ant. Con icono y texto (§5). |
| D2 | Menú ⋯ de la sesión | Una hoja inferior (`AntDrawer`, "Opciones") con tres filas: "Modo experto" (`AntSwitch`), "Unidad" (`AntSegmented` g/oz, con la regla de habilitado de `UnitCard`: solo con la báscula en `Ready`) y "Reiniciar" (P2). Con la báscula simulada añade la sección "Simulación" con los controles de `SimulationCard`. | Un menú desplegable no admite bien un segmentado; la hoja ya existe y cumple "en el menú ⋯ o en una hoja" (§3.4). |
| D3 | Diálogo de salida | `AntChoiceModal` (nuevo): título, texto y botones apilados a todo el ancho: "Terminar y guardar" (`Primary`), "Descartar" (`danger`) y "Seguir preparando". Tocar fuera equivale a "Seguir preparando". | Tres botones no caben en una fila a 360 dp. |
| D4 | Dial | `AntProgressCircle` (nuevo, el `Progress type="circle"` de antd) dentro de `BrewDial`. Tamaño `min(ancho × 0,62; alto disponible × 0,34; 260 dp)` con `BoxWithConstraints`; trazo de 12 dp. El aro se satura al 100 %. Con receta, separaciones de 2° en el agua acumulada de cada paso `POUR` (los `SWIRL` y `WAIT` no añaden agua, así que no tienen segmento). | Garantiza el criterio sin scroll en 1080 × 2400 en los dos modos (F0.3, R8). Un segmento de agua 0 no se vería. |
| D5 | Página 2 del dial (Experto) | `HorizontalPager` de 2 páginas (1 en Básico) con `AntPageIndicator` (nuevo, dos puntos). La página 2 compone `BrewChart` con la curva objetivo a la altura del dial. Al pasar a Básico estando en la página 2, `scrollToPage(0)`. | §3.4 y §4. El pager solo compone la página visible (y la vecina durante el gesto), así la gráfica no cuesta en la página 1. |
| D6 | Siguiente paso | "Siguiente: {tipo} {nombre} en {m:ss}", con el tiempo que falta (`nextStage.startMs − elapsedMs`). En los últimos 5 s, el número sale de `GuideState.countdownS` ("en 5 s") en `primary.base` y negrita. Sin nombre: "Siguiente: Esperar en 0:25". | El spec pide la cuenta atrás de 5 s; fuera de esa ventana también conviene saber cuánto falta. |
| D7 | Preparación libre (sin receta) | Título "Preparación libre". El bloque del paso actual muestra un único paso sintético "Verter hasta {agua objetivo}" y no hay "Siguiente" ni lista. | Sin receta no hay pasos que guiar; el aro sí tiene objetivo (`dosis × ratio`). |
| D8 | Resumen | Sustituye al dial y a la lista (F0.3). Básico: tiempo total, agua, dosis, ratio, café, valoración y notas (las dos últimas aparecen tras "Añadir detalles", leídas de la preparación guardada). Experto añade flujo medio y máximo, `StabilitySection`, `FitBlock` y motivo de fin. Botones "Añadir detalles", "Compartir" y "Listo" (principal). | §3.2 y §3.4. |
| D9 | Detalle de preparación en Básico | Se ocultan la tarjeta de la gráfica, `BrewSummaryCard`, `FitCard`, la tarjeta "Cata", "Comparar" y "Guardar como receta". Se muestra `BrewFactsCard` (nueva: fecha, receta, café como enlace, dosis, agua, ratio, tiempo total, valoración y notas) y debajo "Detalles" (método, molienda, temperatura) y "Editar"/"Borrar". | El spec lista lo que se oculta y lo que muestra la tarjeta de datos. `BrewSummaryCard` lleva la estabilidad y "Cata" repetiría café, valoración y notas. **Amplía** el §3.6 (oculta dos tarjetas más). |
| D10 | Fila del historial en Básico | `HistoryRow(showMetrics = false)`: sin la etiqueta de estabilidad ni la de ajuste. La minicurva decorativa (`BrewPreviewLine`, sin ejes ni semántica) se mantiene. | §3.6 pide quitar la estabilidad; el ajuste es métrica de Experto (§3.2). La minicurva no es una gráfica con datos; se puede quitar si se prefiere. |
| D11 | Primer arranque | No es una clave de navegación: `MainNavigation` muestra `OnboardingScreen` en lugar de las pestañas mientras `onboardingDone == false`. | Sin barra, sin pila que restaurar y con "Atrás" saliendo de la app. Los nombres de §3.1 son orientativos. |
| D12 | Tarjeta de báscula | `ScaleCard` con los estados de §3.3. Variante de "Falló o se desconectó": si la última conexión falló, "No se pudo conectar a {nombre}" con "Reintentar"; si se desconectó (por el usuario o por pérdida), "{nombre} desconectada" con "Conectar". Las dos con "Cambiar báscula". "Conectada" lleva el menú (`AntIconButton(More)` con "Desconectar" y "Cambiar báscula"). | "Reintentar" tras un "Desconectar" del usuario sería confuso. |
| D13 | Báscula simulada sin permisos o con Bluetooth apagado | En esos dos estados la tarjeta añade un enlace secundario "Usar báscula simulada" (P3). | No hay báscula física: sin el enlace, un usuario sin permisos no podría probar nada (hoy `ScanScreen` lo muestra siempre). **Amplía** la tabla de §3.3. |

#### Implementación

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D14 | Paquetes | Nuevo `dev.dotscale.session`: `ScaleSessionManager`, `ScaleTarget`, `ScaleMemory`, `AutoReconnect`, `PrepFlow`, `BrewPlan`. En F2 se mueven ahí `ScaleUiState.kt`, `ScaleCoffees.kt` y `TargetReferences.kt` (`git mv`, solo cambia el `package`). Nuevos `dev.dotscale.ui.prepare`, `dev.dotscale.ui.session` y `dev.dotscale.ui.onboarding`. `ui/scale` conserva las piezas de dibujo reutilizadas. | El gestor no es UI. Los archivos puros se comprueban con `grep "import android"`. |
| D15 | Gestor de la app | `ScaleSessionManager` es un objeto por proceso en `DotScaleApp`, con `MainScope()` (`Dispatchers.Main.immediate`), igual que el hilo de `viewModelScope` de hoy. La conexión se crea al conectar: `fun interface ScaleConnectionFactory { fun create(target: ScaleTarget): ScaleConnection }` (`AndroidScaleConnections`: `Real` → `BleScaleConnection(context, address)`, `Simulated` → `SimulatedScaleConnection()`). Los flujos de la conexión se recogen con `flatMapLatest` sobre `MutableStateFlow<ScaleConnection?>`. | Cambiar de pestaña no desconecta (§3.3) y la rotación no afecta. Con la fábrica, los tests siguen con `FakeScaleConnection`. |
| D16 | Compuerta de la sesión | El gestor solo pasa pesos, taras y estados del temporizador a la `BrewSession` mientras está **armado**: desde que `PrepFlow` llega al paso 4 hasta que la sesión pasa a `Finished`, se descarta o se cierra. Fuera de eso, los pesos solo actualizan la pantalla y `PrepFlow`. En F2–F6 la pantalla provisional (`ScaleScreen`) arma al entrar. | Sin compuerta, un vertido en Cafés o en Preparar empezaría y guardaría una sesión, y una tara en el resumen la rearmaría (hoy `onTared` en `Finished` rearma). |
| D17 | Peso estable del paso Dosis (cierra §6, pregunta 3) | Se calcula en la app, en `PrepFlow`, sin depender de ninguna bandera BLE: ventana de 2 s con al menos 3 lecturas válidas, `máx − mín ≤ 0,3 g` y peso ≥ `max(1 g; 0,5 × dosis objetivo)`; la dosis es la media de la ventana redondeada a 0,1 g. La dosis objetivo es la nominal de la receta o la de Preparar. En el paso 3, antes de la tara, una nueva ventana estable entre `0,5 ×` y `1,5 ×` la dosis objetivo que difiera más de 0,3 g sustituye a la dosis (recaptura). Constantes en `PrepConfig`. | La DOT no envía bandera de estabilidad y el código no calcula estabilidad de peso. El 50 % evita capturar la primera cucharada; la recaptura corrige una captura temprana sin botón nuevo, y el 150 % impide que el agua de un vertido se tome como café. **Amplía** "el peso es > 0" del §3.4. |
| D18 | Captura sin tara automática | En la sesión, la dosis se fija con `captureDose(tare = false)`; el paso 3 pide la tara. `captureDose()` sin argumento conserva el comportamiento de hoy (fija y tara) hasta que se borra `ScaleScreen` en F7. | Si la captura tarase, el paso 3 se confirmaría solo con esa tara y nunca se vería. |
| D19 | Confirmación de tara (pasos 1 y 3) | Vale cualquiera de: (a) `ScaleMessage.Tared(success = true)`; (b) tras "Tarar" en la app, el peso queda en `|p| ≤ 0,3 g` durante 1 s dentro de los 5 s siguientes; (c) sin tara enviada, el peso pasa de ≥ 1 g a `|p| ≤ 0,3 g` estable 1 s (botón físico sin trama). "Saltar" siempre está. | No se ha verificado que la DOT envíe la trama de tara ni con qué frecuencia manda el peso (memoria del proyecto): ninguna suposición BLE es el único camino. **Amplía** "se recibe la confirmación de tara". |
| D20 | Curva con tipos | `TargetStage` gana `type: StageType = POUR` e `instruction: String? = null`. `TargetCurve.fromRecipe` genera los **puntos solo con los pasos `POUR`** (el mismo algoritmo de hoy sobre esa subsecuencia) y las **etapas con todos** los pasos. `TargetCurve.endMs` pasa a ser el fin del último `POUR` (es el tiempo objetivo de `brewFit`); nuevo `lastStepEndMs` (fin del último paso de cualquier tipo) para `GuideState.allStagesDone`. `finalWaterG` no cambia. | Puntos idénticos punto a punto para una receta migrada (§3.5) y misma métrica aunque una receta acabe en `WAIT`. La guía y los avisos ven todos los pasos (§3.5). |
| D21 | `TargetCurveCodec` (cierra §6, pregunta 2) | El formato v1 no cambia: `encode` escribe solo las etapas `POUR` (reindexadas) y `decode` las lee como `POUR`. | La curva guardada solo se usa para superponer el objetivo en la gráfica del detalle; la meseta sale de los puntos. Sin versión nueva, lo ya guardado y lo nuevo son iguales para una receta migrada. |
| D22 | "Descartar" (cierra §6, pregunta 1) | Se evita el guardado: `BrewSession.discard(nowMs)` vuelve a un estado armado limpio sin emitir `BrewEffect.Finished` (emite `PauseTimer` si el temporizador corría). El gestor cierra la compuerta, olvida el objetivo y el café congelados y la dosis, y la pantalla vuelve a Preparar. | No hay escritura que deshacer: ni carrera con el guardado (`applicationScope`), ni `lastUsedAtEpochMs` del café que recalcular, ni `pendingUndo` que limpiar. |
| D23 | Métodos nuevos de `BrewSession` | `fun reset()` (estado armado limpio, conserva el último estado del temporizador), `fun discard(nowMs: Long): List<BrewEffect>` (D22) y `fun startNow(nowMs: Long): List<BrewEffect>` ("Empezar timer": pasa a `Active` con `t0 = nowMs`, línea base = último peso válido o 0, y los mismos efectos de temporizador que `startSession`). | Son las acciones de la tabla de §3.4 que hoy no existen; quedan en la máquina pura y se prueban en la JVM. |
| D24 | Ciclo de vida de la dosis | La dosis capturada se borra al terminar ("Listo"), al descartar y al cambiar de unidad (como hoy). Si se sale con la X antes de `Active`, se conserva: al volver a "Empezar", el paso 2 se da por hecho (§3.4). Una desconexión durante los pasos previos devuelve `PrepFlow` al paso 1 al reconectar, con la dosis conservada. | Da sentido a "si la dosis ya estaba capturada antes de abrir la sesión". Tras reconectar, la báscula puede haber perdido la tara. |
| D25 | Timer de la sesión | `elapsedMs = clock() − t0` en `Active` (el `t0` de la sesión), publicado en cada lectura y en el tick de 1 s; en `Finished`, `summary.totalTimeMs`. Formato `m:ss` con `formatClock(ms)` (sale de `formatTime` privado de `ScaleScreen.kt` a `Formatters.kt`). | No depende del contador de la báscula, sin verificar, y coincide con los tiempos de la curva y de la guía. |
| D26 | Agua objetivo | `session/BrewPlan.kt`: `data class BrewPlan(val doseG: Double, val ratio: Double)` y `fun targetWaterG(target: ActiveTarget?, capturedDoseG: Double?, plan: BrewPlan): Double`: con receta, `target.curve.finalWaterG` (ya escalada por `factor`); sin receta, `(capturedDoseG ?: plan.doseG) × plan.ratio`. | §3.3 y §4 ("sin receta, el aro usa la dosis configurada en Preparar" cuando se salta la dosis). |
| D27 | Dosis y ratio de Preparar | Siempre en gramos. Dosis de 5 a 60 g con un decimal (como `RecipeValidator`); ratio de 10,0 a 20,0 con un decimal. `AntInput` numéricos; un valor fuera de rango marca el campo y no se guarda. `BrewSettings` gana `defaultDoseG: StateFlow<Double>`, `defaultRatio: StateFlow<Double>` y sus `set…`, tras la interfaz `FreeBrewDefaults`. | El spec no fija rangos. Los de la dosis ya existen para recetas; 1:10–1:20 cubre filtrado. |
| D28 | Última báscula | `ScaleMemory` (`lastScale: StateFlow<RememberedScale?>`, `remember(scale)`), implementada por `ConnectionSettings` (`last_scale_address`, `last_scale_name`). El gestor llama a `remember` cuando una conexión `ScaleTarget.Real` llega a `Ready`. Conectar la simulada no la borra: si había una real recordada, se sigue reconectando a esa (P4). | §3.3: solo se recuerda la real. Confirmado por el usuario (P4). |
| D29 | Reconexión automática | `AutoReconnect` con tres disparadores: arranque de la app, vuelta a primer plano (`ON_START` de `MainActivity`) y Bluetooth que pasa a encendido (`BluetoothEnvironment.enabled`, un `BroadcastReceiver` de `ACTION_STATE_CHANGED` expuesto como `StateFlow`). Conecta solo si hay báscula real recordada, permisos, Bluetooth encendido, nada conectado ni conectando, y el usuario no pulsó "Desconectar" en esta ejecución. Un fallo no reintenta hasta el siguiente disparador. | §3.3 y §4: sin bucles en segundo plano. Tras un "Desconectar" explícito, reconectar al volver a la app molestaría. |
| D30 | Pila por pestaña | `enum class Tab(root, label, icon, testTag)`; una `rememberNavBackStack(tab.root)` por pestaña y `var tab by rememberSaveable`. Las entradas de cada pila se decoran con `rememberDecoratedNavEntries` y un `entryProvider` cuyo `contentKey` lleva el prefijo de la pestaña (`"HISTORY/BrewDetail(id=5)"`). `NavDisplay` recibe las entradas de Preparar y, si la pestaña activa es otra, las suyas detrás; su `onBack` aplica `backAction` (D31). | Es la receta de pilas múltiples de Navigation 3. El prefijo evita que dos pilas compartan `ViewModel` o estado guardado. Con las entradas de Preparar debajo, "Atrás" en la raíz de otra pestaña tiene a dónde ir, y en Preparar raíz pasa al sistema (sale). |
| D31 | Reglas de navegación | `TabNavigation.kt` (puro): `fun showsBottomBar(key: NavKey): Boolean` (falso en `Session`, `CoffeeEdit`, `RecipeEdit` y `BrewEdit`); `fun backAction(tab: Tab, stackSize: Int): BackAction` (`Pop`, `SelectPrepare`, `Exit`); tocar la pestaña activa vacía su pila hasta la raíz. Los accesos cruzados se apilan en la pestaña actual (también `ComparePick`, `BrewCompare`, `BrewEdit` y `RecipeEdit` desde un `BrewDetail` abierto en Cafés). "Gestionar recetas" del `TargetPicker` en Preparar selecciona la pestaña Recetas. | §3.1. La tabla del spec es orientativa en las pilas; la regla general es "dentro de la pestaña actual". |
| D32 | Dependencias de la app | `AppDependencies` (nuevo) agrupa los singletons de `DotScaleApp` (repositorios, ajustes, gestor, `AutoReconnect`, `BluetoothEnvironment`, fábrica del escáner). `MainNavigation(deps)` los recibe en lugar de `appContext as DotScaleApp`. Las clases de ajustes ganan `prefsName: String = PREFS_NAME`. | Los tests de pestañas, del recorrido del usuario nuevo y de rendimiento montan la app con Room en memoria, preferencias propias (`"settings_test"`) y la báscula simulada. |
| D33 | Modo en la UI | `ExperienceMode.shows(feature: ExpertFeature): Boolean` (puro; todas las entradas de `ExpertFeature` son de Experto). `LocalExperienceMode` (`compositionLocalOf`), provisto en `MainActivity` desde `ExperienceSettings.mode`, con valor por defecto `EXPERT`. | Los tests de pantalla existentes, que no lo proveen, siguen viendo todo. El defecto de la preferencia sigue siendo `BASIC` (§3.2). |
| D34 | Pasos con tipo: modelo | `enum class StageType { POUR, SWIRL, WAIT }` en `recipe/Recipe.kt`. `RecipeStage` gana `type: StageType = POUR` e `instruction: String? = null`. `fun List<RecipeStage>.withDerivedWater()` fija el `targetWaterG` de `SWIRL` y `WAIT` al acumulado del `POUR` anterior (0 si no hay). `Recipe.totalWaterG` es el del último `POUR`. `RecipeListItem.stageCount` cuenta solo `POUR`. | §3.5. Con el recuento de vertidos la fila de una receta migrada no cambia ("3 etapas"). |
| D35 | Migración v3 → v4 | `val MIGRATION_3_4`: `ALTER TABLE recipe_stages ADD COLUMN type TEXT NOT NULL DEFAULT 'POUR'` y `ADD COLUMN instruction TEXT`. Por cada receta, lee sus pasos por `position`, aplica `StageGaps.fillWaits` y, si cambian, borra sus filas y las vuelve a insertar con `position` renumerada (la clave primaria es `(recipeId, position)`). La entidad declara `@ColumnInfo(defaultValue = "POUR")` para que `runMigrationsAndValidate` acepte el esquema. | Renumerar en el sitio chocaría con la clave primaria; borrar e insertar por receta no toca `recipes`. |
| D36 | Huecos | `recipe/StageGaps.kt` (puro): `fun fillWaits(stages: List<RecipeStage>): List<RecipeStage>`. Un `WAIT` por hueco `> 0` (también desde 0 si el primer paso empieza después), con el agua acumulada anterior, sin nombre ni instrucción. Los solapes (`hueco ≤ 0`) no generan nada. Lo usan la migración y `RecipeFromBrew`. | §3.5 y §4. Una sola implementación probada en la JVM. |
| D37 | Duración de `WAIT` (P1) | `POUR` y `SWIRL` de 1 a 120 s; `WAIT` de 1 a 600 s (`MAX_WAIT_MS = 600_000`, igual que `MAX_LAST_START_MS`). | Un hueco de más de 120 s (vertidos a 0:00 y 3:00) migra a un `WAIT` que el validador rechazaría al guardar la receta. Aprobado (P1); el §3.5 del spec ya lo recoge. |
| D38 | Sembrado y cadena de migraciones | `BuiltInRecipeSeeder.seed(db, texts, nowMs, schema: SeedSchema = SeedSchema.CURRENT)`. `SeedSchema.V2` inserta solo los pasos `POUR`, sin las columnas nuevas; lo usa `migration1To2`. La 3 → 4 convierte esas filas en la definición nueva. | La cadena 1 → 2 → 3 → 4 sigue siendo válida y el resultado es igual a una instalación nueva. |
| D39 | Predefinidas | La definición nueva es exactamente `fillWaits` de la antigua: V60 clásica con 5 pasos (`POUR` 0:00 40 g 10 s, `WAIT` 0:10 35 s, `POUR` 0:45 150 g 20 s, `WAIT` 1:05 25 s, `POUR` 1:30 250 g 25 s) y 4:6 con 9 (5 `POUR` de 10 s cada 45 s y 4 `WAIT` de 35 s). Sin `WAIT` final. Los `WAIT` no tienen nombre (elemento vacío en el `string-array`) y todos los pasos tienen instrucción (`recipe_builtin_<key>_instructions`, es y en). `RecipeTexts.builtInStageNames` pasa a `List<String?>` (vacío → `null`) y gana `builtInStageInstructions(key): List<String?>`; `RecipeRepository.toRecipe` traduce también las instrucciones mientras `localizedNames`. | Como la definición coincide con la estructura migrada, los nombres e instrucciones por índice cuadran en una base migrada. Misma curva que hoy (§3.5). "Esperar" con el nombre "Espera" se leería "Esperar Espera". |
| D40 | `RecipeFromBrew` | Límite propio `MAX_POURS = 10` (en lugar de `MAX_STAGES`) para fusionar vertidos y, tras redondear, `fillWaits`. Resultado de 19 pasos como máximo. | Con `MAX_STAGES = 20` fusionaría hasta 20 vertidos y los huecos superarían el límite. Las esperas explícitas son el modelo nuevo (§7 del spec descarta los huecos implícitos). |
| D41 | `SimulatedTargetPour` | Ventanas, origen y retraso de `DEVIATED` solo con los pasos `POUR`. | Un `WAIT` inicial desplazaría el origen del guion y el retraso de "desde la 2.ª etapa" caería en una espera. |
| D42 | `PrepFlow` | Máquina pura en `session/PrepFlow.kt` (firma en F7) con efectos `PrepEffect.SendTare`, `SetDose(grams: Double?)`, `Arm` y `StartNow`, que ejecuta el gestor. El gestor le pasa cada peso mientras el paso no es `POUR`. | Probable sin Android (§3.4), con el patrón de efectos de `BrewSession`. |
| D43 | "Reiniciar" (P2) | Aprobado: antes de `Active`, vuelve al paso 1 y olvida la dosis capturada; en `Active` está deshabilitado (la X ya ofrece terminar o descartar); en `Finished`, empieza otra sesión con la misma receta y el mismo café, como "Listo" + "Empezar". | El spec no define qué reinicia. Hoy "Reiniciar" pone a cero el temporizador de la báscula, y en `Active` eso termina la sesión (`TIMER_RESET`). |
| D44 | Muerte del proceso | Si al restaurar la pila de Preparar la cima es `Session` y el gestor no tiene sesión, la entrada se retira sola (`LaunchedEffect`). | §4: al volver, Preparar sin sesión. |
| D45 | Commits | Uno por fase, en Conventional Commits y en inglés, sin `Co-Authored-By` ni firmas de herramientas, y solo cuando se pidan. El mensaje se propone al final de cada fase. El plan, cuando se apruebe: `docs(specs): add navigation and modes implementation plan (SPEC-005)`. | Convenciones del usuario. |

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
- [x] La app arranca en Preparar (o en la elección de modo la primera vez), nunca en la lista BLE.
- [x] La barra inferior muestra Preparar, Cafés, Recetas e Historial con icono y texto.
- [x] Cada pestaña conserva su pila; tocar la pestaña activa vuelve a su raíz.
- [x] "Atrás" en la raíz de Cafés, Recetas o Historial selecciona Preparar; desde Preparar sale.
- [x] Ningún topbar tiene iconos de sección; el tema se cambia en Ajustes.
- [x] La elección de modo del primer arranque guarda el modo; "Saltar" deja Básico; no se vuelve a
      mostrar.
- [x] El modo se cambia desde Ajustes y desde el menú ⋯ de la sesión, y se aplica al instante sin
      reiniciar la sesión.
- [x] Las reglas de §3.2 se cumplen en sesión, resumen, historial, detalle, recetas y ajustes, en
      ambos modos.
- [x] Una preparación hecha en Básico muestra la gráfica, la estabilidad y el ajuste al pasar a
      Experto.

### Conexión y Preparar
- [ ] Con una báscula real recordada, la app intenta reconectar al abrir; la simulada no se
      recuerda. (La simulada no se recuerda: comprobado en el E2E. La reconexión a una real solo está probada
      con conexiones falsas en la JVM; falta una báscula física.)
- [x] La tarjeta de báscula muestra cada estado de §3.3 con su acción.
- [x] La hoja de conexión ofrece escaneo, "Mostrar todos" y "Usar báscula simulada".
- [x] Cambiar de pestaña no desconecta la báscula.
- [x] Sin receta, el agua objetivo es `dosis × ratio`, con 15 g y 1:16 por defecto y persistidos al
      editarlos.

### Sesión
- [x] La sesión oculta la barra inferior.
- [x] Los pasos previos avanzan solos (tara confirmada, dosis estable) y cada uno se puede saltar.
- [x] Durante el vertido, el timer, el agua, el paso actual y el siguiente se ven a la vez sin
      scroll en 1080 × 2400, en ambos modos.
- [x] En Experto, la segunda página del dial muestra la gráfica en vivo con la curva objetivo.
- [x] La X en `Active` abre el diálogo de tres opciones; "Descartar" no deja la preparación en el
      historial; antes de `Active` sale sin preguntar.
- [ ] Los avisos de etapa suenan y vibran al empezar cada paso, de cualquier tipo. (Cada paso, incluidos los
      `WAIT`, llega al notificador: probado con `FakeNotifier` y `RecordingNotifier`. El sonido y la vibración
      reales no se pueden comprobar en un emulador.)

### Recetas
- [x] El editor permite elegir el tipo de paso y escribir la instrucción; el agua solo se pide en
      Verter.
- [x] La sesión muestra la instrucción del paso actual y el tipo con icono en la lista.
- [x] Las recetas predefinidas traen pasos Esperar e instrucciones en es y en.

### Pruebas
- [x] **Unitarios (JVM):**
  - reglas de visibilidad por modo;
  - máquina de estados `PrepFlow` (avances, "Saltar", desconexión);
  - agua objetivo desde la dosis y el ratio;
  - `TargetCurve.fromRecipe` con `SWIRL` y `WAIT`;
  - `RecipeValidator` con tipos y el nuevo límite;
  - gestor de conexión (reconexión, no recordar la simulada).
- [x] **Migración v3 → v4** en `HistoryMigrationTest` con `MigrationTestHelper`:
  - los pasos pasan a `POUR`;
  - los huecos se convierten en `WAIT`, incluido el inicial;
  - los solapes no fallan;
  - la curva de cada receta migrada es idéntica punto a punto.
- [x] **Instrumentados (Compose, báscula simulada):**
  - pestañas y pilas;
  - primer arranque;
  - sesión completa en Básico (sin gráfica ni estabilidad) y en Experto (con ellas);
  - interruptor del menú ⋯;
  - diálogo de la X;
  - detalle del historial sin gráfica en Básico.
- [x] **Recorrido del usuario nuevo:** un test instrumentado va de la elección de Básico hasta tener
      una preparación guardada en el historial sin abrir Ajustes.
- [x] **Rendimiento:** un test de rendimiento de la sesión (en la línea de `CoffeePerformanceTest`)
      con un vertido simulado de 2 min sin frames perdidos de forma visible, medido con
      `FrameMetrics` o `JankStats`. El plan fija el umbral y el resultado queda anotado en él (medido en un
      emulador API 34; falta confirmarlo en un teléfono).
- [x] Los tests existentes que dependen de los iconos del topbar o de `Scan` y `Scale` se adaptan
      (`ScaleTopBarTest`, `SettingsNavigationTest`, tags `scan_open_coffees` y `scale_open_coffees`)
      y pasan.
- [x] Capturas en claro y oscuro de Preparar, la sesión en Básico, la sesión en Experto y el
      resumen, hechas en el emulador con la báscula simulada (con tests instrumentados y `adb`, sin
      ARTEMIS).
- [x] Todas las cadenas nuevas existen en `values` (es) y `values-en`.
- [x] El módulo `:app` compila y pasa lint sin avisos nuevos.

## 6. Preguntas abiertas (resueltas en el plan)

- **"Descartar":** se evita el guardado. `BrewSession.discard()` cierra la sesión sin emitir
  `Finished`, así que no hay nada que borrar (plan D22).
- **`TargetCurveCodec`:** el formato v1 no cambia. Solo se codifican las etapas `POUR`, y la curva de
  una receta migrada es idéntica (plan D20 y D21).
- **Criterio de peso estable:** se calcula en la app (§3.4, plan D17).
- **Dial en 1080 × 2400 con 10 o más pasos:** el paso actual y el siguiente son bloques fijos y la lista
  tiene scroll propio. Lo comprueba un test de layout con 19 pasos (plan D4).
- **Ampliaciones del plan que cambian el §3.6:**
  - en Básico, el detalle oculta también `BrewSummaryCard` y la tarjeta "Cata" (D9);
  - "Guardar como receta" genera pasos Esperar explícitos (D40);
  - el primer arranque es una pantalla que se muestra en lugar de las pestañas, no una clave de
    navegación (D11).

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
