# Catálogo de recetas recomendadas

`recipes.json` es el catálogo que Pourline muestra en **Recetas > Recomendadas**. Es la única fuente: la app lo
descarga de la rama `develop` y, además, el build lo copia al APK (`assets/catalog/recipes.json`) como copia inicial.
Nunca se mantiene una segunda copia a mano.

La app descarga el archivo solo al abrir "Recomendadas" (como mucho una vez al día, o al deslizar hacia abajo para
actualizar). Entre la copia descargada y la del APK usa la de `revision` mayor. `raw.githubusercontent.com` cachea unos
minutos: un cambio en `develop` puede tardar ese tiempo en llegar a la app.

## Formato (`schemaVersion` 1)

```json
{
  "schemaVersion": 1,
  "revision": 2,
  "recipes": [
    {
      "key": "four_six",
      "name": { "es": "...", "en": "..." },
      "author": "Tetsu Kasuya",
      "context": { "es": "...", "en": "..." },
      "method": "V60",
      "methodOther": { "es": "...", "en": "..." },
      "doseG": 20.0,
      "waterTempC": 93.0,
      "grind": {
        "text": { "es": "...", "en": "..." },
        "grinder": { "catalogKey": "comandante-c40", "value": 30 }
      },
      "notes": { "es": "...", "en": "..." },
      "sourceUrl": "https://...",
      "roasts": ["LIGHT", "MEDIUM"],
      "stages": [
        {
          "type": "POUR",
          "startS": 0,
          "durationS": 10,
          "waterG": 60.0,
          "name": { "es": "...", "en": "..." },
          "instruction": { "es": "...", "en": "..." }
        }
      ]
    }
  ]
}
```

| Campo | Obligatorio | Contenido |
|---|---|---|
| `schemaVersion` | sí | versión del formato (hoy `1`) |
| `revision` | sí | entero >= 1; se incrementa en **cada** cambio del archivo |
| `key` | sí | `[a-z0-9_]{1,64}`, estable y única; **nunca se reutiliza** para otra receta |
| `name` | sí | texto localizado |
| `author` | no | autor o barista, sin traducir; si está, `sourceUrl` es obligatoria |
| `context` | no | campeonato y año, o publicación (texto localizado) |
| `method` | sí | `V60`, `KALITA_WAVE`, `CHEMEX`, `ORIGAMI`, `AEROPRESS`, `FRENCH_PRESS`, `MOKA` u `OTHER` (también existen `ESPRESSO` y `COLD_BREW`, pero no caben en el modelo de pasos). `MOKA` solo cabe si únicamente el llenado de la base es un vertido que se pesa, como en `coava_moka`; el resto son pasos `ACTION` |
| `methodOther` | solo con `OTHER` | texto localizado ("Clever", "April"…); con otro método no puede aparecer |
| `doseG` | sí | gramos, de 5 a 60 y con un decimal como mucho |
| `waterTempC` | no | °C, de 0 a 100; omítela si la receta usa varias y pon cada una en la instrucción del paso |
| `grind.text` | no | descripción de la molienda (texto localizado) |
| `grind.grinder` | no | `catalogKey` de `app/src/main/assets/grinders/catalog.json` y `value` en las unidades base de ese molino, dentro de su escala |
| `notes` | no | instrucciones generales, no notas de cata (texto localizado) |
| `sourceUrl` | no (sí con `author`) | empieza por `https://` |
| `roasts` | no | lista de tuestes para los que sirve la receta, entre `LIGHT`, `MEDIUM` y `DARK`, sin repetir. Ausente: sirve para cualquier tueste. La app ignora al leer cualquier valor desconocido o un campo que no sea una lista de textos (la receta se muestra como "cualquier tueste"); el test del repo los rechaza |
| `stages[].type` | sí | `POUR`, `SWIRL`, `WAIT` o `ACTION` |
| `stages[].startS`, `durationS` | sí | segundos desde t0 y duración, enteros |
| `stages[].waterG` | solo en `POUR` | agua **acumulada** al terminar el paso (en el resto no puede aparecer) |
| `stages[].name`, `instruction` | no | texto localizado; la instrucción, de 200 caracteres como mucho por idioma (reglas de redacción más abajo) |

Un **texto localizado** es un objeto con `es` y `en`, las dos claves obligatorias y no vacías. Los campos desconocidos
se ignoran.

## Reglas de validación

Las aplica la app al leer (una receta inválida se descarta sola y el resto se muestra) y el test del repo (que falla con
cualquier receta descartada):

- Convertida a receta en cada idioma, pasa el validador de recetas de la app: dosis 5-60 g, temperatura <= 100 °C, de 1 a
  20 pasos con al menos un `POUR`, agua acumulada creciente y <= 1000 g, `POUR`/`SWIRL`/`ACTION` de 1 a 120 s, `WAIT` de
  1 a 600 s, sin solapes y último paso que empieza antes de los 600 s.
- `method` y `type` son valores conocidos; `methodOther` está si y solo si `method` es `OTHER`.
- `grind.grinder.catalogKey` existe en el catálogo de molinos y `value` está en su escala.
- `sourceUrl` empieza por `https://`; una receta con `author` lleva `sourceUrl`.
- `key` es única en el archivo (si se repite, se descartan todas las recetas con esa clave).
- Deben existir `four_six` y `v60_classic`: las copias migradas de las antiguas recetas de fábrica apuntan a ellas.
- `roasts` solo con nombres de tueste conocidos y sin repetir.
- Proporción por tueste: al menos 2/3 de las recetas aptas para medio (con `MEDIUM` o sin `roasts`) y como mucho 10 solo para claro (`["LIGHT"]`).
- Redacción: ningún nombre de paso, instrucción ni nota usa un término prohibido (tabla de abajo) ni un tiempo `m:ss`.

## Cómo añadir o corregir una receta

Para añadir recetas nuevas, `scripts/catalog_recipe.py` hace casi todo: `summary <METHOD>` lista las recetas del método,
el cupo de tueste y lo que ya se descartó en el anexo, y `build <archivo> [--write]` convierte un formato compacto a
JSON, lo valida con las reglas de abajo y, con `--write`, lo añade e incrementa `revision` (formato en el propio
script). En Claude Code, el skill `/receta-catalogo <método>` recorre el proceso completo, investigación incluida.

1. Edita `recipes.json`: añade la receta al final (la lista se muestra en el orden del archivo) o corrige la que toca.
2. **Incrementa `revision`** en 1. Sin eso, las apps que ya descargaron el catálogo no recogen el cambio.
3. Usa una `key` nueva y que nunca se haya usado, aunque borres una receta.
4. Rellena `es` y `en` en todos los textos localizados.
5. Lo que se modela:
   - **Dilución después de prensar** (AeroPress): un paso `ACTION` con los gramos y la temperatura en la instrucción
     ("Añade 60 g de agua a 96 °C"). La curva termina en el agua de infusión; las notas dan el ratio final.
   - **Hielo**: no se modela; las notas indican los gramos de hielo y el ratio final.
   - **Varias temperaturas**: `waterTempC` es la principal o se omite; las demás van en la instrucción.
   - **Duraciones inferidas**: las fuentes casi nunca dan la duración de cada vertido; se fijan para que encajen los
     inicios publicados y el anexo del spec (`docs/specs/anexo-catalogo-recetas.md`) las marca.
6. Elige `roasts` y redacta con las reglas de la sección "Tueste y redacción".
7. Comprueba el catálogo en local, desde la raíz del repo:

   ```sh
   ./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.RecipeCatalogFileTest"
   ```

   El mismo test lo ejecuta el workflow `Catalog` de GitHub Actions en cada `push` y `pull_request` que toque
   `catalog/**`.

## Tueste y redacción

**Tueste.** El catálogo favorece el tueste medio, que es el más fácil de conseguir: al menos 2/3 de las recetas aptas para medio
y, como mucho, 10 solo para claro, reservadas a las de campeonato más icónicas y a recetas de goteros
concretos (como la V60 NEO) sin variante para medio. `roasts` es lo que dice la fuente de la receta;
si la fuente no lo dice se infiere (y el anexo lo marca), y una guía genérica de fabricante sin indicación es "cualquiera" (sin
`roasts`). Si la fuente publica una variante para tueste medio (la 4:6 de Kasuya a 88 °C), se usa esa variante. Nunca se cambian
por cuenta propia los parámetros de un autor: sin variante publicada, la receta se queda como es o se sustituye. El tueste no se
guarda en la receta de "Mis recetas": solo filtra y se muestra en el catálogo.

**Redacción** de nombres de paso, instrucciones y notas, en español y en inglés, para todas las recetas:

1. Cada instrucción dice qué hacer en ese paso, en lenguaje llano y sin jerga.
2. Sin tiempos ni horas que la app ya muestra ("Espera hasta 0:30", "termina hacia 1:08"). Un tiempo que la app no muestra (un rango
   de la fuente, una variante) va en las notas, con unidades: "4 min", "2 min 30 s". Nunca con formato `m:ss`.
3. En los vertidos, lo que se añade y el total: "Añade 60 g (total 120 g)" / "Add 60 g (total 120 g)", con la técnica detrás si la hay
   ("en círculos") o un prefijo ("AeroPress invertida: añade…"). El primer vertido solo dice lo que se añade ("Añade 60 g en
   círculos"): su total sería redundante.
4. "Girar el gotero" siempre es `SWIRL` y la instrucción dice qué se gira ("Gira el gotero suavemente"); "remover con cuchara" siempre es
   `ACTION`. La etiqueta española de `SWIRL` en la app es "Remover", así que un `SWIRL` nunca queda vacío.
5. Si el paso no necesita más que su tipo, la instrucción queda vacía (un `WAIT` solo dice algo si hay algo que hacer o es el filtrado
   final: "Deja que termine de filtrar.").
6. Sin comentarios que no sean una acción ("Pausa.", "El 40 % define dulzor y acidez"): si valen, van a las notas.

| Prohibido | Español | Inglés |
|---|---|---|
| drawdown | "Deja que termine de filtrar" | "Let it finish draining" |
| rao spin | "Gira el gotero con energía" | "Swirl the dripper firmly" |
| remolinea / remolinear | "Gira el gotero suavemente" | "Gently swirl the dripper" |
| bloom (solo en español) | "Preinfusión" | "Bloom" (se mantiene) |

El test busca los términos prohibidos sin distinguir mayúsculas ni acentos (también "draw-down" o "rao-spin") y los tiempos `m:ss` en
`stages[].name`, `stages[].instruction` y `notes`; no mira el nombre, el contexto ni `grind.text` de la receta. **Ratios:** un ratio
entero en las notas va siempre tras la palabra "ratio" ("ratio final con hielo 1:13", "the final ratio is 1:13") y no cuenta como
tiempo; un ratio con decimales ("1:16,7") nunca tiene la forma `m:ss`. Un "1:13" sin "ratio" delante sí cuenta como tiempo.

## Cuándo subir `schemaVersion`

Cuando el cambio es algo que una versión anterior de la app no puede representar: un `BrewMethod` o `StageType` nuevo
como valor obligatorio, o un cambio de significado de un campo. Una versión de la app que no entiende la nueva
`schemaVersion` ignora la descarga y avisa de que hay que actualizar la app.

Dentro de la misma `schemaVersion` se pueden añadir campos opcionales sin subirla: los campos desconocidos se ignoran. Un
valor desconocido en una receta (por ejemplo un método que esta versión de la app no tiene) hace que se descarte solo
esa receta.
