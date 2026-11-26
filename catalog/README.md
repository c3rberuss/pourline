# Catálogo de recetas recomendadas

`recipes.json` es el catálogo que Pourline muestra en **Recetas > Recomendadas**. Es la única fuente: la app lo
descarga de la rama `main` y, además, el build lo copia al APK (`assets/catalog/recipes.json`) como copia inicial.
Nunca se mantiene una segunda copia a mano.

La app descarga el archivo solo al abrir "Recomendadas" (como mucho una vez al día, o al deslizar hacia abajo para
actualizar). Entre la copia descargada y la del APK usa la de `revision` mayor. `raw.githubusercontent.com` cachea unos
minutos: un cambio en `main` puede tardar ese tiempo en llegar a la app.

## Formato (`schemaVersion` 1)

```json
{
  "schemaVersion": 1,
  "revision": 1,
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
| `method` | sí | `V60`, `KALITA_WAVE`, `CHEMEX`, `ORIGAMI`, `AEROPRESS`, `FRENCH_PRESS` u `OTHER` (también existen `ESPRESSO`, `COLD_BREW` y `MOKA`, pero no caben en el modelo de pasos) |
| `methodOther` | solo con `OTHER` | texto localizado ("Clever", "April"…); con otro método no puede aparecer |
| `doseG` | sí | gramos, de 5 a 60 y con un decimal como mucho |
| `waterTempC` | no | °C, de 0 a 100; omítela si la receta usa varias y pon cada una en la instrucción del paso |
| `grind.text` | no | descripción de la molienda (texto localizado) |
| `grind.grinder` | no | `catalogKey` de `app/src/main/assets/grinders/catalog.json` y `value` en las unidades base de ese molino, dentro de su escala |
| `notes` | no | instrucciones generales, no notas de cata (texto localizado) |
| `sourceUrl` | no (sí con `author`) | empieza por `https://` |
| `stages[].type` | sí | `POUR`, `SWIRL`, `WAIT` o `ACTION` |
| `stages[].startS`, `durationS` | sí | segundos desde t0 y duración, enteros |
| `stages[].waterG` | solo en `POUR` | agua **acumulada** al terminar el paso (en el resto no puede aparecer) |
| `stages[].name`, `instruction` | no | texto localizado; la instrucción, de 200 caracteres como mucho por idioma |

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

## Cómo añadir o corregir una receta

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
     inicios publicados y las notas lo dicen.
6. Comprueba el catálogo en local, desde la raíz del repo:

   ```sh
   ./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.recipe.catalog.RecipeCatalogFileTest"
   ```

   El mismo test lo ejecuta el workflow `Catalog` de GitHub Actions en cada `push` y `pull_request` que toque
   `catalog/**`.

## Cuándo subir `schemaVersion`

Cuando el cambio es algo que una versión anterior de la app no puede representar: un `BrewMethod` o `StageType` nuevo
como valor obligatorio, o un cambio de significado de un campo. Una versión de la app que no entiende la nueva
`schemaVersion` ignora la descarga y avisa de que hay que actualizar la app.

Dentro de la misma `schemaVersion` se pueden añadir campos opcionales sin subirla: los campos desconocidos se ignoran. Un
valor desconocido en una receta (por ejemplo un método que esta versión de la app no tiene) hace que se descarte solo
esa receta.
