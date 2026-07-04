#!/usr/bin/env python3
"""Import KanjiVG stroke geometry into the Hiragana Trace character assets.

WHAT
----
For every character listed in each script directory's index.json
(app/src/main/assets/hiragana/ and app/src/main/assets/katakana/), this tool
reads the vendored KanjiVG SVG (tools/kanjivg/<codepoint>.svg), extracts the
per-stroke paths in stroke order, converts them to the app's strict path
dialect (ABSOLUTE M/L/C/Q only — the same contract enforced by
generate_stroke_animations.py), and rewrites the character JSON. YŌON ids
(きゃ, しゅ …; see YOON) have no single code point, so their geometry is
COMPOSED from two SVGs — the -i base kana scaled to the left plus a small
ゃ/ゅ/ょ scaled into the bottom-right (see compose_yoon):

  - `strokes[]`   one absolute path string per stroke, in stroke order
  - `svgPath`     the strokes joined with a single space (legacy field)
  - `viewBox`     109 (KanjiVG's coordinate space; renderers scale from it)
  - `strokeAnimations`  REMOVED — it is derived data and must be regenerated
    by running generate_stroke_animations.py immediately after this tool.
    Until then the app falls back to the static stroke-order diagram.

WHY
---
The original strokes[] were hand-authored approximations: recognizable but
proportioned wrong. KanjiVG (http://kanjivg.tagaini.net, Ulrich Apel,
CC BY-SA 3.0) provides calligraphically correct stroke geometry AND correct
stroke order for all kana. The vendored SVGs keep their license header;
user-facing attribution lives in README.md and docs/DATA.md.

USAGE
-----
    py -3 tools/import_kanjivg.py                (Windows)
    python3 tools/import_kanjivg.py              (elsewhere)
    ...then ALWAYS:
    py -3 tools/generate_stroke_animations.py

No arguments. Requires Python 3.8+, stdlib only. Idempotent.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

TOOLS_DIR = Path(__file__).resolve().parent
ASSETS_ROOT = TOOLS_DIR.parent / "app" / "src" / "main" / "assets"
# One asset subdirectory per script; each has its own index.json + character
# JSONs. A dir that doesn't exist yet is skipped (see main()).
SCRIPT_DIRS = ("hiragana", "katakana")
KANJIVG_DIR = TOOLS_DIR / "kanjivg"

VIEW_BOX = 109.0  # KanjiVG's fixed coordinate space ("0 0 109 109")

# Asset id (index.json basename) -> KanjiVG file (lowercase hex codepoint).
# Ids are unique across scripts (katakana ids are prefixed "kata_"), so one
# flat map serves every asset directory.
CODEPOINTS = {
    "a": "03042",  # あ
    "i": "03044",  # い
    "u": "03046",  # う
    "e": "03048",  # え
    "o": "0304a",  # お
    "ka": "0304b",  # か
    "ki": "0304d",  # き
    "ku": "0304f",  # く
    "ke": "03051",  # け
    "ko": "03053",  # こ
    "sa": "03055",  # さ
    "shi": "03057",  # し
    "su": "03059",  # す
    "se": "0305b",  # せ
    "so": "0305d",  # そ
    "ta": "0305f",  # た
    "chi": "03061",  # ち
    "tsu": "03064",  # つ
    "te": "03066",  # て
    "to": "03068",  # と
    "na": "0306a",  # な
    "ni": "0306b",  # に
    "nu": "0306c",  # ぬ
    "ne": "0306d",  # ね
    "no": "0306e",  # の
    "ha": "0306f",  # は
    "hi": "03072",  # ひ
    "fu": "03075",  # ふ
    "he": "03078",  # へ
    "ho": "0307b",  # ほ
    "ma": "0307e",  # ま
    "mi": "0307f",  # み
    "mu": "03080",  # む
    "me": "03081",  # め
    "mo": "03082",  # も
    "ya": "03084",  # や
    "yu": "03086",  # ゆ
    "yo": "03088",  # よ
    "ra": "03089",  # ら
    "ri": "0308a",  # り
    "ru": "0308b",  # る
    "re": "0308c",  # れ
    "ro": "0308d",  # ろ
    "wa": "0308f",  # わ
    "wo": "03092",  # を
    "n": "03093",  # ん
    # --- Katakana (assets/katakana/), ids prefixed "kata_" ---
    "kata_a": "030a2",  # ア
    "kata_i": "030a4",  # イ
    "kata_u": "030a6",  # ウ
    "kata_e": "030a8",  # エ
    "kata_o": "030aa",  # オ
    "kata_ka": "030ab",  # カ
    "kata_ki": "030ad",  # キ
    "kata_ku": "030af",  # ク
    "kata_ke": "030b1",  # ケ
    "kata_ko": "030b3",  # コ
    "kata_sa": "030b5",  # サ
    "kata_shi": "030b7",  # シ
    "kata_su": "030b9",  # ス
    "kata_se": "030bb",  # セ
    "kata_so": "030bd",  # ソ
    "kata_ta": "030bf",  # タ
    "kata_chi": "030c1",  # チ
    "kata_tsu": "030c4",  # ツ
    "kata_te": "030c6",  # テ
    "kata_to": "030c8",  # ト
    "kata_na": "030ca",  # ナ
    "kata_ni": "030cb",  # ニ
    "kata_nu": "030cc",  # ヌ
    "kata_ne": "030cd",  # ネ
    "kata_no": "030ce",  # ノ
    "kata_ha": "030cf",  # ハ
    "kata_hi": "030d2",  # ヒ
    "kata_fu": "030d5",  # フ
    "kata_he": "030d8",  # ヘ
    "kata_ho": "030db",  # ホ
    "kata_ma": "030de",  # マ
    "kata_mi": "030df",  # ミ
    "kata_mu": "030e0",  # ム
    "kata_me": "030e1",  # メ
    "kata_mo": "030e2",  # モ
    "kata_ya": "030e4",  # ヤ
    "kata_yu": "030e6",  # ユ
    "kata_yo": "030e8",  # ヨ
    "kata_ra": "030e9",  # ラ
    "kata_ri": "030ea",  # リ
    "kata_ru": "030eb",  # ル
    "kata_re": "030ec",  # レ
    "kata_ro": "030ed",  # ロ
    "kata_wa": "030ef",  # ワ
    "kata_wo": "030f2",  # ヲ
    "kata_n": "030f3",  # ン
    "kata_choonpu": "030fc",  # ー (long-vowel mark / chōonpu)
    # --- Hiragana dakuten (゛) + handakuten (゜); opt-in in the learning flow ---
    "ga": "0304c", "gi": "0304e", "gu": "03050", "ge": "03052", "go": "03054",
    "za": "03056", "ji": "03058", "zu": "0305a", "ze": "0305c", "zo": "0305e",
    "da": "03060", "di": "03062", "du": "03065", "de": "03067", "do": "03069",
    "ba": "03070", "bi": "03073", "bu": "03076", "be": "03079", "bo": "0307c",
    "pa": "03071", "pi": "03074", "pu": "03077", "pe": "0307a", "po": "0307d",
    # --- Katakana dakuten + handakuten ---
    "kata_ga": "030ac", "kata_gi": "030ae", "kata_gu": "030b0", "kata_ge": "030b2", "kata_go": "030b4",
    "kata_za": "030b6", "kata_ji": "030b8", "kata_zu": "030ba", "kata_ze": "030bc", "kata_zo": "030be",
    "kata_da": "030c0", "kata_di": "030c2", "kata_du": "030c5", "kata_de": "030c7", "kata_do": "030c9",
    "kata_ba": "030d0", "kata_bi": "030d3", "kata_bu": "030d6", "kata_be": "030d9", "kata_bo": "030dc",
    "kata_pa": "030d1", "kata_pi": "030d4", "kata_pu": "030d7", "kata_pe": "030da", "kata_po": "030dd",
    # --- Extended: small tsu, small vowels, vu, obsolete kana (opt-in) ---
    "sokuon": "03063",  # っ
    "small_a": "03041", "small_i": "03043", "small_u": "03045", "small_e": "03047", "small_o": "03049",
    "vu": "03094",  # ゔ (classifies as dakuten, gated by the voiced opt-in)
    "wi": "03090", "we": "03091",  # ゐ ゑ (obsolete)
    "kata_sokuon": "030c3",  # ッ
    "kata_small_a": "030a1", "kata_small_i": "030a3", "kata_small_u": "030a5",
    "kata_small_e": "030a7", "kata_small_o": "030a9",
    "kata_vu": "030f4",  # ヴ
    "kata_wi": "030f0", "kata_we": "030f1",  # ヰ ヱ (obsolete)
}

# ---- Yōon (contracted syllables: きゃ, しゅ …) --------------------------------
# A yōon has NO single Unicode code point, so KanjiVG has no single file for it.
# We COMPOSE its geometry from two vendored SVGs: the -i base kana (scaled down,
# placed left) + a small ゃ/ゅ/ょ (scaled smaller, placed bottom-right), matching
# how a yōon is written in one cell. YOON maps each yōon id to
# (base_codepoint, small_kana_codepoint); ids are built from the -i base kana ×
# {ya→a, yu→u, yo→o}.
_SMALL_HIRA = {"a": "03083", "u": "03085", "o": "03087"}  # ゃ ゅ ょ
_SMALL_KATA = {"a": "030e3", "u": "030e5", "o": "030e7"}  # ャ ュ ョ
# (id prefix, hiragana -i base codepoint, katakana -i base codepoint)
_YOON_BASES = [
    ("ky", "0304d", "030ad"),  # き / キ
    ("sh", "03057", "030b7"),  # し / シ  -> sha/shu/sho
    ("ch", "03061", "030c1"),  # ち / チ  -> cha/chu/cho
    ("ny", "0306b", "030cb"),  # に / ニ
    ("hy", "03072", "030d2"),  # ひ / ヒ
    ("my", "0307f", "030df"),  # み / ミ
    ("ry", "0308a", "030ea"),  # り / リ
    ("gy", "0304e", "030ae"),  # ぎ / ギ (voiced base)
    ("j", "03058", "030b8"),   # じ / ジ  -> ja/ju/jo
    ("by", "03073", "030d3"),  # び / ビ
    ("py", "03074", "030d4"),  # ぴ / ピ (semi-voiced base)
]


def _build_yoon():
    table = {}
    for prefix, hira_base, kata_base in _YOON_BASES:
        for vowel in ("a", "u", "o"):
            table[f"{prefix}{vowel}"] = (hira_base, _SMALL_HIRA[vowel])
            table[f"kata_{prefix}{vowel}"] = (kata_base, _SMALL_KATA[vowel])
    return table


YOON = _build_yoon()

# ---- Extended katakana (foreign-sound combos: ファ, ティ …) -------------------
# Same composition shape as yōon (base + small kana), but the small kana is a
# small VOWEL (ァ/ィ/ゥ/ェ/ォ). id = kata_<base-id>_<vowel> (self-documenting,
# collision-free with the single-glyph dakuten ids like kata_di / kata_du).
# (base codepoint, small-vowel codepoint)
EXTENDED = {
    "kata_u_i": ("030a6", "030a3"), "kata_u_e": ("030a6", "030a7"), "kata_u_o": ("030a6", "030a9"),  # ウィ ウェ ウォ
    "kata_vu_a": ("030f4", "030a1"), "kata_vu_i": ("030f4", "030a3"),
    "kata_vu_e": ("030f4", "030a7"), "kata_vu_o": ("030f4", "030a9"),  # ヴァ ヴィ ヴェ ヴォ
    "kata_shi_e": ("030b7", "030a7"), "kata_ji_e": ("030b8", "030a7"), "kata_chi_e": ("030c1", "030a7"),  # シェ ジェ チェ
    "kata_te_i": ("030c6", "030a3"), "kata_de_i": ("030c7", "030a3"),  # ティ ディ
    "kata_to_u": ("030c8", "030a5"), "kata_do_u": ("030c9", "030a5"),  # トゥ ドゥ
    "kata_fu_a": ("030d5", "030a1"), "kata_fu_i": ("030d5", "030a3"),
    "kata_fu_e": ("030d5", "030a7"), "kata_fu_o": ("030d5", "030a9"),  # ファ フィ フェ フォ
    "kata_tsu_a": ("030c4", "030a1"), "kata_tsu_i": ("030c4", "030a3"),
    "kata_tsu_e": ("030c4", "030a7"), "kata_tsu_o": ("030c4", "030a9"),  # ツァ ツィ ツェ ツォ
}

# All composite characters (2 code points, no single KanjiVG file) — yōon +
# extended katakana. compose_composite() builds their strokes from the parts.
COMPOSITES = {**YOON, **EXTENDED}

# Affine placement within the 109 box: base kana scaled to the left ~62%, small
# kana scaled to ~40% in the bottom-right. Tuned so all composed coordinates
# stay inside [0, VIEW_BOX] (validated downstream like every other stroke).
YOON_BASE = (0.62, 3.0, 20.0)   # (scale, translate-x, translate-y)
YOON_SMALL = (0.40, 60.0, 52.0)

_TOKEN_RE = re.compile(r"[A-Za-z]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
_PATH_RE = re.compile(r'<path[^>]*\bid="kvg:[0-9a-f]+-s(\d+)"[^>]*\bd="([^"]+)"')


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def fmt(value):
    """Compact 2-decimal formatting: 31.0 -> '31', 31.25 -> '31.25'."""
    rounded = round(value, 2)
    if rounded == int(rounded):
        return str(int(rounded))
    return f"{rounded:.2f}".rstrip("0")


def extract_stroke_paths(svg_text, label):
    """KanjiVG path data strings ordered by their -s<N> stroke number."""
    matches = _PATH_RE.findall(svg_text)
    if not matches:
        fail(f"{label}: no kvg stroke paths found")
    ordered = sorted(matches, key=lambda m: int(m[0]))
    numbers = [int(m[0]) for m in ordered]
    if numbers != list(range(1, len(numbers) + 1)):
        fail(f"{label}: stroke numbers {numbers} are not exactly 1..N")
    return [m[1] for m in ordered]


def to_absolute(path_data, label):
    """Convert one SVG path to the app dialect: absolute M/L/C/Q commands.

    Handles the command set KanjiVG kana actually use (M + relative c) plus
    the near neighbours that could appear in future imports (m/L/l/H/h/V/v/
    C/S/s/Q/q). Anything else — arcs, closepath, T — is a hard error so
    silently-wrong geometry can never ship (same policy as the generator).
    """
    tokens = _TOKEN_RE.findall(path_data)
    out = []
    cur = None
    last_c2 = None  # last cubic's second control point, for S/s reflection
    cmd = None
    i = 0

    def take(count):
        nonlocal i
        if i + count > len(tokens):
            fail(f"{label}: command '{cmd}' is missing coordinates in {path_data!r}")
        values = []
        for tok in tokens[i : i + count]:
            try:
                values.append(float(tok))
            except ValueError:
                fail(f"{label}: expected a number, got {tok!r} in {path_data!r}")
        i += count
        return values

    def emit_cubic(c1, c2, p):
        nonlocal cur, last_c2
        out.append(
            "C "
            + " ".join(fmt(v) for v in (c1[0], c1[1], c2[0], c2[1], p[0], p[1]))
        )
        cur = p
        last_c2 = c2

    def emit_line(p):
        nonlocal cur, last_c2
        out.append(f"L {fmt(p[0])} {fmt(p[1])}")
        cur = p
        last_c2 = None

    while i < len(tokens):
        token = tokens[i]
        if token.isalpha():
            cmd = token
            i += 1
            if cmd in ("M", "m"):
                x, y = take(2)
                cur = (x, y) if cmd == "M" or cur is None else (cur[0] + x, cur[1] + y)
                out.append(f"M {fmt(cur[0])} {fmt(cur[1])}")
                last_c2 = None
                # Further coordinate groups after a moveto are implicit linetos.
                cmd = "L" if cmd == "M" else "l"
                continue
        if cur is None:
            fail(f"{label}: path must start with a moveto: {path_data!r}")
        if cmd in ("C", "c"):
            x1, y1, x2, y2, x, y = take(6)
            if cmd == "C":
                emit_cubic((x1, y1), (x2, y2), (x, y))
            else:
                emit_cubic(
                    (cur[0] + x1, cur[1] + y1),
                    (cur[0] + x2, cur[1] + y2),
                    (cur[0] + x, cur[1] + y),
                )
        elif cmd in ("S", "s"):
            x2, y2, x, y = take(4)
            c1 = (2 * cur[0] - last_c2[0], 2 * cur[1] - last_c2[1]) if last_c2 else cur
            if cmd == "S":
                emit_cubic(c1, (x2, y2), (x, y))
            else:
                emit_cubic(c1, (cur[0] + x2, cur[1] + y2), (cur[0] + x, cur[1] + y))
        elif cmd in ("Q", "q"):
            x1, y1, x, y = take(4)
            if cmd == "Q":
                c, p = (x1, y1), (x, y)
            else:
                c, p = (cur[0] + x1, cur[1] + y1), (cur[0] + x, cur[1] + y)
            out.append("Q " + " ".join(fmt(v) for v in (c[0], c[1], p[0], p[1])))
            cur = p
            last_c2 = None
        elif cmd in ("L", "l"):
            x, y = take(2)
            emit_line((x, y) if cmd == "L" else (cur[0] + x, cur[1] + y))
        elif cmd in ("H", "h"):
            (x,) = take(1)
            emit_line((x, cur[1]) if cmd == "H" else (cur[0] + x, cur[1]))
        elif cmd in ("V", "v"):
            (y,) = take(1)
            emit_line((cur[0], y) if cmd == "V" else (cur[0], cur[1] + y))
        else:
            fail(
                f"{label}: unsupported SVG command {cmd!r} in {path_data!r} "
                "(supported: M/m implicit-lineto, L/l, H/h, V/v, C/c, S/s, Q/q)"
            )
    if len(out) < 2:
        fail(f"{label}: path has a moveto but no drawing commands: {path_data!r}")
    return " ".join(out)


def validate_coordinates(path_string, label):
    """All emitted coordinates must sit inside [0, VIEW_BOX] (validator contract)."""
    for tok in _TOKEN_RE.findall(path_string):
        if not tok.isalpha():
            value = float(tok)
            if not (0.0 <= value <= VIEW_BOX):
                fail(f"{label}: coordinate {value} outside [0, {VIEW_BOX}]")


def transform_path(path_string, scale, tx, ty):
    """Applies an affine (uniform scale + translate) to every coordinate of an
    absolute M/L/C/Q path — all commands carry only absolute (x, y) pairs."""
    pairs_per_command = {"M": 1, "L": 1, "C": 3, "Q": 2}
    tokens = path_string.split()
    out = []
    i = 0
    while i < len(tokens):
        cmd = tokens[i]
        out.append(cmd)
        i += 1
        for _ in range(pairs_per_command[cmd]):
            x = float(tokens[i]) * scale + tx
            y = float(tokens[i + 1]) * scale + ty
            out.append(fmt(x))
            out.append(fmt(y))
            i += 2
    return " ".join(out)


def compose_composite(name, label):
    """Composite strokes (yōon or extended katakana): base kana strokes
    (transformed to the left) followed by small-kana strokes (transformed to
    the bottom-right)."""
    base_cp, small_cp = COMPOSITES[name]
    base = read_strokes(base_cp, f"{label} base")
    small = read_strokes(small_cp, f"{label} small")
    base_strokes = [transform_path(s, *YOON_BASE) for s in base]
    small_strokes = [transform_path(s, *YOON_SMALL) for s in small]
    return base_strokes + small_strokes


def read_strokes(codepoint, label):
    """Absolute M/L/C/Q strokes for one vendored KanjiVG codepoint, in order."""
    svg_path = KANJIVG_DIR / f"{codepoint}.svg"
    if not svg_path.is_file():
        fail(f"missing vendored SVG: {svg_path}")
    raw = extract_stroke_paths(svg_path.read_text(encoding="utf-8"), label)
    return [to_absolute(d, f"{label} stroke {k + 1}") for k, d in enumerate(raw)]


def rewrite_character_json(path, strokes):
    raw = path.read_bytes()
    newline = "\r\n" if b"\r\n" in raw else "\n"
    data = json.loads(raw.decode("utf-8-sig"))
    data["viewBox"] = int(VIEW_BOX)
    data["strokes"] = strokes
    data["svgPath"] = " ".join(strokes)
    # Derived data: stale after a geometry change; regenerated by
    # generate_stroke_animations.py. Dropping it keeps the intermediate
    # state safe (the app falls back to the static diagram).
    data.pop("strokeAnimations", None)
    text = json.dumps(data, indent=2, ensure_ascii=False)
    path.write_bytes((text + "\n").replace("\n", newline).encode("utf-8"))


def import_script(asset_dir):
    """Imports every character listed in one asset directory's index.json.

    Single-codepoint characters come straight from CODEPOINTS; composite ids
    (yōon + extended katakana, in COMPOSITES) are built from two SVGs (see
    [compose_composite])."""
    index = json.loads((asset_dir / "index.json").read_text(encoding="utf-8"))
    names = index["characters"]
    for name in names:
        if name in COMPOSITES:
            label = f"{name} (composite)"
            strokes = compose_composite(name, label)
            source = "composed"
        elif name in CODEPOINTS:
            codepoint = CODEPOINTS[name]
            label = f"{name} ({codepoint}.svg)"
            strokes = read_strokes(codepoint, label)
            source = f"{codepoint}.svg"
        else:
            fail(f"no KanjiVG codepoint mapping for character id {name!r}")

        for k, stroke in enumerate(strokes):
            validate_coordinates(stroke, f"{label} stroke {k + 1}")

        json_path = asset_dir / f"{name}.json"
        character = json.loads(json_path.read_text(encoding="utf-8-sig"))["character"]
        rewrite_character_json(json_path, strokes)
        commands = sum(len(re.findall(r"[A-Za-z]", s)) for s in strokes)
        print(f"{character:<5}{source:<12}{len(strokes):<9}{commands}")


def main():
    if not KANJIVG_DIR.is_dir():
        fail(f"kanjivg directory not found: {KANJIVG_DIR}")

    # One pass per script directory; each is driven by its own index.json. A
    # directory that doesn't exist yet is skipped, so this stays runnable
    # while a new script's assets are still being authored.
    print(f"{'char':<6}{'file':<12}{'strokes':<9}{'commands'}")
    for script_dir in SCRIPT_DIRS:
        asset_dir = ASSETS_ROOT / script_dir
        if not asset_dir.is_dir():
            print(f"(skipping '{script_dir}': directory not found)")
            continue
        import_script(asset_dir)

    print(
        "\nDone. strokeAnimations was removed from all files — now run:\n"
        "    py -3 tools/generate_stroke_animations.py"
    )


if __name__ == "__main__":
    main()
