#!/usr/bin/env python3
"""Ayuda para ampliar catalog/recipes.json.

  python3 scripts/catalog_recipe.py summary AEROPRESS
      Recetas del método, cupo de tueste y descartes del anexo (para no repetir).

  python3 scripts/catalog_recipe.py build recetas.txt [--write]
      Convierte recetas en formato compacto a JSON, las valida y, con --write,
      las añade al final del catálogo e incrementa `revision` en 1.

Formato compacto (varias recetas separadas por una línea `---`; `es | en` en los textos):

  key: coava_aeropress
  method: AEROPRESS
  methodOther: Clever | Clever                (solo con method OTHER)
  name: AeroPress de Coava | Coava AeroPress
  author: Coava Coffee Roasters
  context: Guía de preparación oficial | Official brew guide
  dose: 14
  temp: 95                                     (opcional)
  grind: Media-fina, como arena | Medium-fine, like sand   (opcional)
  grinder: comandante-c40 27                   (opcional)
  notes: AeroPress invertida. | Inverted AeroPress.        (opcional)
  source: https://coavacoffee.com/brew-methods/aeropress
  roasts: LIGHT, MEDIUM                        (opcional; ausente = cualquiera)
  stages:
  POUR 0 5 50 = AeroPress invertida: añade 50 g rápido. | Inverted AeroPress: add 50 g quickly.
  ACTION . 5 = Remueve brevemente. | Stir briefly.
  WAIT . >30
  POUR 30 10 210 + con suavidad | gently
  WAIT . 40 drain

Pasos: TIPO INICIO DURACIÓN [AGUA] [bloom] [drain] [+ cola_es | cola_en] [= texto_es | texto_en]
  - INICIO `.` = justo al terminar el paso anterior.
  - DURACIÓN `>N` = dura hasta el segundo N.
  - POUR: AGUA es el total acumulado; sin `=` la instrucción se genera:
    "Añade 50 g." (primer vertido) o "Añade 160 g (total 210 g)." y la cola opcional detrás.
  - bloom: el paso se llama "Preinfusión" / "Bloom".
  - drain: instrucción "Deja que termine de filtrar." (para el WAIT final).
"""

import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "catalog" / "recipes.json"
GRINDERS = ROOT / "app" / "src" / "main" / "assets" / "grinders" / "catalog.json"
ANNEX = ROOT / "docs" / "specs" / "anexo-catalogo-recetas.md"

METHODS = ["V60", "KALITA_WAVE", "CHEMEX", "ORIGAMI", "AEROPRESS", "FRENCH_PRESS", "MOKA", "OTHER"]
STAGE_TYPES = ["POUR", "SWIRL", "WAIT", "ACTION"]
ROASTS = ["LIGHT", "MEDIUM", "DARK"]
MAX_LIGHT_ONLY = 10
METHOD_KEYWORDS = {
    "V60": ["v60"],
    "KALITA_WAVE": ["kalita"],
    "CHEMEX": ["chemex"],
    "ORIGAMI": ["origami"],
    "AEROPRESS": ["aeropress", "wac"],
    "FRENCH_PRESS": ["prensa francesa", "french press"],
    "MOKA": ["moka"],
    "OTHER": ["clever", "switch", "april", "otro", "gina", "solo dripper", "orea"],
}

BLOOM = {"es": "Preinfusión", "en": "Bloom"}
DRAIN = {"es": "Deja que termine de filtrar.", "en": "Let it finish draining."}

# Mismas reglas que app/src/test/.../CatalogContentRules.kt
FORBIDDEN = [
    ("drawdown", re.compile(r"\bdraw[\s-]*downs?\b"), {"es", "en"}),
    ("rao spin", re.compile(r"\brao[\s-]*spin\b"), {"es", "en"}),
    ("remolinea", re.compile(r"\bremoline\w*"), {"es", "en"}),
    ("bloom", re.compile(r"\bbloom\w*"), {"es"}),
]
RATIO_MENTION = re.compile(r"(?i)\bratio\b[^.;\d]{0,40}?\b1:\d+(?:[.,]\d+)?")
CLOCK_TIME = re.compile(r"(?<![\d:.,])\d{1,2}:\d{2}(?![\d:]|[.,]\d)")


def load_catalog():
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def fold(text):
    return "".join(c for c in unicodedata.normalize("NFD", text.lower()) if unicodedata.category(c) != "Mn")


def light_only(recipe):
    return recipe.get("roasts") == ["LIGHT"]


def suits_medium(recipe):
    return not recipe.get("roasts") or "MEDIUM" in recipe["roasts"]


def annex_keys():
    if not ANNEX.exists():
        return set()
    return set(re.findall(r"`([a-z0-9_]{3,64})`", ANNEX.read_text(encoding="utf-8")))


# ---------------------------------------------------------------- summary


def summary(method):
    if method not in METHODS:
        sys.exit(f"método desconocido: {method} (usa uno de {', '.join(METHODS)})")
    recipes = load_catalog()["recipes"]
    mine = [r for r in recipes if r["method"] == method]
    print(f"## {method}: {len(mine)} recetas en el catálogo\n")
    for r in mine:
        other = f" [{r['methodOther']['es']}]" if r.get("methodOther") else ""
        roasts = ",".join(r.get("roasts", [])) or "cualquiera"
        print(f"- {r['key']}{other} | {r.get('author', '-')} | {r['name']['es']} | {r['doseG']:g} g | {roasts}")

    lo = sum(1 for r in recipes if light_only(r))
    med = sum(1 for r in recipes if suits_medium(r))
    print(f"\n## Cupo de tueste\n")
    print(f"- Solo claro: {lo} de {MAX_LIGHT_ONLY} -> caben {max(0, MAX_LIGHT_ONLY - lo)} más.")
    print(f"- Aptas para medio: {med} de {len(recipes)} (mínimo 2/3).")
    if lo >= MAX_LIGHT_ONLY:
        print("- Las nuevas deben servir para tueste medio (fuente que lo diga o guía genérica sin tueste).")

    keys = {r["key"] for r in recipes}
    print("\n## Investigadas antes y fuera del catálogo (retiradas o descartadas)\n")
    for title, key in annex_recipes(method):
        if key not in keys:
            print(f"- {title} (`{key}`)")
    print("\n## Descartes y reservas del anexo que mencionan el método\n")
    print("(no volver a investigarlas salvo que haya una fuente nueva)\n")
    for line in annex_mentions(method):
        print(line)


def annex_blocks():
    """Párrafos del anexo (las líneas partidas se unen; cada fila de tabla va sola) con su sección."""
    section, paragraph = "", []
    for raw in ANNEX.read_text(encoding="utf-8").splitlines() + [""]:
        line = raw.strip()
        if paragraph and (not line or line.startswith(("#", "|", "- ")) or paragraph[0].startswith("|")):
            yield section, " ".join(paragraph)
            paragraph = []
        if line.startswith("#"):
            section = line
            yield section, line
        elif line:
            paragraph.append(line)


def annex_recipes(method):
    """Recetas con ficha propia en el anexo: (título, clave)."""
    if not ANNEX.exists():
        return []
    keywords = METHOD_KEYWORDS[method]
    found, title = [], None
    for section, block in annex_blocks():
        if block.startswith("### ") and re.match(r"### \d+\. ", block):
            title = block[4:]
        elif title and block.startswith("- **Clave**"):
            key = re.search(r"`([a-z0-9_]+)`", block)
            if key and any(k in fold(title) for k in keywords):
                found.append((title, key.group(1)))
            title = None
    return found


def annex_mentions(method):
    if not ANNEX.exists():
        return []
    keywords = METHOD_KEYWORDS[method]
    out = []
    for section, block in annex_blocks():
        if block.startswith("#"):
            continue
        relevant = re.search(r"descart|reserva|fuente insuficiente|no caben", fold(section)) or re.match(
            r"^(\*\*)?descartad", fold(block)
        )
        if relevant and any(k in fold(block) for k in keywords):
            out.append(f"- {block[:600]}")
    return out


# ---------------------------------------------------------------- build


def loc(value, field):
    parts = [p.strip() for p in value.split("|")]
    if len(parts) != 2 or not all(parts):
        raise ValueError(f"{field}: hace falta 'es | en', recibido {value!r}")
    return {"es": parts[0], "en": parts[1]}


def number(text, field):
    try:
        return float(text.replace(",", "."))
    except ValueError:
        raise ValueError(f"{field}: número inválido {text!r}")


def parse_stage(line, previous_end, first_pour):
    text, full = line, None
    if " = " in text:
        text, full = text.split(" = ", 1)
    tail = None
    if " + " in text:
        text, tail = text.split(" + ", 1)
    tokens = text.split()
    if len(tokens) < 3:
        raise ValueError(f"paso incompleto: {line!r}")
    kind = tokens[0].upper()
    if kind not in STAGE_TYPES:
        raise ValueError(f"tipo de paso desconocido: {tokens[0]!r}")
    start = previous_end if tokens[1] == "." else int(tokens[1])
    if tokens[2].startswith(">"):
        duration = int(tokens[2][1:]) - start
    else:
        duration = int(tokens[2])
    rest = tokens[3:]
    stage = {"type": kind, "startS": start, "durationS": duration}
    water = None
    if kind == "POUR":
        if not rest:
            raise ValueError(f"POUR sin agua acumulada: {line!r}")
        water = number(rest.pop(0), "agua")
        stage["waterG"] = water
    flags = {f.lower() for f in rest}
    unknown = flags - {"bloom", "drain"}
    if unknown:
        raise ValueError(f"marcas desconocidas {sorted(unknown)} en {line!r}")
    if "bloom" in flags:
        stage["name"] = dict(BLOOM)
    if full:
        stage["instruction"] = loc(full, "instrucción")
    elif "drain" in flags:
        stage["instruction"] = dict(DRAIN)
    elif kind == "POUR":
        stage["instruction"] = pour_text(water, first_pour, tail)
    elif tail:
        raise ValueError(f"'+ cola' solo vale en POUR: {line!r}")
    return stage


def pour_text(total, previous_total, tail):
    g = lambda v: f"{v:g}"
    added = total - (previous_total or 0)
    tails = loc(tail, "cola") if tail else {"es": "", "en": ""}
    if previous_total is None:
        es, en = f"Añade {g(added)} g", f"Add {g(added)} g"
    else:
        es, en = f"Añade {g(added)} g (total {g(total)} g)", f"Add {g(added)} g (total {g(total)} g)"
    if tail:
        es, en = f"{es} {tails['es']}", f"{en} {tails['en']}"
    return {"es": es.rstrip(".") + ".", "en": en.rstrip(".") + "."}


FIELD_ORDER = ["key", "name", "author", "context", "method", "methodOther", "doseG", "waterTempC", "grind",
               "notes", "sourceUrl", "roasts", "stages"]


def parse_recipe(block):
    fields, stages, in_stages = {}, [], False
    previous_end, last_water = 0, None
    for raw in block.splitlines():
        line = raw.split("  #", 1)[0].rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if in_stages:
            stage = parse_stage(line.strip(), previous_end, last_water)
            stages.append(stage)
            previous_end = stage["startS"] + stage["durationS"]
            if stage["type"] == "POUR":
                last_water = stage["waterG"]
            continue
        if line.strip() == "stages:":
            in_stages = True
            continue
        if ":" not in line:
            raise ValueError(f"línea sin 'campo: valor': {line!r}")
        name, value = (p.strip() for p in line.split(":", 1))
        fields[name] = value

    r = {}
    try:
        r["key"] = fields.pop("key")
        r["name"] = loc(fields.pop("name"), "name")
        r["method"] = fields.pop("method").upper()
        r["doseG"] = number(fields.pop("dose"), "dose")
    except KeyError as missing:
        raise ValueError(f"falta el campo obligatorio {missing}")
    if "author" in fields:
        r["author"] = fields.pop("author")
    if "context" in fields:
        r["context"] = loc(fields.pop("context"), "context")
    if "methodOther" in fields:
        r["methodOther"] = loc(fields.pop("methodOther"), "methodOther")
    if "temp" in fields:
        r["waterTempC"] = number(fields.pop("temp"), "temp")
    grind = {}
    if "grind" in fields:
        grind["text"] = loc(fields.pop("grind"), "grind")
    if "grinder" in fields:
        key, value = fields.pop("grinder").split()
        grind["grinder"] = {"catalogKey": key, "value": int(value) if float(value).is_integer() else float(value)}
    if grind:
        r["grind"] = grind
    if "notes" in fields:
        r["notes"] = loc(fields.pop("notes"), "notes")
    if "source" in fields:
        r["sourceUrl"] = fields.pop("source")
    if "roasts" in fields:
        r["roasts"] = [x.strip().upper() for x in fields.pop("roasts").split(",") if x.strip()]
    if fields:
        raise ValueError(f"campos desconocidos: {sorted(fields)}")
    r["stages"] = stages
    return {k: r[k] for k in FIELD_ORDER if k in r}


def texts(recipe, lang):
    """Textos que el test revisa: nombres de paso, instrucciones y notas."""
    out = []
    if "notes" in recipe:
        out.append(("notes", recipe["notes"][lang]))
    for i, s in enumerate(recipe["stages"]):
        for field in ("name", "instruction"):
            if field in s:
                out.append((f"stages[{i}].{field}", s[field][lang]))
    return out


def validate(recipe, existing_keys, historic_keys, grinders):
    p = []
    key = recipe["key"]
    if not re.fullmatch(r"[a-z0-9_]{1,64}", key):
        p.append("key inválida")
    if key in existing_keys:
        p.append("key repetida en el catálogo")
    elif key in historic_keys:
        p.append("key ya usada en el anexo (retirada o descartada): las claves nunca se reutilizan")
    if recipe["method"] not in METHODS:
        p.append(f"método desconocido {recipe['method']}")
    if (recipe["method"] == "OTHER") != ("methodOther" in recipe):
        p.append("methodOther va si y solo si method es OTHER")
    dose = recipe["doseG"]
    if not 5 <= dose <= 60 or round(dose, 1) != dose:
        p.append("dosis fuera de 5-60 g o con más de un decimal")
    temp = recipe.get("waterTempC")
    if temp is not None and (not 0 <= temp <= 100 or round(temp, 1) != temp):
        p.append("temperatura fuera de 0-100 °C o con más de un decimal")
    if "author" in recipe and "sourceUrl" not in recipe:
        p.append("con author hace falta source")
    if "sourceUrl" in recipe and not recipe["sourceUrl"].startswith("https://"):
        p.append("source debe empezar por https://")
    roasts = recipe.get("roasts", [])
    if any(x not in ROASTS for x in roasts) or len(set(roasts)) != len(roasts):
        p.append(f"roasts inválido {roasts}")
    grinder = recipe.get("grind", {}).get("grinder")
    if grinder:
        g = grinders.get(grinder["catalogKey"])
        if not g:
            p.append(f"molino {grinder['catalogKey']} no está en el catálogo de molinos")
        elif not g["scale"]["min"] <= grinder["value"] <= g["scale"]["max"]:
            p.append(f"ajuste {grinder['value']} fuera de la escala {g['scale']}")

    stages = recipe["stages"]
    if not 1 <= len(stages) <= 20:
        p.append("hacen falta de 1 a 20 pasos")
    if not any(s["type"] == "POUR" for s in stages):
        p.append("hace falta al menos un POUR")
    prev, water = None, 0.0
    for i, s in enumerate(stages):
        limit = 600 if s["type"] == "WAIT" else 120
        if not 1 <= s["durationS"] <= limit:
            p.append(f"paso {i} ({s['type']}): duración {s['durationS']} s fuera de 1-{limit}")
        if s["startS"] < 0:
            p.append(f"paso {i}: inicio negativo")
        if prev and s["startS"] < prev["startS"] + prev["durationS"]:
            p.append(f"paso {i}: se solapa con el anterior")
        if s["type"] == "POUR":
            if s["waterG"] <= water:
                p.append(f"paso {i}: el agua acumulada no crece")
            water = s["waterG"]
        for lang in ("es", "en"):
            if len(s.get("instruction", {}).get(lang, "")) > 200:
                p.append(f"paso {i}: instrucción ({lang}) de más de 200 caracteres")
        prev = s
    if water > 1000:
        p.append("más de 1000 g de agua")
    if stages and stages[-1]["startS"] >= 600:
        p.append("el último paso empieza a los 600 s o después")

    for lang in ("es", "en"):
        for field, text in texts(recipe, lang):
            folded = fold(text)
            for term, pattern, langs in FORBIDDEN:
                if lang in langs and pattern.search(folded):
                    p.append(f"{field} ({lang}): término prohibido «{term}»")
            if CLOCK_TIME.search(RATIO_MENTION.sub("", text)):
                p.append(f"{field} ({lang}): tiempo m:ss (escribe «2 min 30 s» o pon «ratio» delante)")
    return p


def fmt_time(seconds):
    return f"{seconds // 60} min {seconds % 60:02d} s"


def build(path, write):
    source = sys.stdin.read() if path == "-" else Path(path).read_text(encoding="utf-8")
    blocks = [b for b in re.split(r"^---\s*$", source, flags=re.M) if b.strip()]
    catalog = load_catalog()
    existing = {r["key"] for r in catalog["recipes"]}
    historic = annex_keys()
    grinders = {g["key"]: g for g in json.loads(GRINDERS.read_text(encoding="utf-8"))["grinders"]}

    new, failed = [], False
    for block in blocks:
        try:
            recipe = parse_recipe(block)
        except ValueError as e:
            print(f"ERROR al leer una receta: {e}")
            failed = True
            continue
        problems = validate(recipe, existing | {r["key"] for r in new}, historic, grinders)
        last = recipe["stages"][-1] if recipe["stages"] else {"startS": 0, "durationS": 0}
        water = max((s["waterG"] for s in recipe["stages"] if s["type"] == "POUR"), default=0)
        ratio = f"1:{water / recipe['doseG']:.1f}" if recipe["doseG"] else "-"
        status = "OK" if not problems else "ERROR"
        print(f"[{status}] {recipe['key']} | {recipe['method']} | {recipe['doseG']:g} g / {water:g} g ({ratio}) | "
              f"{len(recipe['stages'])} pasos | {fmt_time(last['startS'] + last['durationS'])}")
        for problem in problems:
            print(f"    - {problem}")
        failed |= bool(problems)
        new.append(recipe)

    all_recipes = catalog["recipes"] + new
    lo = sum(1 for r in all_recipes if light_only(r))
    med = sum(1 for r in all_recipes if suits_medium(r))
    if lo > MAX_LIGHT_ONLY:
        print(f"ERROR: quedarían {lo} recetas solo para claro (máximo {MAX_LIGHT_ONLY})")
        failed = True
    if med * 3 < len(all_recipes) * 2:
        print(f"ERROR: solo {med} de {len(all_recipes)} recetas servirían para tueste medio (mínimo 2/3)")
        failed = True

    if failed:
        sys.exit(1)
    if not write:
        print(json.dumps(new, ensure_ascii=False, indent=2))
        print(f"\n{len(new)} receta(s) válidas. Repite con --write para añadirlas al catálogo.")
        return
    catalog["recipes"] = all_recipes
    catalog["revision"] += 1
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\nAñadidas {len(new)} receta(s): {len(all_recipes)} en total, revision {catalog['revision']}.")


def main(argv):
    if len(argv) >= 2 and argv[0] == "summary":
        summary(argv[1].upper())
    elif len(argv) >= 2 and argv[0] == "build":
        build(argv[1], "--write" in argv[2:])
    else:
        print(__doc__)
        sys.exit(2)


if __name__ == "__main__":
    main(sys.argv[1:])
