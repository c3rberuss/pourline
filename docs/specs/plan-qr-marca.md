# Plan de implementación: SPEC-009 Enmienda 2, QR con la marca

| Campo | Valor |
|---|---|
| Spec | [`spec-compartir-receta.md`](spec-compartir-receta.md) (SPEC-009), **Enmienda 2** (§3.3, §5, §6 y §7) |
| Estado | Aprobado (2026-11-20), P1-P3 con la propuesta del plan, salvo la regla de la medición (decisión del usuario del 2026-11-20, ver "Decisión tras F0"). Commits por fase autorizados. Implementado (F0 a F3, 2026-11-20); pendiente la prueba manual del usuario en el Xiaomi |
| Fecha | 2026-11-20 |
| Rama | `feat/share-recipe-qr-style`, desde `develop` (`55359f8`), con el spec commiteado (`7464c1d`) |
| Módulos | `:app` (`:protocol` no cambia) |
| Planes anteriores | [`plan-compartir-receta.md`](plan-compartir-receta.md) (D1 a D29) y [`plan-qr-grande.md`](plan-qr-grande.md) (D30 a D46). Este plan sigue desde D47 |
| Colores | Módulos de datos y centro de los ojos `#003EB3`; anillo de los ojos `#1677FF`, o `#003EB3` si F2 aplica la salida del §6 (D54) |
| `MAX_URI_LENGTH` | 600, sin cambios: la enmienda no lo baja (§6) |
| Línea base | F0 (2026-11-20): JVM 106 tests en 11 clases, 0 fallos; `emulator-5560`, seis clases 68 tests, 0 fallos, 1 omitido (`cardSweep`); lint 2 errores y 20 avisos previos, corregidos los 2 errores en un commit aparte: 0 errores y 20 avisos (ver "Resultado de F0") |
| Resultado de la medición | F2: la marca iguala o supera al negro en todos los puntos; sin salida, anillo en `#1677FF` (ver "Resultado de F2") |

La enmienda cambia solo cómo se pinta el QR de la tarjeta de receta. La matriz de zxing, la corrección M, los módulos de
6 px, la zona de silencio, el fondo blanco, la posición de la caja (D31) y `MAX_URI_LENGTH` no cambian. El plan ordena
el trabajo para que la legibilidad se mida cuanto antes:

1. **Referencia (F0).** Línea base de las clases afectadas y una medición del QR negro de hoy con las semillas que va a
   usar la medición de la marca. Sin tocar código.
2. **Colores y geometría (F1).** Las dos constantes de color y la geometría de los ojos, en código puro con tests JVM.
   La tarjeta todavía no cambia.
3. **La marca en la tarjeta y la medición (F2).** `QrCodeBox` pinta la marca, `findQrBox` deja de buscar negro y lo
   siguiente es medir sobre la tarjeta real. Con la medición en 20/20 (con o sin la salida del §6), se adaptan los
   tests que comprobaban el negro.
4. **Verificación y cierre (F3).** Suites afectadas, E2E por adb, capturas, nota en el §6 del spec y prueba manual del
   usuario en el Xiaomi.

Al recorrer el código salieron cuatro cosas que el spec no dice y que condicionan el plan:

- **`findQrBox` asume negro y la medición depende de él.** Busca el primer píxel casi negro de la imagen, y
  `RecipeQrLegibilityTest.measure` lo llama para anotar `qrTop`. Con la tarjeta de la marca no hay ningún píxel negro
  en el QR, así que `cardSweep` fallaría en el `check` antes de leer nada. Tiene que cambiar antes de medir (D52).
- **La barra de acento es del color del anillo.** `ShareFrame` pinta arriba, a todo el ancho, una barra de 4 dp en
  `AntTheme.colors.primary.base`, que es `#1677FF`. Buscar el anillo "desde arriba" la encontraría primero (D52).
- **El margen de los tests ya no es 8.** El §5 pide "el mismo margen de 8 niveles por canal que hoy", pero
  `QrTestSupport.PIXEL_TOLERANCE` vale 12 desde SPEC-010 (`ea1890b`), por una costura de un píxel que deja la captura
  (discrepancia 1, P3).
- **600 está en el límite.** La enmienda 1 fijó 600 con 20/20 en dos tandas de semillas (`10_000 + i` y `20_000 + i`),
  pero 625 ya fallaba 1 de 20 al reducir y recomprimir. Unas semillas nuevas podrían fallar también con el QR negro, y
  entonces la salida del §6 (cambiar el color del anillo) no arreglaría nada. F0 mide el negro con las mismas semillas
  que la marca para distinguirlo (D54, P2).

No hay detekt ni ktlint en el proyecto. Como en los planes anteriores, el control es `:app:lintDebug`.

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso en la enmienda |
|---|---|
| `share/QrCode.kt`: `QrMatrix` (`modules`, `cells`, `get`), `qrMatrix`, `qrVersion`, `QR_QUIET_ZONE` | La matriz no cambia. `QrMatrix` gana los ojos (D48). Sigue sin `import android` ni `import androidx`. |
| `share/ShareSpec.kt`: `SHARE_QR_MODULE` (2 dp), `qrBoxSize`, `SHARE_DENSITY` (3) | Módulo de 6 px y caja de `(módulos + 8) × 6` px, sin cambios. Gana los dos colores (D47). |
| `share/RecipeShareCard.kt`: `QrStrip`, `EvenCenterHorizontally` (D31), `QrCodeBox` | La franja y la posición de la caja no cambian. Solo cambia el dibujo dentro de `QrCodeBox` (D50). |
| `share/ShareFrame.kt` (barra de acento en `primary.base`) y `share/ShareCapture.kt` (`ShareCaptureHost`, siempre con `PourlineTheme(darkTheme = false)` y densidad 3) | Sin cambios. La barra de acento condiciona `findQrBox` (D52); el tema claro fijo hace que el PNG no dependa del tema del teléfono (R7). |
| `ui/theme/AntPalette.kt` (`generate`, `contrast`) y `ui/theme/AntColors.kt` (`AntSeedColors.primary`) | Solo en el test que ata los colores del QR a la paleta (D47). El tema no cambia. `AntPaletteTest` ya fija la paleta azul: el índice 5 es `#1677ff` y el 7 es `#003eb3`. |
| `recipe/share/RecipeShareCodec.kt` (`MAX_URI_LENGTH = 600`) | Sin cambios. |
| Tests: `QrTestSupport.kt` (`CardCapturer`, `worstCaseRecipe`, `uriOfLength`, `awaitQrModel`, `drawQr`, `drawQrModules`), `RecipeQrLegibilityTest` (`cardSweep` con `-e lengths`, `-e samples` y `-e seed`; `measure`; `maxUriLengthIsLegibleOnTheCard`; `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`), `ShareCardsLayoutTest` (`node`, `recipe`, `recipeData`, `worstCaseData`), `RecipeShareTest` (`save`, `show`, `tapShare`, `sharedDir`), `RecipeShareCodecTest` (URIs del E2E, D44) | La medición, los tests de la tarjeta y el E2E. `cardSweep` ya admite la semilla inicial con `-e seed`, así que la medición no necesita cambios en el test. |

### 1.2 Falta o hay que cambiar

- `QrCodeBox` (`RecipeShareCard.kt:191`) pinta todos los módulos oscuros con `Color.Black`, también los de los tres
  ojos, como cuadrados.
- No hay constantes de color del QR ni geometría de los ojos.
- `QrTestSupport.kt`: `findQrBox` toma el primer píxel `isDark()` (12 niveles o menos por canal) recorriendo filas, y
  `assertQrModules` exige que cada bloque de 6 × 6 sea todo negro o todo blanco según la matriz.
- **Tests que cambian de expectativa.** El §5 permite cambiar solo las expectativas que comprobaban el negro:

  | Test | Hoy | Con la enmienda | Fase |
  |---|---|---|---|
  | `QrTestSupport.findQrBox` (ayudante) | El primer píxel negro es la esquina del ojo de arriba a la izquierda | Busca el color del anillo por debajo de la barra de acento, en dos pasos (D52) | F2 |
  | `QrTestSupport.assertQrModules` (ayudante) | Cada celda, toda negra o toda blanca | Cada píxel, del color de su zona, sin las esquinas curvas de los ojos (D51) | F2 |
  | `ShareCardsLayoutTest.theQrIsBlackOnWhite` | El píxel (3, 3) del ojo de arriba a la izquierda es exactamente `Color.BLACK` | Se renombra `theQrIsBrandBlueOnWhite`. Las cuatro esquinas de la zona de silencio siguen blancas; el módulo de sincronismo (8, 6) está en `SHARE_QR_DARK`; los píxeles (21, 2) y (21, 21) del ojo, en `SHARE_QR_EYE_RING` y `SHARE_QR_DARK`. El píxel (3, 3) ya no vale: cae en una esquina curva | F2 |
  | `ShareCardsLayoutTest.everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6` | `findQrBox` y `assertQrModules` en negro | El cuerpo no cambia; cambian los dos ayudantes | F2 |
  | `RecipeShareTest.theSharedPngHas6x6QrModules` | Igual | El cuerpo no cambia; cambian los dos ayudantes | F2 |
  | `RecipeQrLegibilityTest.measure` (`cardSweep` y `maxUriLengthIsLegibleOnTheCard`) | `qrTop` con `findQrBox` | Sin cambios en el test: usa el `findQrBox` nuevo | F2 |

  Sin cambios, y tienen que seguir pasando: el resto de `ShareCardsLayoutTest`, `RecipesImportTest`,
  `RecipeIntentImportTest`, `QrLegibilityTest`, `RecipeQrLegibilityTest.theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`
  y los tests existentes de `QrCodeTest` y `ShareModelsTest` (D55).

### 1.3 Discrepancias entre el spec y el código, o dentro del spec

| # | Spec | Código real o problema | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §5: "con el mismo margen de 8 niveles por canal que hoy". El §6 (cierre de la enmienda 1) también dice 8. | `PIXEL_TOLERANCE = 12` desde SPEC-010 (`ea1890b`): la captura deja una costura de un píxel de hasta `#0A0A0A` y `#F5F5F5`. Con 8, los tests de hoy fallarían. | Se usa el margen de hoy, 12, que es la intención del criterio ("el mismo que hoy"). Al cerrar se propone corregir el número en el §5 y en el §6 (P3). |
| 2 | §3.3: los ojos son "dos piezas redondeadas encima de la matriz". | Si los módulos de los ojos se pintaran también, sus esquinas cuadradas asomarían bajo las piezas redondeadas. | Los 3 × 49 módulos de los ojos no se pintan como módulos; solo las piezas. Es lo que hace la maqueta aprobada (`isFinder`) (D50). |
| 3 | §5: "Solo cambian las expectativas que comprobaban el color negro". | `findQrBox` no es una expectativa, pero asume negro y `cardSweep` depende de él. | Cambia antes de medir, en F2 (D52). |
| 4 | §5 y §6: 20/20 a 600 "con 20 muestras y semillas nuevas"; si no, la salida es cambiar el color del anillo. | 600 está en el límite del negro (625 ya fallaba 1 de 20). Unas semillas nuevas podrían fallar también en negro, y la salida no lo arreglaría. | Referencia en negro con las mismas semillas en F0. Si el negro ya falla, se para antes de tocar código (D54, P2). |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas o delega al plan, y como "(amplía)" si
añaden algo que el spec no dice.

### 2.1 Colores y geometría

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D47 | Colores (concreta §3.3) | En `share/ShareSpec.kt`: `val SHARE_QR_DARK = Color(0xFF003EB3)` (módulos de datos y centro de los ojos) y `val SHARE_QR_EYE_RING = Color(0xFF1677FF)` (anillo de los ojos). El KDoc cita el tono de la paleta, el contraste sobre blanco (9,0:1 y 4,1:1) y la salida del §6. No se leen de `AntTheme`, y `AntColors` y `AntColorRole` no cambian. `ShareModelsTest` los ata a la paleta generada desde `AntSeedColors().primary`: `SHARE_QR_DARK` es el tono 8 (índice 7 de `AntPalette.generate`) y `SHARE_QR_EYE_RING` el tono 6 (índice 5, `primary`). | El spec pide constantes de la tarjeta que no dependan del tema. La legibilidad se mide con estos dos valores exactos. Si salieran de la paleta, un cambio de `primary` cambiaría el QR sin volver a medir; con el test, ese cambio falla y obliga a revisar. `AntColorRole` no expone el tono 8: en el tema claro coincide con `primary.solidActive`, pero por el cálculo del contraste del botón pulsado, no por su significado, y añadir un rol cambiaría la API del tema para un solo uso. Van en `ShareSpec.kt`, junto a `SHARE_QR_MODULE`, y no en `QrCode.kt`, porque son `Color` de Compose y `QrCode.kt` sigue sin dependencias de Android. |
| D48 | Geometría de los ojos (concreta §3.3) | En `share/QrCode.kt`, sin dependencias de Android: `const val QR_EYE_SIZE = 7`. En `QrMatrix`: `val eyes: List<Pair<Int, Int>>` (la esquina de cada ojo en módulos, sin zona de silencio: `0 to 0`, `modules − 7 to 0` y `0 to modules − 7`) y `fun isEye(x: Int, y: Int): Boolean`. Nuevos `data class QrEyeRadii(val outer: Int, val hole: Int, val center: Int)` y `fun qrEyeRadii(modulePx: Int): QrEyeRadii`, que redondea al píxel los radios de referencia de la maqueta (2,1, 1,3 y 0,9 módulos, en constantes privadas). | Una sola fuente para el dibujo y para los tests JVM e instrumentados. En `QrCode.kt` se prueba en la JVM. |
| D49 | Radios en píxeles (concreta §3.3) | Con módulos de 6 px: **13 px** en el borde exterior del anillo (12,6), **8 px** en el hueco (7,8) y **5 px** en el centro (5,4), el entero más cercano. Piezas, desde la esquina del ojo: anillo de 42 × 42 px con un hueco de 30 × 30 px desplazado 6 px, y centro de 18 × 18 px desplazado 12 px. El anillo nunca es más fino que un módulo: 6 px en los tramos rectos y 6,4 px en la diagonal, porque `outer − hole ≤ modulePx` (5 ≤ 6). | Es lo que delega el spec ("el plan los fija en píxeles enteros"). Los tramos rectos de las tres piezas empiezan y acaban en múltiplos de 6 px desde la caja, que está en una x par (D31): siguen cayendo en píxeles enteros, y solo las curvas mezclan color. El valor de los radios no afecta a la lectura: los lectores localizan los ojos por la proporción 1:1:3:1:1 en las líneas que pasan por su centro, que son tramos rectos. |
| D50 | Dibujo en `QrCodeBox` (concreta §3.3; discrepancia 2) | Misma firma, `QrCodeBox(matrix: QrMatrix, modifier: Modifier = Modifier)`, y misma caja. En el `Canvas`: (1) fondo blanco; (2) los módulos oscuros que no son de un ojo (`!matrix.isEye(x, y)`), como hoy pero en `SHARE_QR_DARK`; (3) para cada ojo de `matrix.eyes`, con origen en `((ex + QR_QUIET_ZONE) × m, (ey + QR_QUIET_ZONE) × m)`: el anillo como un `Path` con `fillType = PathFillType.EvenOdd` y dos `addRoundRect` (un `RoundRect` de 7m con `CornerRadius(outer)` y otro de 5m desplazado m con `CornerRadius(hole)`), pintado con `drawPath(path, SHARE_QR_EYE_RING)`, y el centro con `drawRoundRect(SHARE_QR_DARK, topLeft = origen + 2m, size = 3m × 3m, cornerRadius = CornerRadius(center))`. Antialias por defecto de `DrawScope`: en bordes de píxel entero no mezcla, así que solo se nota en las curvas. KDoc de `QrCodeBox` y de `RecipeShareCard` al día. | Es el dibujo de la maqueta aprobada, que salta los módulos de los ojos y pinta el anillo con `evenodd`. Un solo relleno con `EvenOdd` evita pintar el hueco de blanco encima del anillo, que mezclaría dos veces el antialias de sus curvas. |

### 2.2 Tests y medición

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D51 | Comprobación de píxeles (concreta §5; discrepancia 1) | `assertQrModules(bitmap, matrix, left, top, modulePx)` conserva la firma y compara cada píxel de la caja con el color de su zona, con `PIXEL_TOLERANCE` = 12 por canal. Zonas: zona de silencio, módulos claros, separadores y hueco de los ojos, blanco; módulos de datos oscuros y centro de los ojos, `SHARE_QR_DARK`; anillo, `SHARE_QR_EYE_RING`. En cada ojo se saltan los cuadrados de las esquinas curvas, con `qrEyeRadii(modulePx)`: los 4 de lado `outer` (13 px) en las esquinas del anillo, los 4 de lado `hole` (8 px) en las del hueco y los 4 de lado `center` (5 px) en las del centro. Fuera de esos cuadrados todo cae en píxeles enteros. `isDark` e `isLight` se sustituyen por `Int.isNear(argb: Int)`. El mensaje de fallo da la celda, el píxel, la zona y el color esperado. El KDoc de `PIXEL_TOLERANCE` explica el margen con los colores: mezclar hasta un 4 % de blanco mueve los dos azules unos 10 niveles por canal, dentro del margen. | Es el criterio del §5: mismo margen que hoy, color esperado por zona y esquinas curvas fuera. Sirve igual para el bitmap capturado y para el PNG de `FileProvider`. |
| D52 | Localizar la caja (amplía; discrepancia 3) | `findQrBox(bitmap, modulePx)` en dos pasos. (1) Recorre las filas desde arriba y salta las que tienen en x = 0 el color del anillo (la barra de acento de `ShareFrame` ocupa todo el ancho; el QR nunca toca x = 0). La primera fila restante con un píxel `isNear(SHARE_QR_EYE_RING)` es el borde superior del ojo de arriba a la izquierda, `y0`. (2) En la fila `y0 + 3 × modulePx + modulePx / 2`, la mitad del ojo, donde su borde izquierdo es recto, el primer píxel de ese color es el borde izquierdo, `x0`. La caja empieza en `(x0 − 4 × modulePx, y0 − 4 × modulePx)`. Con la salida aplicada (anillo en `#003EB3`) funciona igual: en esas dos filas, el ojo es lo primero que aparece. | Con la regla de hoy, el `check` de `findQrBox` corta la medición. Con las esquinas redondeadas, el primer píxel del color del anillo no es la esquina del ojo: de ahí el segundo paso. `everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6` compara el resultado con la posición del nodo `share_recipe_qr`. |
| D53 | Ojos redondeados (amplía) | `ShareCardsLayoutTest.theEyesAreRoundedWithTheBrandColors`, con la receta corta y con la de peor caso, en los tres ojos. En píxeles desde la esquina de cada ojo: (0, 0), (41, 0), (0, 41) y (41, 41) son blancos (esquinas del anillo redondeadas); (6, 6) y (35, 35) son del color del anillo (esquinas del hueco redondeadas); (12, 12) es blanco (esquina del centro redondeada); (21, 2) es del anillo, (21, 9) blanco y (21, 21) `SHARE_QR_DARK`. Ninguno queda a menos de 1 px de una curva. | `assertQrModules` salta las esquinas curvas, así que un ojo cuadrado también pasaría. Estas sondas prueban el redondeo con píxeles que no dependen del antialias. |
| D54 | Medición y salida (concreta §5 y §6; discrepancia 4) | **Referencia (F0, sin tocar código):** `RecipeQrLegibilityTest#cardSweep` sobre la tarjeta de hoy (QR negro) con las mismas longitudes y semillas que la medición. **Medición (F2):** `cardSweep` con `-e lengths 600 -e samples 20 -e seed 30000` (semillas `30_000 + i`, nuevas: la enmienda 1 usó `10_000 + i` en `cardSweep`, `20_000 + i` en `maxUriLengthIsLegibleOnTheCard` y `100 + i` en `QrLegibilityTest`) y una pasada gruesa con `-e lengths 300,350,400,450,500,550 -e samples 10 -e seed 30000`, con las tres lecturas de siempre: tal cual, 50 % + JPEG 70 y reducida a 2048 px. Además tienen que pasar `maxUriLengthIsLegibleOnTheCard` y `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`, que leen la misma tarjeta a 600. **Regla:** la marca vale si 600 da 20/20 en las tres lecturas, la pasada gruesa acierta todas y los dos tests pasan. **Salida (la del §6):** si algo de eso falla, `SHARE_QR_EYE_RING` pasa a `Color(0xFF003EB3)`, la expectativa de `ShareModelsTest` pasa al tono 8 y se repite todo con las mismas semillas. **Parada:** si con la salida sigue fallando, se para y decide el usuario, con las tablas de la referencia, de la marca y de la salida, y los PNG que fallan. `MAX_URI_LENGTH` no se toca. | El §5 pide 20/20 a 600 con 20 muestras y semillas nuevas. Con la referencia en negro y las mismas semillas, un fallo se atribuye a los colores o a las muestras (P2). La pasada gruesa cubre la receta 4:6 del usuario (450 caracteres) y repite la regla de la enmienda 1 (P1). Los dos tests de regresión miden lo mismo con otras muestras: si fallan, la medición tampoco vale. |
| D55 | Lo que sigue en negro (concreta §5) | No cambian: `drawQr`, `drawQrModules` y `awaitQrModel` de `QrTestSupport.kt`; `QrLegibilityTest` (el lienzo de 1080 × 1350); los QR sintéticos de `RecipesImportTest.withQr` y de `RecipeIntentImportTest.aSendIntentWithAForeignQrShowsNoRecipeFound`; y el raster de `QrCodeTest.binary`. | No dibujan la tarjeta: generan imágenes de entrada (un QR ajeno, una imagen enorme) o miden el lienzo genérico. Un QR negro sigue siendo una entrada válida, la que llega de otras apps. La tarjeta de la marca la cubren los tests que la capturan con `ShareCaptureHost`: `RecipesImportTest.recipeCardUri` y `RecipeIntentImportTest.cardPng` ya la usan y leerán la tarjeta nueva sin cambios. |
| D56 | Lectura con zxing en la JVM (amplía) | `QrCodeTest.a raster in the brand colors decodes with zxing`: pinta en un `IntArray` el QR de un URI de 600 caracteres con módulos de 6 px, como `QrCodeBox`: datos y centro en `SHARE_QR_DARK.toArgb()`, anillo en `SHARE_QR_EYE_RING.toArgb()`, y las tres piezas redondeadas decididas por el centro de cada píxel con `qrEyeRadii(6)`. Lo lee con `QRCodeReader` sobre `HybridBinarizer`. Una vez con el anillo de la constante y otra con `#003EB3`. | Un aviso temprano, antes del emulador, de que la geometría y los colores los lee un lector estándar distinto de ML Kit. No sustituye a la medición de D54. |
| D57 | E2E, capturas y cierre (concreta §6) | E2E por adb y capturas como en la enmienda 1, solo en `emulator-5560` (§7.4 y §7.6). Al cerrar, F3 escribe en el §6 del spec, en la nota "Enmienda 2, pendiente", la tabla de la medición (referencia, marca y, si la hubo, salida) y si se aplicó la salida. La fila Estado del spec solo cambia si el usuario lo pide. | Es lo que pide el propio §6. |

## 3. Fases

Orden: F0 → F1 → F2 → F3. F2 depende de F1 (colores y geometría). F3 depende de que F2 haya cerrado la medición en
20/20, con o sin la salida.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base y referencia en negro con las semillas nuevas | S | Hecha |
| F1 | Colores y geometría de los ojos, con sus tests JVM | S | Hecha |
| F2 | La marca en `QrCodeBox`, medición con la salida del §6 y tests de la tarjeta | L | Hecha |
| F3 | Verificación: suites afectadas, comprobaciones, E2E por adb, capturas y cierre | M | Hecha (falta la prueba manual en el Xiaomi) |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con `./gradlew build`
ni con builds limpios.

- **Compilar:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- **Tests JVM:** solo las clases de la enmienda, con `./gradlew :app:testDebugUnitTest --tests "<patrón>"`. Nunca la
  batería completa.
- **Tests instrumentados:** solo en `emulator-5560` (AVD `SG_API36_play`, con Play Store: el único que descarga el
  modelo de ML Kit de Play Services), una suite cada vez. Si no está arrancado, se arranca **en segundo plano** con
  `/Users/josue/Library/Android/sdk/emulator/emulator -avd SG_API36_play -port 5560` (en primer plano bloquea la shell),
  y se espera con `adb -s emulator-5560 wait-for-device` hasta que `adb -s emulator-5560 shell getprop sys.boot_completed`
  dé `1`. `emulator-5554` no se toca. El Xiaomi del usuario (adb inalámbrico) no se toca: allí los tests que abren
  actividades se cuelgan, y la prueba en él es manual (§7.5).
- **Las seis clases afectadas** (§5 de la enmienda):

  ```
  ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.ShareCardsLayoutTest,dev.josueamaya.pourline.share.QrLegibilityTest,dev.josueamaya.pourline.share.RecipeQrLegibilityTest,dev.josueamaya.pourline.ui.recipes.RecipeShareTest,dev.josueamaya.pourline.ui.recipes.RecipesImportTest,dev.josueamaya.pourline.RecipeIntentImportTest
  ```

  `cardSweep` se salta sin `-e lengths`.
- **Mediciones con argumentos:** `connectedDebugAndroidTest` desinstala la app al terminar, así que la medición instala
  primero con `ANDROID_SERIAL=emulator-5560 ./gradlew :app:installDebug :app:installDebugAndroidTest` y lanza cada tanda
  con `am instrument`, después de vaciar el logcat y la carpeta de fallos:

  ```
  adb -s emulator-5560 logcat -c
  adb -s emulator-5560 shell rm -rf /sdcard/Android/data/dev.josueamaya.pourline/files/qr-legibility-card
  adb -s emulator-5560 shell am instrument -w -e class dev.josueamaya.pourline.share.RecipeQrLegibilityTest#cardSweep -e lengths 600 -e samples 20 -e seed 30000 dev.josueamaya.pourline.test/dev.josueamaya.pourline.PourlineTestRunner
  adb -s emulator-5560 logcat -d -s QrLegibility
  adb -s emulator-5560 pull /sdcard/Android/data/dev.josueamaya.pourline/files/qr-legibility-card <carpeta del scratchpad>
  ```

  La pasada gruesa es la misma orden con `-e lengths 300,350,400,450,500,550 -e samples 10 -e seed 30000`. Los dos tests
  de regresión de D54 van con `-e class dev.josueamaya.pourline.share.RecipeQrLegibilityTest#maxUriLengthIsLegibleOnTheCard,dev.josueamaya.pourline.share.RecipeQrLegibilityTest#theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`.
- **Sin ARTEMIS.** Tests JVM, tests instrumentados y adb.
- **Fallos previos que no bloquean:** los que F0 encuentre en las seis clases antes de tocar código, si siguen siendo
  los mismos.
- **Lint:** `./gradlew :app:lintDebug` en F0, F2 y F3, sin errores ni avisos nuevos respecto a la línea base.
- **Commits:** uno por fase (F0 no cambia código: sus resultados van en el commit de F1), en Conventional Commits y en
  inglés, sin `Co-Authored-By` ni atribución de herramientas, nunca en `main` ni `develop`. Antes de F0, este plan,
  cuando se apruebe: `docs(specs): add the implementation plan for SPEC-009 amendment 2 (branded QR)`.

### F0. Línea base y referencia en negro

**Objetivo:** las cifras de partida de las clases afectadas y la legibilidad del QR negro de hoy con las semillas de la
medición de F2. Sin tocar código.

**Pasos:**

1. `adb devices -l`. Arrancar `emulator-5560` si falta. Si aparece un dispositivo distinto de `emulator-5554`,
   `emulator-5560` y el Xiaomi, se pregunta antes de seguir.
2. JVM: `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.*" --tests "dev.josueamaya.pourline.ui.theme.*" --tests "dev.josueamaya.pourline.recipe.share.*"`.
   Lint: `./gradlew :app:lintDebug`.
3. Las seis clases en `emulator-5560`.
4. **Referencia (D54):** instalar y lanzar `cardSweep` a 600 (20 muestras, semilla 30000) y la pasada gruesa (300 a
   550, 10 muestras, semilla 30000). Anotar las líneas `card <longitud> v<versión> asIs n/N reduced n/N import n/N` y
   bajar los PNG que fallen.
5. **Parada (P2):** si el negro no da 20/20 en las tres lecturas a 600, o falla alguna muestra de la pasada gruesa, se
   para aquí, sin tocar código, y se consulta al usuario con las tablas.

**Archivos:** este plan (cabecera y "Resultado de F0").

**Tests:** ninguno nuevo.

**Verificación:** los pasos 2 a 4.

**Criterio de salida:** las cifras de la línea base en la cabecera, y la referencia en negro con 20/20 a 600 y la pasada
gruesa sin fallos.

Sin commit propio.

#### Decisión tras F0 (usuario, 2026-11-20)

El negro ya falla a 450 en la lectura 50 % + JPEG 70, así que la regla de D54 ("la pasada gruesa acierta todas") y la
propuesta de P1 se sustituyen por "no peor que el negro". La regla vigente de la medición de F2 es:

- 600 caracteres, 20 muestras, semillas 30000: 20/20 en las tres lecturas (sin cambios).
- Pasada gruesa de 300 a 550 (10 muestras, semillas 30000) y 450 con 20 muestras (semillas 30000 y 40000): en cada
  longitud y cada lectura, la marca acierta al menos lo mismo que la referencia en negro de F0 con las mismas semillas.
- Los dos tests de regresión a 600 pasan.
- Si la marca acierta menos que el negro en algún punto, se aplica la salida (anillo en `#003EB3`) y se repite con las
  mismas semillas. Si con la salida sigue por debajo del negro, se para y se devuelve el control con las tablas.
- Al cerrar, el §6 del spec anota que el QR negro ya falla a 450 en esa lectura (18/20 con dos lotes de semillas) como
  hallazgo preexistente, fuera de esta enmienda.

También por decisión del usuario, los 2 errores `StringFormatInvalid` de la línea base de lint
(`four_six_split_taste` y `four_six_split_strength`) se corrigen en esta rama en un commit independiente
(`formatted="false"`, como `tour_four_six_sliders_body`; el texto mostrado no cambia). Con eso, la línea base de lint
pasa a 0 errores y 20 avisos.

#### Resultado de F0

F0 se interrumpió antes de tocar código porque el QR negro de hoy no da 20/20 en la pasada gruesa con las semillas
nuevas (P2). El usuario decidió seguir con la regla "no peor que el negro" (ver "Decisión tras F0"). F0 no cambia
código.

Línea base (2026-11-20, rama `feat/share-recipe-qr-style`, sin cambios de código):

- JVM (`share.*`, `ui.theme.*`, `recipe.share.*`): 106 tests, 11 clases, 0 fallos.
- `emulator-5560`, las seis clases: 68 tests, 0 fallos, 1 omitido (`cardSweep`, sin `-e lengths`).
- `:app:lintDebug`: falla con 2 errores previos y 20 avisos, ninguno de esta enmienda: `StringFormatInvalid` en
  `values/strings.xml:972` (`four_six_split_taste`) y `values-en/strings.xml:974` (`four_six_split_strength`), por
  `'% s'`; el resto son avisos de versiones y de `ExifInterface`.

Referencia en negro (tarjeta de hoy, receta de peor caso, semillas `30_000 + i`):

| Longitud | Muestras | Versión | Tal cual | 50 % + JPEG 70 | Importación (2048 px) |
|---|---|---|---|---|---|
| 600 | 20 | v19 | 20/20 | 20/20 | 20/20 |
| 300 | 10 | v13 | 10/10 | 10/10 | 10/10 |
| 350 | 10 | v14 | 10/10 | 10/10 | 10/10 |
| 400 | 10 | v15 | 10/10 | 10/10 | 10/10 |
| **450** | 10 | v16 | 10/10 | **9/10** | 10/10 |
| 500 | 10 | v17 | 10/10 | 10/10 | 10/10 |
| 550 | 10 | v18 | 10/10 | 10/10 | 10/10 |
| 450 (extra) | 20, semillas 30000 | v16 | 20/20 | **18/20** | 20/20 |
| 450 (extra) | 20, semillas 40000 | v16 | 20/20 | **18/20** | 20/20 |

`maxUriLengthIsLegibleOnTheCard` (600): 20/20 en las tres. `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`: verde.

Los fallos son solo de la lectura tras reducir al 50 % y JPEG 70 (la más dura); la importación, que es la ruta real,
acierta siempre. Fallan las semillas 30003 y 30015 de 450 (PNG guardados en el scratchpad del agente). Con otras
semillas (40000) falla igual 2 de 20: no es un caso aislado de las semillas nuevas, el negro ya falla a 450 en esa
lectura. La enmienda 1 midió 450 con otras semillas y 10 muestras sin fallos.

Consecuencia: la regla de D54 ("la pasada gruesa acierta todas") no la cumple ni el negro; se sustituye por la de
"Decisión tras F0".

### F1. Colores y geometría de los ojos

**Objetivo:** las constantes de color (D47) y la geometría de los ojos (D48, D49), en código que se prueba en la JVM. La
tarjeta todavía no cambia.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `share/ShareSpec.kt` | `SHARE_QR_DARK` y `SHARE_QR_EYE_RING`, con su KDoc (D47). |
| `share/QrCode.kt` | `QR_EYE_SIZE`; `QrMatrix.eyes` y `QrMatrix.isEye`; `QrEyeRadii` y `qrEyeRadii` (D48, D49). Sin `import android` ni `import androidx`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../share/QrCodeTest.kt` | `` `the three eyes are the finder patterns of the matrix` ``: con URIs de 20, 150, 331 y 600 caracteres, en cada ojo de `eyes` el borde de 7 × 7 y el centro de 3 × 3 son oscuros, el hueco es claro, y la fila y la columna de separación son claras. Así, pintar los ojos como piezas fijas reproduce la matriz. `` `only the three 7 by 7 corners are eye modules` ``: `isEye` marca 147 módulos, los de `eyes`, en esas mismas matrices. `` `the eye radii are 13, 8 and 5 px with 6 px modules` ``: los valores de D49, y para módulos de 4, 6 y 8 px, `outer − hole ≤ modulePx` y cada radio cabe en la mitad de su pieza. `` `a raster in the brand colors decodes with zxing` `` (D56). Los cinco casos existentes no cambian. |
| `test/.../share/ShareModelsTest.kt` | `` `the qr colors are tones of the primary palette` ``: `SHARE_QR_DARK.toArgb()` es el índice 7 y `SHARE_QR_EYE_RING.toArgb()` el índice 5 de `AntPalette.generate(AntSeedColors().primary.toArgb())`, y su contraste con blanco (`AntPalette.contrast`), redondeado a un decimal, es 9,0 y 4,1, los del §3.3. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.*" --tests "dev.josueamaya.pourline.ui.theme.*"`.
La compilación de los tres conjuntos. `grep -n "import android" app/src/main/kotlin/dev/josueamaya/pourline/share/QrCode.kt`
no encuentra nada.

**Criterio de salida:** todo en verde y `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/share/RecipeShareCard.kt`
vacío. Si zxing no lee el raster de la marca (D56), se para y se consulta antes de F2.

Commit sugerido: `feat(share): define the brand colors and rounded eye geometry of the recipe QR`.

#### Resultado de F1

Hecha (2026-11-20).

- `ShareSpec.kt`: `SHARE_QR_DARK` (`#003EB3`) y `SHARE_QR_EYE_RING` (`#1677FF`). `QrCode.kt`: `QR_EYE_SIZE`,
  `QrMatrix.eyes` y `isEye`, `QrEyeRadii` y `qrEyeRadii`; sin `import android` (`grep` vacío).
- Tests nuevos: 4 en `QrCodeTest` y 1 en `ShareModelsTest`. En `QrCodeTest`, las matrices de los ojos usan URIs de 40,
  150, 331 y 600 caracteres: con 20 no cabe ni el prefijo de 24 caracteres.
- JVM (`share.*`, `ui.theme.*` y `recipe.share.*`): 111 tests, 0 fallos (106 de la línea base más los 5 nuevos). La
  tarjeta no cambia: `RecipeShareCard.kt` sin diff.
- El raster en los colores de la marca, con módulos de 6 px y los ojos redondeados, lo lee zxing con el anillo en
  `#1677FF` y en `#003EB3` (D56).

### F2. La marca en la tarjeta, medición y tests

**Objetivo:** pintar el QR con la marca, medir su legibilidad sobre la tarjeta real (D54) y, con la medición en 20/20,
dejar en verde los tests de la tarjeta.

**Pasos:**

1. **Dibujo (D50):** `QrCodeBox` y los KDoc de `QrCodeBox` y `RecipeShareCard`.
2. **`findQrBox` (D52)** e `Int.isNear`, en `QrTestSupport.kt`. Es lo único de los tests que la medición necesita.
3. **Instalar:** `ANDROID_SERIAL=emulator-5560 ./gradlew :app:installDebug :app:installDebugAndroidTest`.
4. **Medición (D54):** `cardSweep` a 600 (20 muestras, semilla 30000), la pasada gruesa (300 a 550, 10 muestras,
   semilla 30000) y los dos tests de regresión. Se anotan las tres lecturas por longitud y se bajan los PNG que fallen.
5. **Salida:** si falla algo de la regla de D54, `SHARE_QR_EYE_RING` pasa a `Color(0xFF003EB3)` (y su KDoc lo dice), la
   expectativa de `ShareModelsTest` pasa al índice 7, y se repiten los pasos 3 y 4 con las mismas semillas.
6. **Parada:** si con la salida sigue fallando, se para y se consulta al usuario con las tres tablas y los PNG.
7. **Tests de la tarjeta:** `assertQrModules` (D51), `theQrIsBlackOnWhite` → `theQrIsBrandBlueOnWhite` (§1.2) y
   `theEyesAreRoundedWithTheBrandColors` (D53).
8. **Suites:** los tests JVM de F1, las seis clases en `emulator-5560` y lint.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `share/RecipeShareCard.kt` | `QrCodeBox` con la marca (D50); KDoc de `QrCodeBox` y de `RecipeShareCard`. |
| `share/ShareSpec.kt` | Solo si se aplica la salida: `SHARE_QR_EYE_RING` en `#003EB3`. |
| `docs/specs/plan-qr-marca.md` | Cabecera ("Colores" y "Resultado de la medición") y "Resultado de F2". |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../share/QrTestSupport.kt` | `findQrBox` (D52), `assertQrModules` (D51), `Int.isNear` en lugar de `isDark` e `isLight`, y el KDoc de `PIXEL_TOLERANCE` (sigue en 12). `drawQr`, `drawQrModules`, `awaitQrModel`, `CardCapturer`, `worstCaseRecipe` y `uriOfLength`, sin cambios (D55). |
| `androidTest/.../share/ShareCardsLayoutTest.kt` | `theQrIsBrandBlueOnWhite` (antes `theQrIsBlackOnWhite`, §1.2). Nuevo `theEyesAreRoundedWithTheBrandColors` (D53). `everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6`, con el cuerpo sin cambios: con los ayudantes nuevos comprueba los colores en el bitmap capturado de la receta corta y de la de peor caso. El resto, sin cambios. |
| `androidTest/.../ui/recipes/RecipeShareTest.kt` | Sin cambios en el código. `theSharedPngHas6x6QrModules` comprueba ahora los colores en el PNG que entrega `FileProvider` (criterio del §5 "en el PNG generado"). |
| `androidTest/.../share/RecipeQrLegibilityTest.kt` | Sin cambios en el código: mide (paso 4). |
| `test/.../share/ShareModelsTest.kt` | Solo si se aplica la salida: el anillo es el índice 7 y su contraste, 9,0. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.*" --tests "dev.josueamaya.pourline.ui.theme.*"`.
Las seis clases en `emulator-5560`. `./gradlew :app:lintDebug`.
`git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/share/BrewShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CoffeeShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/CompareShareCard.kt app/src/main/kotlin/dev/josueamaya/pourline/share/ShareFrame.kt app/src/main/kotlin/dev/josueamaya/pourline/share/ShareCapture.kt`
vacío.

**Criterio de salida:** la medición con 20/20 a 600 en las tres lecturas, la pasada gruesa sin fallos y los dos tests de
regresión en verde, con el color del anillo final anotado en la cabecera; las seis clases en verde (salvo los fallos
previos de F0); lint como la línea base.

Commit sugerido: `feat(share): draw the recipe QR in the brand colors with rounded eyes`.

#### Resultado de F2

Hecha (2026-11-20). Con la regla "no peor que el negro" (ver "Decisión tras F0"), la marca vale con el anillo en
`#1677FF`: no hizo falta aplicar la salida del §6.

Medición en `emulator-5560` (lector de Play Services), sobre la tarjeta real, receta de peor caso, semillas `30_000 + i`
(y `40_000 + i` en el segundo lote de 450). Cada celda es "tal cual / 50 % + JPEG 70 / importación a 2048 px".

| Longitud | Muestras | Semillas | Negro (F0) | Marca (anillo `#1677FF`) | Salida |
|---|---|---|---|---|---|
| 600 (v19) | 20 | 30000 | 20/20, 20/20, 20/20 | 20/20, 20/20, 20/20 | no aplicada |
| 300 (v13) | 10 | 30000 | 10/10, 10/10, 10/10 | 10/10, 10/10, 10/10 | no aplicada |
| 350 (v14) | 10 | 30000 | 10/10, 10/10, 10/10 | 10/10, 10/10, 10/10 | no aplicada |
| 400 (v15) | 10 | 30000 | 10/10, 10/10, 10/10 | 10/10, 10/10, 10/10 | no aplicada |
| 450 (v16) | 10 | 30000 | 10/10, **9/10**, 10/10 | 10/10, 10/10, 10/10 | no aplicada |
| 500 (v17) | 10 | 30000 | 10/10, 10/10, 10/10 | 10/10, 10/10, 10/10 | no aplicada |
| 550 (v18) | 10 | 30000 | 10/10, 10/10, 10/10 | 10/10, 10/10, 10/10 | no aplicada |
| 450 (v16) | 20 | 30000 | 20/20, **18/20**, 20/20 | 20/20, 20/20, 20/20 | no aplicada |
| 450 (v16) | 20 | 40000 | 20/20, **18/20**, 20/20 | 20/20, 20/20, 20/20 | no aplicada |

- La marca iguala o supera al negro en todas las longitudes y lecturas. `qrTop` y el alto de la tarjeta coinciden con los
  del negro (1518 px y 2328 px a 600), así que `findQrBox` localiza bien la caja.
- `maxUriLengthIsLegibleOnTheCard` (600): 20/20 en las tres lecturas. `theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70`: verde.
- Las seis clases en `emulator-5560`: 69 tests, 0 fallos, 1 omitido (`cardSweep`). Son los 68 de la línea base más
  `theEyesAreRoundedWithTheBrandColors`; `theQrIsBlackOnWhite` pasa a `theQrIsBrandBlueOnWhite`.
- Lint: 0 errores y 20 avisos (los mismos de la línea base, sin los 2 errores corregidos aparte).
- Tests de la tarjeta: `assertQrModules` compara cada píxel con el color de su zona (margen de 12 por canal, esquinas
  curvas fuera) y `findQrBox` busca el anillo en dos pasos (D51, D52). El margen es el de hoy, 12 (P3).

### F3. Verificación y cierre

**Objetivo:** el §7 completo, la nota del §6 del spec y la prueba manual del usuario.

**Archivos:** este plan (cabecera y "Resultado de F3") y, como pide el §6 del spec, la nota "Enmienda 2, pendiente":
la tabla de la medición y si se aplicó la salida (D57). La fila Estado del spec solo cambia si el usuario lo pide.

**Verificación:** el §7.

Commit sugerido: `docs(specs): record the SPEC-009 amendment 2 verification`.

#### Resultado de F3

Hecha (2026-11-20), salvo la prueba manual del usuario en el Xiaomi.

- **Suites (§7.1):** JVM, 111 tests, 0 fallos. `emulator-5560`, las seis clases, 69 tests, 0 fallos, 1 omitido
  (`cardSweep`). Lint: 0 errores y 20 avisos (los mismos que en la línea base; los 2 errores se corrigieron en un
  commit aparte, `formatted="false"`). Los totales suben en 4 (`QrCodeTest`), 1 (`ShareModelsTest`) y 1
  (`ShareCardsLayoutTest`) respecto a la línea base.
- **Comprobaciones (§7.2):** sin `import android` en `recipe/share` ni en `QrCode.kt`; sin `Color.Black` en `share/`;
  `MAX_URI_LENGTH = 600`; `git diff 55359f8` vacío en `recipe/`, `grinder/`, `ui/`, el manifiesto, `app/build.gradle.kts`
  y `gradle/`, y en `BrewShareCard`, `CoffeeShareCard`, `CompareShareCard`, `ShareFrame` y `ShareCapture`. Desviación: el
  diff de `app/src/main/res` no está vacío, por los dos `formatted="false"` de `four_six_split_*` (decisión del
  usuario; el texto mostrado no cambia).
- **E2E por adb (§7.4), `emulator-5560`, tras `pm clear`:** Onboarding (Básico, sin báscula, Femobook A2), importación
  de los dos URIs de D44 (4:6 con la molienda "Femobook A2 · 0 · 34"; peor caso) y Guardar. Compartir "V60 clásica", la
  4:6 y la de peor caso, sin diálogos: 1080 × 1350, 1080 × 1656 y 1080 × 2226, los mismos tamaños que la enmienda 1.
  El PNG compartido es idéntico (mismo SHA-256) con el teléfono en tema claro y en oscuro. Los PNG a tamaño completo y
  reducidos a 540 px con JPEG 70 (4:6 y peor caso) se importan por "Desde una imagen" y abren la vista previa. Sin
  entradas en `logcat -b crash`. Se vio a ojo la tarjeta 4:6: ojos redondeados con el anillo en azul primario, datos y
  centros en azul oscuro sobre blanco, QR centrado bajo los pasos.
- **Capturas (§7.6):** `~/Downloads/pourline-recipe-share/qr-style/{es,en}/` con `recipe-short.png`,
  `recipe-four-six.png` y `recipe-worst-case.png` (6 archivos, claro).
- **Spec:** actualizado el §3.3 (radios 13, 8 y 5 px; los módulos de los ojos no se pintan como módulos), el §5 y la
  nota de la enmienda 1 del §6 (12 niveles, no 8), el resultado de la medición y el hallazgo del negro a 450 en el §6, y
  la cabecera (Enmienda 2 implementada).

**Prueba manual del usuario en el Xiaomi (§7.5), nada instalado por el agente:**

- APK de esta rama con la misma firma que el que tiene instalado. Un APK de debug sobre el release firmado con su clave
  no se instala encima: obliga a desinstalar y borra los datos. Antes, exportar la copia de seguridad de SPEC-011 (R8).
- Compartir la receta 4:6 por WhatsApp y reimportarla desde la imagen recibida, por la galería ("Desde una imagen") y
  por "Escanear QR" con la imagen en otra pantalla: las dos abren la vista previa sin aviso de recorte.
- A ojo: ojos redondeados y QR en los azules de la marca, también con el teléfono en tema oscuro. Opcional: la cámara
  del sistema o Lens, anotando si ofrece abrir el enlace.

### 3.1 Cadenas nuevas

Ninguna. La enmienda no cambia textos: `git diff develop -- app/src/main/res` queda vacío (§7.2).

## 4. Trazabilidad con el §5 del spec

Los criterios de la enmienda 2 se cubren con tests nuevos o adaptados. Los demás no los toca esta enmienda: se cubren
por regresión con las clases que ya los prueban y que pasan por la tarjeta o el QR, o, si su código no cambia, con el
`git diff` vacío del §7.2.

| Criterio | Fase | Prueba |
|---|---|---|
| **Enmienda 2:** en el PNG, datos en `#003EB3`, anillo en `#1677FF` (o `#003EB3` con la salida) y centro en `#003EB3`, fondo blanco; módulos de datos de 6 × 6 y caja de `(módulos + 8) × 6`; margen de hoy por canal y esquinas curvas fuera | F1, F2 | `ShareModelsTest.the qr colors are tones of the primary palette`; `ShareCardsLayoutTest.everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6`, `theQrIsBrandBlueOnWhite` y `theEyesAreRoundedWithTheBrandColors` (bitmap capturado); `RecipeShareTest.theSharedPngHas6x6QrModules` (PNG de `FileProvider`); `assertQrModules` (D51) |
| **Enmienda 2:** `cardSweep` da 20/20 a 600 en las tres lecturas, 20 muestras y semillas nuevas, anotado en el §6 | F0 (referencia), F2, F3 | D54: "Resultado de F0" y "Resultado de F2"; nota del §6 del spec (D57) |
| **Enmienda 2:** pasan `ShareCardsLayoutTest`, `RecipeShareTest`, `RecipesImportTest`, `RecipeIntentImportTest`, `RecipeQrLegibilityTest` y `QrLegibilityTest`; solo cambian las expectativas del negro | F2, F3 | Las seis clases (§3 y §7.1); los únicos cambios son los del §1.2 |
| **Enmienda 2, manual en el Xiaomi:** compartir por WhatsApp y reimportar por la galería y por el escáner | F3 (usuario) | §7.5 |
| El icono "Compartir" genera la imagen con la receta y el QR y abre el selector con `ACTION_SEND` `image/png`, en Básico y Experto, también con predefinidas | F2, F3 (regresión) | `RecipeShareTest.aSavedRecipeSharesAPngInBasic`, `aSavedRecipeSharesAPngInExpert` y `aBuiltInRecipeCanBeShared`; §7.4 |
| **Enmienda 1:** 1080 px de ancho, al menos 1350 de alto, crece con el contenido y muestra todos los pasos y el QR de la receta de 20 pasos | F2, F3 (regresión) | `ShareCardsLayoutTest.aShortRecipeCardKeepsTheMinimum1080x1350WithTheQrBelowTheSteps`, `theRecipeCardGrowsWithItsContent` y `theTallestRecipeCardShowsEveryStepAndTheQr`; §7.4 (tamaños iguales a los de la enmienda 1) |
| **Enmienda 1:** módulos de 6 × 6 y caja de `(módulos + 8) × 6` en el PNG | F2 | Los mismos tests que el primer criterio de la enmienda 2 |
| **Enmienda 1:** la receta 4:6 del usuario se comparte sin recortar nada | F3 (regresión) | `RecipeShareTest.theUsersFourSixRecipeSharesWithoutAskingAndKeepsNotesAndInstructions`; `RecipeShareCodecTest`; §7.4 |
| Unitario: ida y vuelta; recorte en orden; inválida frente a demasiado larga; `NotPourline`, `NewerVersion`, `Corrupt` e `Invalid` | F3 (regresión) | `RecipeShareCodecTest` (§7.1); `git diff` vacío en `recipe/` y `ui/` (§7.2) |
| Unitario: molienda en los tres casos y sin molienda | F3 (sin cambios) | `git diff` vacío en `grinder/` y `recipe/` (§7.2): `SharedGrindResolutionTest` no se ve afectado y no se ejecuta |
| Unitario: idéntica y sufijos " (2)" y " (3)" | F3 (regresión) | `RecipeImportRulesTest` (§7.1) |
| Unitario: `recipe.share` no importa `android.*` | F3 | `RecipeSharePurityTest` (§7.1); `grep` del §7.2 |
| Instrumentado: el QR del PNG de peor caso se lee con ML Kit tal cual y tras 50 % + JPEG 70 | F2 | `RecipeQrLegibilityTest.theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70` y `maxUriLengthIsLegibleOnTheCard` (D54) |
| Instrumentado: compartir envía un `ACTION_SEND` con un PNG legible por `FileProvider` | F2, F3 (regresión) | `RecipeShareTest.thePngIsReadableThroughTheFileProviderAndItsQrDecodesToTheRecipe` |
| Instrumentado: `ACTION_VIEW` y `ACTION_SEND image/*` con un PNG generado abren `RecipeImport`; Guardar añade la receta | F2, F3 (regresión) | `RecipeIntentImportTest` (su `cardPng` ya es la tarjeta de la marca); §7.4 |
| Instrumentado: con una sesión, el intent no navega y la vista previa se abre al terminar | F2, F3 (regresión) | `RecipeIntentImportTest.withASessionTheIntentDoesNotNavigateAndThePreviewOpensWhenItEnds` |
| Instrumentado: una imagen sin QR y un QR ajeno muestran sus mensajes | F2, F3 (regresión) | `RecipesImportTest` y `RecipeIntentImportTest` (QR ajeno en negro, D55) |
| El manifiesto final no contiene `INTERNET` ni `CAMERA` | F3 | `git diff` vacío en el manifiesto, `app/build.gradle.kts` y `gradle/` (§7.2): sin dependencias nuevas, el manifiesto fusionado no cambia |
| Textos nuevos en `values` y `values-en` | F3 | No hay textos nuevos (§3.1); `git diff` vacío en `res/` |
| `:app:lintDebug` y los tests existentes pasan sin cambiar sus expectativas | F2, F3 | §7.1; solo cambian las expectativas del §1.2 |
| Manual con dos móviles: WhatsApp, cuatro entradas y conversión de molienda | F3 (usuario) | Pendiente del plan original; se puede hacer en la misma sesión del §7.5 |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Las semillas nuevas fallan a 600 también con el QR negro: 600 está en el límite (625 ya daba 19 de 20). | Referencia en negro con las mismas semillas en F0 (D54). Si falla, se para antes de tocar código (P2). |
| R2 | La reducción al 50 % con JPEG 70 aclara el azul más que el negro: el JPEG submuestrea el color y mezcla el anillo (luma de `#1677FF`: unos 105 sobre 255) con el blanco de alrededor. | La medición de D54, con la salida del §6 (todo el ojo en `#003EB3`, luma de unos 57) y una parada si tampoco basta. |
| R3 | Las esquinas redondeadas o los colores confunden a otros lectores: la cámara del sistema, Lens, o una imagen muy comprimida por WhatsApp. | Lectura con zxing en la JVM (D56), además de ML Kit. El Code Scanner también se basa en ML Kit. La prueba manual del §7.5 cubre WhatsApp y el escáner; la cámara del sistema se anota, como en el plan original. |
| R4 | Los tests de píxeles fallan por el antialias de la GPU o por la mezcla de la captura (1 % en el primer píxel de cada módulo y la costura de SPEC-010). | Margen de 12 por canal: una mezcla de hasta un 4 % con blanco mueve los dos azules unos 10 niveles. Las esquinas curvas se saltan (D51) y las sondas de D53 están a más de 1 px de cualquier curva. Si falla un píxel de un tramo recto, se investiga antes de tocar el margen y se consulta. |
| R5 | `findQrBox` confunde la barra de acento (`#1677FF`, todo el ancho) con el anillo. | Se saltan las filas con ese color en x = 0 (D52). `everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6` compara su resultado con la posición del nodo. |
| R6 | Cambia `AntSeedColors.primary` y el QR deja de coincidir con la marca, o cambia sin volver a medir. | Los colores son constantes (D47) y `ShareModelsTest` falla si dejan de ser los tonos 6 y 8 de la paleta. |
| R7 | La tarjeta sale distinta con el tema oscuro del teléfono. | `ShareCaptureHost` dibuja siempre con tema claro y los colores del QR son constantes. Las capturas lo comprueban con un hash (§7.6). |
| R8 | Instalar en el Xiaomi un APK con otra firma (el de debug sobre un release con la clave propia) obliga a desinstalar y se pierden los datos. | El agente no instala nada en el Xiaomi. El usuario instala un APK de esta rama con la misma firma que el que tiene, y si no puede, exporta antes la copia de seguridad de SPEC-011 (§7.5). |
| R9 | La medición alarga las suites. | `cardSweep` se salta sin `-e lengths`; las tandas de D54 se lanzan aparte, con `am instrument`. |

## 6. Preguntas para el usuario

Cada una tiene una propuesta. Si se aprueba el plan sin comentarios, se aplica la propuesta.

1. **P1. La pasada gruesa (D54).** El §6 solo habla de 600. *Propuesta: un fallo de 300 a 550 cuenta como un fallo de
   la medición (se aplica la salida y, si persiste, se para), porque ahí está la receta 4:6 del usuario (450).*
   Alternativa: que la pasada gruesa solo informe.
2. **P2. Si el QR negro ya falla con las semillas nuevas (F0, R1).** *Propuesta: parar en F0, sin tocar código, y
   consultar con las tablas.* Las salidas se deciden entonces: dar por buena la marca si iguala al negro con esas
   semillas, o revisar `MAX_URI_LENGTH`, que queda fuera de esta enmienda. No se cambian las semillas para que pase.
3. **P3. El margen de los tests (discrepancia 1).** El §5 dice "8 niveles, como hoy", pero hoy son 12. *Propuesta: usar
   12 y, al cerrar, corregir el número en el §5 y en la nota de cierre de la enmienda 1 del §6.* Alternativa: volver a
   8, que hace fallar los tests de hoy por la costura de la captura que añadió SPEC-010.

## 7. Verificación final

### 7.1 Suites (solo las afectadas)

```
./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.share.*" --tests "dev.josueamaya.pourline.ui.theme.*" --tests "dev.josueamaya.pourline.recipe.share.*"
ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<las seis clases del §3>
./gradlew :app:lintDebug
```

Se compara con la línea base de F0:

- **Tests:** los totales suben por los nuevos: 4 en `QrCodeTest`, 1 en `ShareModelsTest` y 1 en `ShareCardsLayoutTest`.
  No cambia ninguna otra expectativa que las del §1.2.
- **Fallos que no bloquean:** los de F0, si siguen siendo los mismos.
- **Lint:** sin errores ni avisos nuevos.

### 7.2 Comprobaciones

- `grep -rn "import android" app/src/main/kotlin/dev/josueamaya/pourline/recipe/share app/src/main/kotlin/dev/josueamaya/pourline/share/QrCode.kt`
  no encuentra nada.
- `grep -rn "Color.Black" app/src/main/kotlin/dev/josueamaya/pourline/share` no encuentra nada.
- `grep -n "const val MAX_URI_LENGTH = 600" app/src/main/kotlin/dev/josueamaya/pourline/recipe/share/RecipeShareCodec.kt`
  encuentra la línea.
- `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/recipe app/src/main/kotlin/dev/josueamaya/pourline/grinder app/src/main/kotlin/dev/josueamaya/pourline/ui app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle.kts gradle`
  está vacío: no cambian el formato, el códec, la importación, el tema, los textos, el manifiesto ni las dependencias.
- `git diff develop --name-only` lista solo: `share/ShareSpec.kt`, `share/QrCode.kt`, `share/RecipeShareCard.kt`,
  `QrCodeTest.kt`, `ShareModelsTest.kt`, `QrTestSupport.kt`, `ShareCardsLayoutTest.kt`, este plan y el spec.

### 7.3 Rendimiento

La enmienda no pide medidas. El dibujo añade tres `Path` y tres `drawRoundRect` por QR, y se salta 147 módulos. No se
mide.

### 7.4 E2E por adb (sin ARTEMIS)

Todo en `emulator-5560`, porque la reimportación desde la galería necesita el modelo de Play Services.

**Antes de empezar:**

1. `adb devices -l`. El Xiaomi no se toca.
2. `ANDROID_SERIAL=emulator-5560 ./gradlew :app:installDebug` y `adb -s emulator-5560 shell pm clear dev.josueamaya.pourline`.
3. Los dos URIs de D44 (`4:6 URI:` y `E2E URI:`), sacados del XML de resultados de `RecipeShareCodecTest`, que el §7.1
   ya genera:
   `app/build/test-results/testDebugUnitTest/TEST-dev.josueamaya.pourline.recipe.share.RecipeShareCodecTest.xml`.

**Herramientas:** `uiautomator dump`, `input tap` y `screencap`. El PNG compartido se saca con
`adb -s emulator-5560 exec-out run-as dev.josueamaya.pourline cat cache/shared/recipe-<id>.png > <archivo>.png` justo
después de cada compartir. `adb -s emulator-5560 logcat -b crash -d` debe quedar vacío en todo el recorrido.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Onboarding → Molinos → añadir "Femobook A2" del catálogo a mis molinos (el mismo punto de partida que el E2E de la enmienda 1). `adb -s emulator-5560 shell "am start -a android.intent.action.VIEW -d '<URI de la 4:6>'"` → Guardar. Lo mismo con el URI de peor caso | Las dos vistas previas, sin aviso de recorte; la 4:6 con la molienda enlazada al A2. |
| 2 | Abrir "V60 clásica", la 4:6 y la de peor caso, y Compartir cada una | Ningún diálogo. Tamaños, con `sips -g pixelWidth -g pixelHeight`, iguales a los de la enmienda 1 (§6 del spec): 1080 × 1350 (corta), 1080 × 1656 (4:6) y 1080 × 2226 (peor caso del E2E). Los colores no cambian la maqueta. |
| 3 | Abrir los tres PNG al 100 % | Ojos redondeados, anillo en el azul primario (o en azul oscuro, si se aplicó la salida), datos y centros en azul oscuro, fondo blanco, QR centrado bajo los pasos. |
| 4 | En el Mac, `sips --resampleWidth 540 -s format jpeg -s formatOptions 70` sobre el PNG de la 4:6 y el de peor caso; `adb push` del PNG y del JPEG a `/sdcard/Download/` y `adb -s emulator-5560 shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/<archivo>`; Recetas → Importar → "Desde una imagen" | Las cuatro imágenes abren la vista previa. La 4:6, sin aviso de recorte y con las notas y las 10 instrucciones. |

### 7.5 Prueba manual del usuario (Xiaomi)

La hace el usuario. El agente no instala nada en su móvil.

- **APK:** uno de esta rama con la misma firma que el que tiene instalado. Si es el release firmado con su clave, lo
  genera el usuario con su configuración de firma. Un APK con otra firma obliga a desinstalar y borra los datos: antes,
  exportar la copia de seguridad de SPEC-011 (R8).
- Compartir la receta 4:6 por WhatsApp y volver a importarla desde la imagen recibida: por la galería ("Desde una
  imagen") y por "Escanear QR", con la imagen mostrada en otra pantalla. Las dos abren la vista previa sin aviso de
  recorte.
- A ojo: que los ojos se vean redondeados y el QR en los azules de la marca, también con el teléfono en tema oscuro.
- Opcional: la cámara del sistema o Lens con la misma imagen, anotando si ofrece abrir el enlace.
- Si algo falla, se consulta antes de cerrar.
- Lo pendiente de los planes anteriores (dos móviles, cuatro entradas y conversión de molienda) se puede hacer en la
  misma sesión.

### 7.6 Capturas

En `~/Downloads/pourline-recipe-share/qr-style/{es,en}/`, fuera del repo:

- **La tarjeta (el PNG compartido):** la receta corta, la 4:6 del usuario y la de peor caso, en tema claro. Son 6
  archivos. En tema oscuro, `shasum -a 256` de cada PNG tiene que coincidir con el de claro (no se guardan dos veces).
- Idioma: `adb -s emulator-5560 shell cmd locale set-app-locales dev.josueamaya.pourline --locales es-ES` (y `en-US`), o
  el idioma del sistema si el emulador no tiene ese comando. Tema: Ajustes → Apariencia.
- **Qué revisar a ojo:** lo del paso 3 del §7.4, al lado de las capturas en negro de la enmienda 1
  (`~/Downloads/pourline-recipe-share-large-qr/`).

## 8. Seguimiento fuera de este plan

- **Al cerrar (F3), en el §6 del spec:** la medición y si se aplicó la salida, como pide el propio spec (D57).
- **Cambios que conviene llevar al spec, sin tocarlo ahora:** el margen de 12 niveles en el §5 y en el §6 (P3); los
  radios en píxeles (13, 8 y 5) en el §3.3; que los módulos de los ojos no se pintan como módulos (discrepancia 2); y
  la referencia en negro de la medición (D54).
- **README:** sus capturas no muestran la tarjeta de receta, así que no hay nada que actualizar.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
