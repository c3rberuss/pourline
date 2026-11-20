# SPEC-009: Compartir e importar recetas

| Campo | Valor |
|---|---|
| Estado | Implementado y fusionado a `develop` (2026-11-18, `bbc96aa`). Plan: [`plan-compartir-receta.md`](plan-compartir-receta.md). **Enmienda 1** (QR grande): Aprobado (2026-11-18). Plan aprobado: [`plan-qr-grande.md`](plan-qr-grande.md) (P1-P3 con la propuesta del plan). En implementación. **Enmienda 2** (QR con la marca): Aprobado (2026-11-20), pendiente de plan |
| Fecha | 2026-11-17. Enmienda 1: 2026-11-18. Enmienda 2: 2026-11-20 |
| Rama | `feat/share-recipe`, desde `develop` (`e64330a`). Enmienda 1: `feat/share-recipe-large-qr`, desde `develop` (`bbc96aa`). Enmienda 2: `feat/share-recipe-qr-style`, desde `develop` (`55359f8`) |
| Enmienda 1 | El QR deja de tener una caja fija de 432 px: cada módulo mide 6 px exactos y la caja crece con la versión del QR, lo que sube `MAX_URI_LENGTH` de 331 a 600 (el plan partía de 700 y la medición sobre la tarjeta real lo bajó). El QR pasa a una franja propia al final de la tarjeta y el alto de la tarjeta se ajusta al contenido. Si aun así no cabe, las instrucciones se recortan de una en una desde el último paso, no todas de golpe. Cambia §3.1, §3.2, §3.3, §3.4, §3.6, §4, §5, §6 y §7. Motivo: la receta 4:6 generada por la app (10 pasos, con notas e instrucciones) ocupa 450 caracteres y perdía notas e instrucciones al compartirse |
| Enmienda 2 | El QR de la tarjeta deja de ser negro genérico y lleva la marca: los módulos de datos se dibujan en el azul oscuro de la paleta (`#003EB3`) y los tres ojos (patrones de posición) se dibujan aparte, redondeados, con el anillo en el azul primario (`#1677FF`) y el centro en `#003EB3`. La matriz, la corrección M, los módulos de 6 px, la zona de silencio, el fondo blanco y `MAX_URI_LENGTH` = 600 no cambian, y la legibilidad se vuelve a medir. Cambia §3.3, §5, §6 y §7. Motivo: el usuario quiere que el QR se reconozca como de Pourline. Elegida sobre una maqueta con cuatro variantes (la actual, solo color, azul con ojos redondeados y tinta con ojos azules) |
| Deriva de | [SPEC-004](spec-catalogo-cafes.md) (alcance Out: "Compartir como texto o como archivo importable por otra instalación") |
| Módulos afectados | `:app`: `recipe` (nuevo códec), `share` (nueva tarjeta y QR), `ui/recipes` (compartir, importar, vista previa), `grinder` (resolución de molienda), `MainActivity` y manifiesto (intents de entrada), navegación |
| Specs relacionados | [SPEC-003](spec-curva-objetivo.md) (recetas), [SPEC-004](spec-catalogo-cafes.md) (imágenes para compartir), [SPEC-005](spec-navegacion-modos.md) (pestañas y modos), [SPEC-006](spec-molinos.md) (molinos y conversión) |

## 1. Resumen y Contexto

- **Problema:**
  - No se puede enseñar una receta fuera de la app: `share/` solo genera imágenes de cafés, de
    preparaciones y de comparaciones.
  - Para pasar una receta a otra persona que usa Pourline, esa persona tiene que teclear el nombre,
    la dosis y hasta 20 pasos (`RecipeValidator.MAX_STAGES`) con sus tiempos y su agua.
  - La app no tiene servidor ni dominio, y el manifiesto elimina `INTERNET`. No hay enlaces https
    que abran la app ni almacenamiento compartido.
- **Solución:**
  - **Una sola imagen** sirve para las dos cosas: muestra la receta completa y lleva un **QR** con
    la receta codificada.
  - Quien no tiene Pourline lee la imagen y sigue la receta. Quien la tiene, la importa leyendo el
    QR por cualquiera de estas cuatro entradas:
    - el escáner de la app;
    - una imagen de la galería;
    - la hoja de compartir del sistema;
    - la cámara del sistema o Google Lens, porque el QR es un enlace `pourline://`.
  - Toda importación pasa por una vista previa con Guardar y Cancelar.
- **Éxito:**
  - Un usuario comparte "V60 clásica" por WhatsApp. Quien la recibe en otro móvil con Pourline la
    importa en menos de 4 toques desde que abre la imagen, y obtiene una receta con los mismos
    pasos, tiempos, agua, dosis y temperatura.
  - Quien no tiene Pourline puede seguir la receta solo con la imagen.
- **Alcance (In):**
  - Icono "Compartir" en la barra superior del editor de receta (`RecipeEditScreen`), para todas las
    recetas, predefinidas incluidas, y en los modos Básico y Experto.
  - Tarjeta de receta (`RecipeShareCard`) con la receta completa y el QR.
  - Códec de la receta: modelo → URI → modelo, con versión, compresión y recorte por tamaño.
  - Importación por las cuatro entradas, con vista previa, resolución de molienda, detección de
    duplicados y aplazamiento durante una sesión.
- **Alcance (Out):**
  - Enlace a Google Play para quien no tiene la app: la app no está publicada.
  - Enlaces https / App Links, archivos `.pourline` y compartir como texto.
  - Compartir varias recetas a la vez, o importar varias de una sola imagen.
  - Compartir desde la fila de la lista de recetas (pulsación larga).
  - Sincronización o actualización de una receta ya importada cuando el autor la cambia.
  - Mostrar en la imagen las notas de la receta y las instrucciones de los pasos: viajan solo en el
    QR.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como aficionado, quiero compartir una receta como imagen en redes o mensajería para
  enseñar cómo preparo mi café.
- **US-2:** Como aficionado, quiero que un amigo que también usa Pourline añada mi receta a su lista
  sin teclear los pasos.
- **US-3:** Como receptor, quiero escanear con la app el QR que se ve en otra pantalla.
- **US-4:** Como receptor, quiero importar una receta desde una imagen guardada en la galería o
  mandándola a Pourline desde la hoja de compartir de WhatsApp o Fotos.
- **US-5:** Como receptor, quiero que la cámara del sistema o Google Lens me ofrezcan abrir el QR en
  Pourline.
- **US-6:** Como receptor, quiero revisar la receta antes de guardarla y elegir con qué molino
  propio la uso cuando no tengo el molino del autor.
- **US-7:** Como receptor sin Pourline, quiero entender la receta completa solo con la imagen.

## 3. Especificación Técnica

### 3.1 Formato del QR (contrato entre versiones de la app)

- **Contenido del QR:** un URI en modo byte.

  ```
  pourline://recipe?v=<versión>&d=<payload>
  ```

  - `v`: entero, versión del formato. Este spec define `v=1`.
  - `d`: `base64url` sin relleno de `deflate` (raw, nivel 9) del **payload binario** descrito abajo.
  - Corrección de errores del QR: **M**. Zona de silencio: 4 módulos.
- **Por qué binario:**
  - F0 midió que el QR de 432 px, reducido al 50 % y recomprimido en JPEG 70, solo se lee de forma
    fiable con URI de hasta unos 331 caracteres.
  - Con JSON, una receta de 20 pasos sin textos ocupaba entre 430 y 490 caracteres.
  - El binario la deja en torno a 200.
  - **Enmienda 1:** el límite real no era la capacidad del QR, sino el tamaño en píxeles de cada
    módulo. Con módulos de 6 px exactos el QR se lee 20 de 20 hasta 600 caracteres sobre la tarjeta real (§3.3, §6).
- **Primitivas del payload `v=1`:**
  - `uvar`: entero sin signo en LEB128 (7 bits por byte, el bit alto indica que sigue otro byte).
  - `svar`: entero con signo en zigzag sobre `uvar`.
  - `str`: `uvar` con la longitud en bytes, seguido de los bytes UTF-8.
  - Los campos van en el orden de la tabla, sin separadores ni relleno. Un campo opcional solo está
    presente si su bit en `flags` vale 1.
- **Cabecera y receta:**

  | Orden | Campo | Tipo | Contenido |
  |---|---|---|---|
  | 1 | `flags` | `uvar` | bits de presencia y de recorte, más la unidad de tiempo (tabla siguiente) |
  | 2 | `name` | `str` | `name`, tal como se muestra (las predefinidas sin renombrar, ya traducidas) |
  | 3 | `dose` | `uvar` | `doseG` en décimas de gramo |
  | 4 | `method` | `uvar` | código de `BrewMethod` (tabla abajo) |
  | 5 | `methodOther` | `str` | `methodOther`, solo con `OTHER` |
  | 6 | `temp` | `uvar` | `waterTempC` en décimas de °C |
  | 7 | `notes` | `str` | `notes` |
  | 8 | molienda | bloque | ver abajo |
  | 9 | `stageCount` | `uvar` | número de pasos, de 1 a 20 |
  | 10 | pasos | bloque × `stageCount` | ver abajo, ordenados por `startMs` |

  Bits de `flags`:

  | Bit | Significado |
  |---|---|
  | 0 | hay `method` |
  | 1 | hay `methodOther` |
  | 2 | hay temperatura |
  | 3 | hay notas |
  | 4 | hay molienda |
  | 5 | se recortaron las notas |
  | 6 | se recortaron las instrucciones |
  | 7 | se recortaron los nombres de paso |
  | 8-9 | unidad de tiempo: 0 = s, 1 = 0,1 s, 2 = ms |

  Un bit de `flags` distinto de los definidos, o bytes sobrantes al final, es `Corrupt`.
- **Códigos de `BrewMethod`:** fijos, sin depender del orden del enum. Un código desconocido es
  `Corrupt`.

  | Código | Método |
  |---|---|
  | 0 | `V60` |
  | 1 | `KALITA_WAVE` |
  | 2 | `CHEMEX` |
  | 3 | `ORIGAMI` |
  | 4 | `AEROPRESS` |
  | 5 | `FRENCH_PRESS` |
  | 6 | `ESPRESSO` |
  | 7 | `COLD_BREW` |
  | 8 | `MOKA` |
  | 9 | `OTHER` |
- **Bloque de molienda.** Se omite, con su bit a 0, si `grind` es `null` o `isEmpty`.

  | Orden | Campo | Tipo | Contenido |
  |---|---|---|---|
  | 1 | `gflags` | `uvar` | bit 0 `catalogKey`; bit 1 marca y modelo; bit 2 ajuste; bit 3 `label` |
  | 2 | `catalogKey` | `str` | `Grinder.catalogKey`, si el molino viene del catálogo |
  | 3 | `brand`, `model` | `str`, `str` | `Grinder.brand` y `Grinder.model` |
  | 4 | `value` | `svar` | `GrindSetting.value` en centésimas |
  | 5 | `label` | `str` | `GrindSetting.label`, solo si el molino ya fue eliminado (`grinderId = null`) |

  Con un molino eliminado solo viaja `label`.
- **Bloque de paso:**

  | Orden | Campo | Tipo | Contenido |
  |---|---|---|---|
  | 1 | `sflags` | `uvar` | bits 0-1: tipo (0 `POUR`, 1 `SWIRL`, 2 `WAIT`, 3 `ACTION` desde [SPEC-010](spec-paso-accion.md); antes, 3 era `Corrupt`). Bit 2: nombre. Bit 3: instrucción |
  | 2 | `gap` | `uvar` | tiempo desde el fin del paso anterior hasta el inicio de este (`startMs` en el primero), en la unidad de tiempo |
  | 3 | `duration` | `uvar` | `durationMs`, en la unidad de tiempo |
  | 4 | `water` | `uvar` | solo en `POUR`: agua que añade sobre el acumulado del `POUR` anterior (o sobre 0), en décimas de gramo |
  | 5 | `name` | `str` | `RecipeStage.name`, tal como se muestra |
  | 6 | `instruction` | `str` | `instruction` |

  Al decodificar, el agua de `SWIRL` y `WAIT` se deriva con `withDerivedWater()`.
- **Unidad de tiempo:** el codificador elige la mayor de s, 0,1 s y ms que divida exactamente todos
  los `startMs` y `durationMs`. El tiempo nunca pierde precisión.
- **Precisión:**
  - Dosis y temperatura: exactas, porque `RecipeValidator` ya exige un decimal como máximo.
  - Agua: se redondea a décimas de gramo. Es exacta para valores con un decimal como máximo.
  - Ajuste de molienda: se redondea a centésimas. Todos los molinos del catálogo usan paso 1.0.
- **Versionado:**
  - Cualquier cambio que una versión anterior de la app no pueda representar incrementa `v`.
    Por ejemplo: un valor nuevo de `BrewMethod` o de `StageType`, o un campo nuevo.
  - Una app que lee `v` mayor que la suya rechaza el QR (§4).
  - Dentro de la misma `v`, un bit, código o byte inesperado es un error de formato.
- **No viaja:** `id`, `builtInKey`, `localizedNames`, `grinderId`, ni nada del usuario (cafés,
  preparaciones, molinos propios).

### 3.2 Códec y recorte por tamaño (`dev.josueamaya.pourline.recipe.share`, JVM puro)

- Contratos:

  ```kotlin
  /** El resultado de codificar: el URI y lo que hubo que quitar para que cupiera. */
  data class EncodedRecipe(val uri: String, val trimmed: Set<TrimmedPart>)
  enum class TrimmedPart { NOTES, INSTRUCTIONS, STAGE_NAMES }

  /** Lo decodificado: la receta sin id ni molino local, y la molienda tal como viajó. */
  data class SharedRecipe(val recipe: Recipe, val grind: SharedGrind?, val trimmed: Set<TrimmedPart>)
  data class SharedGrind(val catalogKey: String?, val brand: String?, val model: String?, val value: Double?, val label: String?)

  sealed interface DecodeResult {
    data class Ok(val shared: SharedRecipe) : DecodeResult
    data object NotPourline : DecodeResult     // otro esquema, otro host o sin `d`
    data object NewerVersion : DecodeResult    // `v` > versión soportada
    data object Corrupt : DecodeResult         // base64, deflate o binario inválidos
    data object Invalid : DecodeResult         // no pasa RecipeValidator
  }

  object RecipeShareCodec {
    const val VERSION = 1
    fun encode(recipe: Recipe, grinder: Grinder?): EncodedRecipe?  // null: no cabe ni recortando
    fun decode(text: String): DecodeResult
  }
  ```

- **Tope de tamaño:**
  - Se mide sobre la longitud del URI: `MAX_URI_LENGTH`.
  - El plan fija su valor con el test de legibilidad (§5): el QR de un URI de esa longitud tiene que
    leerse tras reducir la imagen a la mitad y recomprimirla en JPEG calidad 70.
  - **Enmienda 1:** `MAX_URI_LENGTH = 600` (QR versión 19, corrección M), con el QR dibujado a 6 px
    por módulo (§3.3). Medido el 2026-11-18 en `emulator-5560` con el lector de Play Services, sobre la
    tarjeta real (ver §6): 20 de 20 en las tres lecturas para 600 caracteres; a 625 y 650 falla 1 de 20
    al reducir y recomprimir, a 675 también 1 y a 700 fallan 3. La primera versión de la enmienda
    fijaba 700 con una medición sobre un lienzo de prueba, que no valía para la tarjeta.
- **Orden del recorte** (reescrito por la enmienda 1), que se detiene en cuanto el URI cabe:
  1. Receta completa.
  2. Sin las notas.
  3. Además, sin la instrucción del último paso que la tenga; se repite, de un paso en uno hacia el
     primero, hasta que cabe o no queda ninguna instrucción.
  4. Además, sin los nombres de los pasos (todos a la vez).
  5. Si aún no cabe, `encode` devuelve `null` (§4).

  Lo quitado se anota en los bits 5-7 de `flags`: el bit 6 vale 1 si se quitó al menos una
  instrucción. El formato `v=1` no cambia y las apps ya instaladas leen estos QR. Pasos, tiempos,
  agua, dosis, temperatura, método y molienda nunca se recortan.
- **Recorte parcial de instrucciones (enmienda 1):** quien comparte y quien importa distinguen si
  faltan todas las instrucciones o solo algunas.
  - Al compartir, `encode` informa cuántas instrucciones quitó. El plan decide cómo se refleja en
    `EncodedRecipe`; `TrimmedPart.INSTRUCTIONS` sigue indicando que se quitó al menos una.
  - Al importar, "algunas" es: bit 6 a 1 y al menos un paso decodificado con instrucción.
- **`decode`:**
  - Acepta el URI exacto y también con espacios alrededor.
  - Rechaza esquema distinto de `pourline` o host distinto de `recipe`.
  - Construye la `Recipe` con `id = 0`, `builtInKey = null`, `localizedNames = false` y
    `grind = null`. La molienda se resuelve después (§3.6).
  - Valida con `RecipeValidator`.
- Sin `import android` en el paquete. Se comprueba con `grep`, como en `history`.

### 3.3 Tarjeta para compartir (`share/RecipeShareCard.kt`)

- **Marco:** reutiliza `ShareFrame`, `ShareCaptureHost`, el PNG en `cacheDir/shared/`, `FileProvider`
  y `ShareSender` (SPEC-004, D30).
- **Tamaño** (reescrito por la enmienda 1):
  - Ancho fijo de 360 dp (1080 px a `SHARE_DENSITY` = 3).
  - Alto ajustado al contenido, con un mínimo de 450 dp (1080 × 1350 px, 4:5). Sustituye a los dos
    tamaños fijos (4:5 hasta 10 pasos y 9:16 desde 11) y a `RECIPE_CARD_COMPACT_MAX_STAGES`.
  - Todos los pasos se muestran siempre.
- **Contenido:**
  - **Cabecera:** nombre y método. Con `OTHER`, el texto de `methodOther`.
  - **Datos:** dosis, agua total, ratio, temperatura y molienda. La molienda es el texto que muestra
    el editor (`GrindFormat.label`) o el `label` del molino eliminado. Los datos ausentes no se
    muestran.
  - **Pasos:** una fila compacta por paso con el tipo, el nombre si hay, el inicio (`m:ss`), la
    duración y el agua acumulada (solo en `POUR`). Usa los formateadores de `RecipeFormatters`.
  - **QR** (reescrito por la enmienda 1):
    - Va en una franja propia al final de la tarjeta, debajo de los pasos y encima del pie,
      centrado y con el texto "Escanéalo con Pourline para añadir esta receta" debajo.
    - La cabecera (nombre, método y datos) ocupa todo el ancho, porque el QR ya no va a su lado.
    - Cada módulo mide exactamente 2 dp, es decir 6 px enteros, sin antialias. La caja mide
      `(módulos + 8) × 6` px, zona de silencio de 4 módulos por lado incluida: unos 462 px en
      versión 13 y 606 px en versión 19, el máximo con `MAX_URI_LENGTH`.
    - Se dibuja sobre fondo blanco, también con tema oscuro. Los colores (reescrito por la
      enmienda 2) son constantes de la tarjeta y no dependen del tema:
      - **Módulos de datos:** `#003EB3` (azul 8 de la paleta de Ant generada desde `primary`
        `#1677FF`; contraste 9,0:1 sobre blanco). Siguen siendo cuadrados de 6 px enteros sin
        antialias.
      - **Ojos** (los tres patrones de posición de 7 × 7 módulos, arriba a la izquierda, arriba a la
        derecha y abajo a la izquierda): no se dibujan módulo a módulo, sino como dos piezas
        redondeadas encima de la matriz. El anillo (7 × 7 con el hueco de 5 × 5) va en `#1677FF`
        (contraste 4,1:1) y el centro (3 × 3) en `#003EB3`. Radios de referencia, los de la maqueta
        aprobada: 2,1 módulos en el borde exterior del anillo, 1,3 en el hueco y 0,9 en el centro. El
        plan los fija en píxeles enteros. Solo las esquinas curvas llevan antialias; los bordes rectos
        caen en píxeles enteros, como el resto de módulos.
      - El separador blanco de cada ojo y la zona de silencio no cambian.
      - Si la medición de §6 no llega a 20/20, el anillo pasa a `#003EB3` (todo el ojo en azul
        oscuro) y se mide de nuevo.
  - **Orden:** cabecera, datos, pasos, franja del QR y pie. Sin QR (`encode` devolvió `null`), la
    franja no aparece.
  - **Pie:** el de las otras tarjetas.
- **Unidad e idioma:**
  - Pesos siempre en gramos, igual que las recetas en el resto de la app. `WeightUnit` es la
    unidad de la báscula, no una preferencia del usuario, así que no se usa aquí.
  - Textos en el idioma de quien comparte.
  - El QR va siempre en gramos (§3.1).
- **Generación del QR:** `com.google.zxing:core`, Java puro, en una matriz de módulos que la tarjeta
  dibuja con `Canvas`.
- Sin notas ni instrucciones de paso en la imagen (alcance Out).

### 3.4 Flujo de compartir

1. El usuario toca el icono "Compartir" de la barra superior de `RecipeEditScreen`.
   - Se comparte la receta **guardada**.
   - Con cambios sin guardar, o con una receta nueva sin guardar, el icono está deshabilitado.
2. `RecipeShareCodec.encode(recipe, grinder)`.
3. Según el resultado:
   - `trimmed` no vacío: diálogo "Esta receta es muy larga para el QR. Al importarla faltarán …",
     con la lista de lo recortado ("las notas", "las instrucciones de los pasos", "los nombres de
     los pasos"). Botones Compartir y Cancelar. Si solo se quitaron algunas instrucciones, el texto
     es "las instrucciones de algunos pasos" (enmienda 1).
   - `null`: diálogo "Esta receta es demasiado larga para importarse con QR. Se compartirá solo la
     imagen.", con Compartir y Cancelar. La tarjeta se genera sin QR y sin el texto del QR.
4. Se genera el PNG y se abre el selector con `ACTION_SEND` (`image/png`) a través de `ShareSender`.
   Pourline se excluye del selector con `EXTRA_EXCLUDE_COMPONENTS`, porque su filtro `SEND image/*`
   la haría aparecer como destino de sus propias imágenes.

### 3.5 Entradas de importación

- **Barra superior de `RecipesScreen`:** un icono "Importar" abre un menú con dos acciones:
  - **"Escanear QR":** Google Code Scanner (`play-services-code-scanner`, solo
    `Barcode.FORMAT_QR_CODE`). No pide permiso de cámara ni usa CameraX.
  - **"Desde una imagen":** el selector de fotos del sistema (`PickVisualMedia`, solo imágenes).
- **Hoja de compartir del sistema:** `MainActivity` declara `ACTION_SEND` con `image/*`. Pourline
  aparece al compartir una imagen desde WhatsApp, Fotos, etc.
- **Cámara del sistema y Lens:** `MainActivity` declara `ACTION_VIEW`, categorías `DEFAULT` y
  `BROWSABLE`, esquema `pourline`, host `recipe`.
- **Una sola instancia de `MainActivity`** (`android:launchMode="singleTask"`):
  - Una sesión en curso nunca se duplica.
  - La actividad procesa el intent en `onCreate` y en `onNewIntent`.
  - El intent ya procesado no se vuelve a procesar al recrearse la actividad (rotación, proceso
    restaurado).
- **Lectura desde imagen** (galería y `ACTION_SEND`):
  - Se decodifica el bitmap reducido a un máximo de 2048 px de lado.
  - Se pasa por ML Kit en su variante **de Google Play Services**
    (`play-services-mlkit-barcode-scanning`), solo QR. El modelo lo descarga Play Services y no
    va en el APK.
  - Se toma el primer QR cuyo texto empiece por `pourline://recipe`.
- **Destino común:**
  - Todas las entradas producen un texto de QR, que se pasa a `RecipeShareCodec.decode`.
  - Con `Ok`, se navega a la vista previa en la pestaña Recetas (`Tab.RECIPES`).
  - Con otro resultado, se muestra el error (§4).

### 3.6 Vista previa e importación

- **Navegación:** nueva clave `@Serializable data class RecipeImport(val uri: String) : NavKey`. La
  pantalla vuelve a decodificar el URI, así que sobrevive a la recreación.
- **Contenido** (`ui/recipes/RecipeImportScreen.kt`), de solo lectura:
  - Los mismos datos y pasos que la tarjeta, más las notas y las instrucciones de los pasos.
  - Pesos en gramos.
- **Avisos** (`AntMessage` o tarjeta de aviso, encima del contenido):
  - Con `trimmed` no vacío: "La receta original tenía … que no cupieron en el QR". Con recorte
    parcial de instrucciones (§3.2), dice "instrucciones de algunos pasos" (enmienda 1).
  - Si es **idéntica** a una receta existente: "Ya tienes esta receta". No bloquea el guardado.
    - Idéntica significa: mismo nombre (comparado sin mayúsculas ni espacios extremos), misma dosis,
      mismo método, misma temperatura y mismos pasos (tipo, nombre, inicio, duración, agua de
      `POUR` e instrucción).
- **Molienda.** Se resuelve con `SharedGrind` y los molinos del receptor (`GrinderRepository`):
  1. **Mismo molino:**
     - Hay un molino propio y no retirado (`owned && !retired`) con el mismo `catalogKey`.
     - Sin `catalogKey`: con la misma marca y el mismo modelo, sin distinguir mayúsculas.
     - Se enlaza: `GrindSetting(grinderId = suyo, value = v, label = <copia de texto>)`. La copia de
       texto se regenera al guardar, como en SPEC-006 (D17).
     - Si el ajuste `v` no existe en la escala del molino del receptor, se guarda como texto
       (caso 3).
  2. **Otro molino con conversión:**
     - El molino del autor existe en la base del receptor por `catalogKey` (el catálogo está
       sembrado) y hay `v`.
     - Se calcula `GrindConverter.convert(v, from = molino del catálogo, to = cada molino propio,
       pairs = calibraciones del receptor)` para cada molino propio con equivalencia.
     - La vista previa muestra un selector:
       - "Usar con <molino propio> · <ajuste convertido>", uno por molino convertible;
       - "Guardar como texto".
     - Se preselecciona el primer molino convertible en el orden de la pantalla Molinos.
     - Una conversión redondeada se marca con "≈".
     - Los molinos retirados no se ofrecen ni para enlazar ni para convertir.
  3. **Texto:**
     - En los demás casos: molino propio del autor sin `catalogKey` que el receptor no tiene,
       molino eliminado, sin ajuste o sin molinos convertibles.
     - Se guarda `GrindSetting(grinderId = null, value = null, label = "<marca> <modelo> · <ajuste>")`,
       o `l` tal cual, que es el estado "molino eliminado" de SPEC-006 (D16).
     - Si no hay ajuste, el texto es solo "<marca> <modelo>".
     - Si el molino del autor no está en la base del receptor, `<ajuste>` es el número `v` sin
       formato de unidades.
  - Sin `g`, la receta se guarda sin molienda.
- **Guardar:**
  - Si ya existe una receta con el mismo nombre (misma comparación que arriba), se añade el sufijo
    " (2)", " (3)"… hasta que no coincida.
  - `RecipeRepository.save(recipe)`.
  - Se quita solo la vista previa de la pila, para no perder un editor abierto debajo, y se muestra
    el mensaje "Receta añadida".
- **Cancelar:** vuelve atrás sin guardar.

### 3.7 Aplazamiento durante una sesión

- **Sesión en curso:** `ScaleUiState.hasSession` es `true`, con o sin báscula.
- Si una importación llega por `ACTION_SEND` o `ACTION_VIEW` con una sesión en curso:
  - El texto del QR ya decodificado (`Ok`) se guarda en un `PendingRecipeImport` en memoria de la
    app, en `AppDependencies`. Solo se guarda uno y el último reemplaza al anterior.
  - La sesión no se interrumpe ni se navega. Se muestra el mensaje "Receta recibida. La verás al
    terminar la preparación."
  - Cuando `hasSession` pasa a `false`, se navega a `RecipeImport` y se vacía el pendiente.
- Las entradas internas (escáner y galería) no existen durante una sesión, porque la pantalla de
  sesión no muestra Recetas.

### 3.8 Dependencias nuevas

| Dependencia | Uso |
|---|---|
| `com.google.zxing:core` | Generar la matriz del QR (JVM puro) |
| `com.google.android.gms:play-services-mlkit-barcode-scanning` | Leer QR desde un bitmap. El modelo lo descarga Play Services; el incluido en el APK sumaba 20,8 MB (medido en F0) |
| `com.google.android.gms:play-services-code-scanner` | Escáner de cámara sin permiso de cámara |

- El manifiesto final sigue sin `INTERNET` y sin `CAMERA`.
- El módulo del escáner lo descarga Google Play Services, no la app.

### 3.9 Flujo de ejecución

```
Compartir:  RecipeEditScreen ─► RecipeShareCodec.encode ─► (diálogo si recorte/null)
            ─► RecipeShareCard (+ QR zxing) ─► ShareCaptureHost ─► PNG ─► ShareSender(ACTION_SEND)

Importar:   Escáner / Galería / ACTION_SEND image / ACTION_VIEW pourline://
            ─► texto del QR (Code Scanner | ML Kit sobre bitmap | data del intent)
            ─► RecipeShareCodec.decode
                ├─ error ─► mensaje (§4)
                └─ Ok ─► ¿hasSession? ─ sí ─► PendingRecipeImport ─► (al terminar) ─┐
                                        └ no ───────────────────────────────────────┴► RecipeImport
            ─► resolver molienda ─► avisos ─► Guardar (sufijo de nombre) ─► RecipeRepository.save
```

## 4. Casos Límite y Manejo de Errores

- **QR de otra cosa** (URL, texto, otra app), leído con el escáner -> `NotPourline` -> "Este QR no
  es una receta de Pourline". En una imagen, un QR ajeno se ignora y se aplica el caso siguiente.
- **Imagen sin QR** o sin QR de Pourline (galería o `ACTION_SEND`) -> "No se encontró ninguna receta
  en la imagen".
- **Falta `v`** o no es un entero -> `Corrupt`.
- **Versión futura** (`v` > 1) -> "Esta receta se creó con una versión más nueva de Pourline.
  Actualiza la app para importarla." No se importa nada.
- **Payload corrupto** (base64 o deflate inválidos, binario truncado, bytes sobrantes, o bit o
  código desconocido en `v=1`) -> `Corrupt`
  -> "No se pudo leer la receta del QR".
- **Payload que no pasa `RecipeValidator`** (dosis fuera de rango, 0 o más de 20 pasos, tiempos
  inválidos…) -> `Invalid` -> "La receta del QR no es válida". No se abre la vista previa.
- **`BrewMethod` desconocido en `v=1`** -> `Corrupt`. Un método nuevo exige `v=2` (§3.1).
- **Receta que no cabe ni recortando** -> se comparte la imagen sin QR, con aviso previo (§3.4).
- **Receta que no pasa `RecipeValidator` al compartir** -> hoy `encode` devuelve `null` y se muestra
  el mismo aviso de "demasiado larga". Con la enmienda 1, el aviso distingue los dos casos: si la
  receta guardada no es válida, "Esta receta tiene datos no válidos y no se puede importar con QR.
  Se compartirá solo la imagen."
- **Fallo al generar la imagen** -> `AntMessage` de error y no se abre el selector, como en
  SPEC-004.
- **Escáner no disponible** (sin Google Play Services, o módulo sin descargar y sin conexión) ->
  "El escáner no está disponible. Prueba con «Desde una imagen»".
- **El usuario cancela el escáner o el selector de fotos** -> no pasa nada.
- **El modelo de lectura de QR de Play Services aún no está descargado** (galería o `ACTION_SEND`)
  -> se pide la descarga y se muestra "El lector de QR se está preparando. Inténtalo en unos
  segundos".
- **Imagen ilegible o enorme** -> se reduce a 2048 px. Si no se puede decodificar, se muestra "No se
  encontró ninguna receta en la imagen".
- **`ACTION_SEND` sin `EXTRA_STREAM`, o URI sin permiso de lectura** -> el mismo mensaje.
- **La cámara del sistema no ofrece abrir `pourline://`** (depende del fabricante) -> sin solución en
  la app. La galería y el escáner siguen funcionando. Se documenta en la prueba manual.
- **Llega una receta durante una sesión** -> se aplaza (§3.7). Si llega otra, reemplaza a la
  pendiente.
- **El proceso muere con una importación aplazada** -> se pierde. Se acepta porque el remitente
  puede reenviarla.
- **Receta idéntica ya existente** -> aviso, se permite guardar con sufijo.
- **Mismo nombre, contenido distinto** -> se guarda con sufijo, sin aviso.
- **El molino enlazado o convertido se retira o se borra antes de guardar** -> al guardar se
  vuelve a resolver la molienda. Si el molino ya no es válido, se guarda como texto.
- **Receta predefinida compartida** -> el receptor obtiene una receta propia (`builtInKey = null`),
  con los nombres en el idioma del remitente.
- **Rotación o recreación en la vista previa** -> se conserva, porque `RecipeImport(uri)` se vuelve a
  decodificar. El intent externo no se reprocesa (§3.5).
- **Privacidad:** el QR solo lleva los datos de la receta (§3.1). No incluye el nombre del usuario,
  cafés, preparaciones ni identificadores.

## 5. Criterios de Aceptación (Definition of Done)

- [ ] El icono "Compartir" del editor genera una imagen con la receta completa y un QR, y abre el
      selector con `ACTION_SEND` `image/png`, en los modos Básico y Experto y también con recetas
      predefinidas.
- [ ] La imagen mide 1080 px de ancho y al menos 1350 px de alto, crece con el contenido y muestra
      todos los pasos y el QR de una receta de 20 pasos con el URI en `MAX_URI_LENGTH` (enmienda 1).
- [ ] **Enmienda 1:** en el PNG generado, cada módulo del QR mide 6 × 6 px exactos y la caja mide
      `(módulos + 8) × 6` px. Lo comprueba un test instrumentado leyendo los píxeles.
- [ ] **Enmienda 1:** la receta 4:6 equilibrada e intensidad media de 18,8 g, con molino del catálogo
      (la que se recortaba en el Xiaomi), se comparte sin recortar nada.
- [ ] **Enmienda 2:** en el PNG generado, los módulos de datos son `#003EB3`, el anillo de cada ojo
      `#1677FF` (o `#003EB3` si se aplicó la salida de §6) y su centro `#003EB3`, y el fondo blanco.
      Cada módulo de datos sigue midiendo 6 × 6 px y la caja `(módulos + 8) × 6` px. Lo comprueba un
      test instrumentado leyendo los píxeles con el mismo margen de 8 niveles por canal que hoy, ahora
      contra el color esperado de cada zona en lugar de contra negro. Las esquinas curvas de los ojos
      quedan fuera de la comprobación píxel a píxel.
- [ ] **Enmienda 2:** `RecipeQrLegibilityTest#cardSweep` sobre la tarjeta real da 20/20 con
      `MAX_URI_LENGTH` = 600 en las tres lecturas (tal cual, 50 % + JPEG 70 y reducida a 2048 px), con
      20 muestras y semillas nuevas. El resultado queda anotado en §6.
- [ ] **Enmienda 2:** los tests existentes de la tarjeta, compartir e importar (`ShareCardsLayoutTest`,
      `RecipeShareTest`, `RecipesImportTest`, `RecipeIntentImportTest`, `RecipeQrLegibilityTest`,
      `QrLegibilityTest`) pasan. Solo cambian las expectativas que comprobaban el color negro.
- [ ] **Enmienda 2, manual en el Xiaomi:** compartir una receta por WhatsApp y volver a importarla desde
      la imagen recibida, por la galería y por el escáner.
- [ ] **Unitario:** ida y vuelta. `decode(encode(r))` reproduce nombre, método, `methodOther`, dosis,
      temperatura, notas y pasos (tipo, nombre, inicio, duración, agua e instrucción) de las
      predefinidas y de una receta con todos los campos.
- [ ] **Unitario:** recorte en orden notas → instrucciones de una en una desde el último paso →
      nombres de paso, con los bits de recorte y `trimmed` correctos. Se quitan solo las
      instrucciones necesarias. `null` solo cuando ni el recorte completo cabe (enmienda 1).
- [ ] **Unitario:** una receta inválida y una que no cabe dan avisos distintos (enmienda 1).
- [ ] **Unitario:** `NotPourline`, `NewerVersion`, `Corrupt` (base64, deflate, binario
      truncado, bytes sobrantes, bit o código desconocido) e `Invalid`.
- [ ] **Unitario:** resolución de molienda en los tres casos (mismo molino por `catalogKey` y por
      marca y modelo, conversión con varios molinos propios, texto) y sin molienda.
- [ ] **Unitario:** detección de idéntica y sufijo " (2)" y " (3)".
- [ ] **Unitario:** el paquete `recipe.share` no importa `android.*` (test JVM, además del `grep`).
- [ ] **Instrumentado:** el QR del PNG generado con la receta de peor caso se decodifica con ML Kit.
      La receta de peor caso tiene el URI en `MAX_URI_LENGTH`, 20 pasos y un nombre de 60
      caracteres. La lectura funciona tal cual y tras reducir la imagen al 50 % y recomprimirla en
      JPEG calidad 70.
- [ ] **Instrumentado:** compartir envía por `ShareSender` un `ACTION_SEND` con un PNG legible por
      `FileProvider`.
- [ ] **Instrumentado:** un intent `ACTION_VIEW pourline://recipe?...` y un `ACTION_SEND image/*` con
      un PNG generado abren `RecipeImport`. Guardar añade la receta a la lista con el mensaje
      "Receta añadida".
- [ ] **Instrumentado:** con una sesión en curso, el intent no navega. Al terminar la sesión se abre
      la vista previa.
- [ ] **Instrumentado:** una imagen sin QR y un QR ajeno muestran sus mensajes.
- [ ] El manifiesto final (`processDebugManifest`) no contiene `android.permission.INTERNET` ni
      `android.permission.CAMERA`.
- [ ] Textos nuevos en `values` (es) y `values-en`.
- [ ] `:app:lintDebug` y los tests existentes pasan sin cambiar sus expectativas.
- [ ] **Manual, con dos móviles:**
  - compartir por WhatsApp;
  - importar por las cuatro entradas (escáner, galería, compartir hacia Pourline y cámara del
    sistema), anotando el fabricante si la cámara no ofrece el enlace;
  - comprobar la conversión de molienda con un molino distinto.

## 6. Preguntas abiertas y riesgos

- **`MAX_URI_LENGTH`:**
  - F0 midió 331 con el QR de 432 px y el lector incluido en el APK.
  - Hay que volver a medirlo con el lector de Play Services.
  - Con el formato binario, la receta de 20 pasos sin textos debe quedar muy por debajo; el
    plan fija el margen mínimo.
- **Code Scanner sin `INTERNET`:**
  - La primera fase del plan debe verificar en el dispositivo que funciona con el permiso eliminado.
    El módulo lo descarga Play Services.
  - Si no funciona, la alternativa es CameraX + ML Kit, que exige el permiso `CAMERA`. Esa decisión
    vuelve al usuario.
- **ML Kit de Play Services:**
  - Confirmar que no reintroduce `INTERNET` tras el `tools:node="remove"` actual.
  - Confirmar que lee QR con el permiso eliminado, una vez descargado el modelo.
  - Mientras el modelo no esté descargado, la lectura desde imagen falla: ver §4.

- **Al cerrar la implementación:** anotar en este spec el valor final de `MAX_URI_LENGTH` y el
  número de pasos a partir del cual la tarjeta pasa de 4:5 a 9:16.
  - **Valores finales (2026-11-18):**
    - `MAX_URI_LENGTH` = 331 caracteres (QR versión 13, corrección M, 432 px = 144 dp), medido con el
      lector de Play Services. La receta de 20 pasos de peor caso queda en 231 a 267.
    - La tarjeta es 4:5 (1080 × 1350) hasta `RECIPE_CARD_COMPACT_MAX_STAGES` = 10 pasos y 9:16
      (1080 × 1920) a partir de 11.
  - **Enmienda 1, medición previa (2026-11-18, `emulator-5560`, `QrLegibilityTest#sweep`):**
    - Con caja fija, la lectura tras reducir al 50 % y JPEG 70 es irregular. A 720 px, por ejemplo,
      500 caracteres dan 3 de 5 y 600 dan 5 de 5. La causa es que el módulo mide un número no entero
      de píxeles.
    - Con módulos de píxeles enteros: a 4 px se pierden lecturas desde 400 caracteres. A 6 px, 20 de
      20 hasta 700 y 19 de 20 a 750. A 8 px, 20 de 20 de 650 a 900, pero con una caja de hasta 968 px.
    - Se eligen 6 px y 700 caracteres (el valor final es 600: ver el cierre de la enmienda).
  - **Enmienda 1, riesgo:** la muestra es de 20 URIs aleatorias por longitud y en un solo emulador.
    El plan vuelve a medir con 20 muestras por longitud alrededor de `MAX_URI_LENGTH` sobre la
    tarjeta real, y si falla alguna, baja el valor.
  - **Cierre de la enmienda 1 (2026-11-18):**
    - Medición sobre la tarjeta real (`RecipeQrLegibilityTest#cardSweep`, receta de peor caso, 20
      muestras por longitud con semillas nuevas y tres lecturas: tal cual, 50 % + JPEG 70 y reducida a
      2048 px como la lee la importación): 600 caracteres, 20/20 en las tres. 625 y 650: 20/20, 19/20 y
      20/20. 675: 20/20, 19/20 y 20/20. 700: 20/20, 17/20 y 20/20. De 300 a 550 acierta todo (10
      muestras). Por la regla del plan se fija **`MAX_URI_LENGTH` = 600**, no 700. Los fallos son solo de
      la lectura tras reducir y recomprimir y no son monótonos con la longitud.
    - La receta 4:6 del usuario mide 450 caracteres (versión 16) y se comparte sin recortar nada.
    - Alto de la tarjeta de peor caso (20 pasos, nombre de 2 líneas, molienda de 2 líneas, URI de 576 a
      600 caracteres, QR de versión 19): **1080 × 2328 px**. Sin la molienda en dos líneas (E2E con URI de
      576 caracteres), 1080 × 2226 px. La receta 4:6 del usuario: 1080 × 1656 px. La receta corta: 1080 ×
      1350 px.
    - La caja del QR se coloca en píxeles pares (hasta 1 px a la izquierda del centro) y la captura de
      Compose mezcla un 1 % en el primer píxel de cada módulo, por lo que los tests comprueban "negro" y
      "blanco" con 8 niveles de margen.
  - **Enmienda 2, riesgo:** los lectores binarizan la imagen, y el azul de los datos (9,0:1) y el de
    los anillos (4,1:1) quedan oscuros, pero la reducción al 50 % con JPEG 70 mezcla el color con el
    blanco y puede aclarar más el anillo que el negro. Por eso se vuelve a medir con
    `RecipeQrLegibilityTest#cardSweep` en 600 caracteres antes de dar la enmienda por buena.
    - **Salida decidida por el usuario:** si alguna de las tres lecturas baja de 20/20, el anillo pasa
      de `#1677FF` a `#003EB3` y se repite la medición. `MAX_URI_LENGTH` no baja por esta enmienda.
    - Si tampoco así llega a 20/20, la decisión vuelve al usuario.
  - **Enmienda 2, pendiente:** anotar aquí el resultado de la medición (lecturas por longitud) y si se
    aplicó la salida.

## 7. Alternativas descartadas

- **Archivo `.pourline` adjunto:** algunas apps de mensajería no ofrecen abrir archivos con tipos
  desconocidos y exige compartir dos cosas.
- **Enlace `pourline://` como texto:** muchas apps de mensajería no lo hacen pulsable.
- **Enlaces https / App Links:** requieren dominio y servidor que la app no tiene.
- **Formulario de edición prellenado al importar:** abruma más que una vista previa; tras guardar la
  receta se edita como cualquier otra.
- **Bloquear el QR si no cabe / limitar longitudes al editar:** se prefiere recortar textos y
  avisar, sin cambiar el editor.
- **Abrir la vista previa encima de una sesión:** interrumpe la preparación.
- **Códigos para los textos que genera la app (enmienda 1):** enviar un código en vez de las
  instrucciones y notas de 4:6 y de las predefinidas. Bajaba la receta 4:6 a unos 150 caracteres,
  pero exigía `v=2` y no ayudaba con los textos del usuario. El usuario eligió solo el QR grande.
- **Módulos de 8 px y 900 caracteres (enmienda 1):** el QR ocuparía casi todo el ancho (hasta
  968 px), y también en recetas cortas.
- **Recortar todas las instrucciones de golpe:** era el comportamiento original. La enmienda 1 las
  quita de una en una para conservar las que caben.
- **Solo color, con ojos cuadrados (enmienda 2):** todo el QR en `#003EB3`. Cambia poco respecto al
  negro y no se parece al icono.
- **Tinta casi negra con ojos azules (enmienda 2):** datos en `#0B1A33` y la marca solo en los ojos.
  Arriesgaba menos en la lectura, pero el usuario prefirió el azul en todo el código.
- **Módulos redondeados o en puntos (enmienda 2):** reducen el área oscura de cada módulo y, con 6 px,
  ponen en riesgo la lectura tras la compresión de las apps de mensajería.
- **Logo en el centro (enmienda 2):** exige subir la corrección de M a Q o H, lo que agranda el QR o
  baja `MAX_URI_LENGTH`.
- **Colores invertidos (claro sobre oscuro, enmienda 2):** varios lectores no los leen.
- **Importar QR de versión futura ignorando campos:** podría crear recetas incompletas sin que el
  usuario lo sepa.
