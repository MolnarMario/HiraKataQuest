#!/usr/bin/env python3
"""Render the mnemonic overlay drawings over their real kana strokes.

WHAT
----
The mnemonic feature (domain/model/KanaMnemonics.kt) lets a kana carry a small
hand-authored "overlay": accent path strokes drawn OVER the grey glyph so the
memory image (a barb turning し into a fishhook, a ring closing の into a
no-entry sign) sits on the kana's real shape. Overlay paths live in the
shipped Kotlin — the single source of truth — wrapped in marker comments:

    // OVERLAY:BEGIN shi
    OverlayPath("M 62 88 L 74 80"),
    OverlayPath("M 30 12 C 32 10 36 10 38 12", filled = true),
    // OVERLAY:END shi

This tool extracts those blocks, validates every path, and writes a
self-contained HTML contact sheet (tools/overlay_review/contact_sheet.html,
gitignored) that renders each kana's real KanjiVG strokes in warm grey with
its overlay in vermilion — the authoring loop is: edit the .kt, re-run this,
refresh the browser.

WHY REGEX-EXTRACTION AND NOT A SIDECAR FILE
-------------------------------------------
A sidecar JSON used only during authoring drifts the first time someone edits
the Kotlin directly, and nothing would catch it. Extracting from the .kt means
the contact sheet always renders exactly what ships. Fragility is contained by
a strict declaration contract plus hard failure: every line between BEGIN/END
must be exactly one `OverlayPath("...")` literal (optionally `, filled = true`)
— a BEGIN without END, an id with no asset JSON, an empty block, or any
unparseable line exits non-zero. Silent drift is impossible.

VALIDATION (mirrors KanaMnemonicsTest's pure validator)
------------------------------------------------------
- absolute M/L/C/Q/Z commands only (relative/H/V/S/T/A rejected),
- one command letter per segment with exact arity (M/L=2, C=6, Q=4, Z=0),
- first command M, all numbers finite,
- coordinates within -2..111 (the 109 viewBox with a 2-unit tolerance),
- at most 6 paths per kana, each path at least 2 commands unless it closes
  (a filled dot like "M .. C .. Z" is fine).

LICENSING NOTE
--------------
Overlay drawings must be ORIGINAL art positioned relative to the KanjiVG
geometry — never trace a KanjiVG stroke, or the drawing inherits CC BY-SA.
The contact sheet exists precisely so overlays can be authored against the
real strokes without copying them.

USAGE
-----
    py -3 tools/render_mnemonic_overlays.py            (Windows)
    python3 tools/render_mnemonic_overlays.py          (elsewhere)

No arguments; paths are resolved relative to this script. Python 3.8+,
stdlib only. Exits non-zero on any validation failure.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
KOTLIN_FILE = (
    ROOT / "app" / "src" / "main" / "java" / "com" / "hive" / "hiragana"
    / "domain" / "model" / "KanaMnemonics.kt"
)
ASSETS_HIRAGANA = ROOT / "app" / "src" / "main" / "assets" / "hiragana"
OUT_DIR = ROOT / "tools" / "overlay_review"
OUT_FILE = OUT_DIR / "contact_sheet.html"

GLYPH_COLOR = "#6B7280"      # presentation.theme.DiagramStroke
ACCENT_COLOR = "#BC3F34"     # presentation.theme.Vermilion
COORD_MIN, COORD_MAX = -2.0, 111.0
MAX_PATHS_PER_KANA = 6

BEGIN_RE = re.compile(r"^\s*//\s*OVERLAY:BEGIN\s+(\S+)\s*$")
END_RE = re.compile(r"^\s*//\s*OVERLAY:END\s+(\S+)\s*$")
PATH_LINE_RE = re.compile(
    r'^\s*OverlayPath\(\s*"([^"]+)"\s*(?:,\s*filled\s*=\s*(true|false)\s*)?\)\s*,?\s*$'
)
TOKEN_RE = re.compile(r"[A-Za-z]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARITY = {"M": 2, "L": 2, "C": 6, "Q": 4, "Z": 0}


def fail(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def extract_overlays(kt_text: str) -> "dict[str, list[tuple[str, bool]]]":
    """id -> [(path, filled)], honoring the strict marker contract."""
    overlays: dict[str, list[tuple[str, bool]]] = {}
    current_id: str | None = None
    for lineno, line in enumerate(kt_text.splitlines(), start=1):
        begin = BEGIN_RE.match(line)
        end = END_RE.match(line)
        if begin:
            if current_id is not None:
                fail(f"line {lineno}: OVERLAY:BEGIN {begin.group(1)} while "
                     f"{current_id}'s block is still open (missing END)")
            current_id = begin.group(1)
            if current_id in overlays:
                fail(f"line {lineno}: duplicate OVERLAY block for '{current_id}'")
            overlays[current_id] = []
        elif end:
            if current_id is None:
                fail(f"line {lineno}: OVERLAY:END {end.group(1)} without a BEGIN")
            if end.group(1) != current_id:
                fail(f"line {lineno}: OVERLAY:END {end.group(1)} does not match "
                     f"open block '{current_id}'")
            if not overlays[current_id]:
                fail(f"line {lineno}: OVERLAY block '{current_id}' contains no paths")
            current_id = None
        elif current_id is not None:
            m = PATH_LINE_RE.match(line)
            if not m:
                fail(f"line {lineno}: unparseable line inside OVERLAY block "
                     f"'{current_id}' (expected one OverlayPath(\"...\") literal "
                     f"per line): {line.strip()!r}")
            overlays[current_id].append((m.group(1), m.group(2) == "true"))
    if current_id is not None:
        fail(f"OVERLAY block '{current_id}' was never closed (missing END)")
    return overlays


def validate_path(kana_id: str, index: int, d: str) -> "list[str]":
    """Returns a list of problems (empty = valid)."""
    problems: list[str] = []
    tokens = TOKEN_RE.findall(d)
    if not tokens:
        return [f"{kana_id} path {index}: empty path"]
    if tokens[0] != "M":
        problems.append(f"{kana_id} path {index}: first command must be M")
    commands = 0
    i = 0
    while i < len(tokens):
        cmd = tokens[i]
        if len(cmd) != 1 or not cmd.isalpha():
            problems.append(
                f"{kana_id} path {index}: expected a command letter, got {cmd!r}")
            break
        if cmd not in ARITY:
            problems.append(
                f"{kana_id} path {index}: command {cmd!r} not allowed "
                f"(absolute M/L/C/Q/Z only)")
            break
        commands += 1
        i += 1
        ok = True
        for _ in range(ARITY[cmd]):
            if i >= len(tokens):
                problems.append(
                    f"{kana_id} path {index}: missing coordinate after {cmd}")
                ok = False
                break
            try:
                v = float(tokens[i])
            except ValueError:
                problems.append(
                    f"{kana_id} path {index}: {tokens[i]!r} is not a number")
                ok = False
                break
            if not (COORD_MIN <= v <= COORD_MAX):
                problems.append(
                    f"{kana_id} path {index}: coordinate {v} outside "
                    f"{COORD_MIN}..{COORD_MAX}")
            i += 1
        if not ok:
            break
    closed = "Z" in tokens
    if commands < 2 and not closed:
        problems.append(
            f"{kana_id} path {index}: only {commands} command(s) — a path needs "
            f"at least 2 unless it is a closed dot (…Z)")
    return problems


def load_character(kana_id: str) -> "tuple[list[str], float, str]":
    path = ASSETS_HIRAGANA / f"{kana_id}.json"
    if not path.is_file():
        fail(f"OVERLAY block '{kana_id}' has no asset JSON at {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    return data["strokes"], float(data.get("viewBox", 109)), data["character"]


def svg_cell(kana_id: str, glyph: str, strokes: "list[str]", view_box: float,
             overlay: "list[tuple[str, bool]]", ok: bool) -> str:
    stroke_elems = "\n".join(
        f'      <path d="{d}" fill="none" stroke="{GLYPH_COLOR}" stroke-width="3" '
        f'stroke-linecap="round" stroke-linejoin="round"/>'
        for d in strokes
    )
    overlay_elems = "\n".join(
        (
            f'      <path d="{d}" fill="{ACCENT_COLOR}" stroke="none"/>'
            if filled else
            f'      <path d="{d}" fill="none" stroke="{ACCENT_COLOR}" '
            f'stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>'
        )
        for d, filled in overlay
    )
    grid_lines = "\n".join(
        f'      <line x1="{v}" y1="0" x2="{v}" y2="{view_box:g}"/>'
        f'<line x1="0" y1="{v}" x2="{view_box:g}" y2="{v}"/>'
        for v in range(10, int(view_box), 10)
    )
    status = "PASS" if ok else "FAIL"
    return f"""  <figure class="cell {'pass' if ok else 'fail'}">
    <svg viewBox="0 0 {view_box:g} {view_box:g}">
      <g class="grid">
{grid_lines}
      </g>
{stroke_elems}
{overlay_elems}
    </svg>
    <figcaption><b>{glyph}</b> {kana_id} · {len(overlay)} path(s) ·
      <span class="status">{status}</span></figcaption>
  </figure>"""


HTML_SHELL = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Mnemonic overlay contact sheet</title>
<style>
  body {{ font-family: system-ui, sans-serif; background: #FBF8F4; color: #20222E;
         margin: 24px; }}
  h1 {{ font-size: 20px; }}
  .cells {{ display: flex; flex-wrap: wrap; gap: 20px; }}
  .cell {{ margin: 0; padding: 12px; background: #fff; border-radius: 12px;
          box-shadow: 0 1px 4px rgba(0,0,0,.12); width: 220px;
          transition: transform .15s; }}
  .cell:hover {{ transform: scale(1.6); z-index: 2; position: relative; }}
  .cell svg {{ width: 100%; height: auto; display: block; }}
  .cell.fail {{ outline: 3px solid #B3261E; }}
  figcaption {{ font-size: 13px; margin-top: 6px; text-align: center; }}
  .pass .status {{ color: #1B5E20; }} .fail .status {{ color: #B3261E; }}
  .grid {{ stroke: #C7CFD8; stroke-width: .5; visibility: hidden; }}
  body.show-grid .grid {{ visibility: visible; }}
  footer {{ margin-top: 28px; font-size: 12px; color: #6B7280; }}
</style>
</head>
<body>
<h1>Mnemonic overlay contact sheet</h1>
<p><label><input type="checkbox" onchange="document.body.classList.toggle('show-grid', this.checked)">
  Show 10-unit coordinate grid</label></p>
<div class="cells">
{cells}
</div>
<footer>Grey strokes are the app's real glyph geometry, derived from
<a href="https://kanjivg.tagaini.net/">KanjiVG</a> (CC BY-SA); vermilion
overlay paths are original HiraKataQuest art. Generated by
tools/render_mnemonic_overlays.py — edit KanaMnemonics.kt and re-run.</footer>
</body>
</html>
"""


def main() -> None:
    if not KOTLIN_FILE.is_file():
        fail(f"missing {KOTLIN_FILE}")
    overlays = extract_overlays(KOTLIN_FILE.read_text(encoding="utf-8"))

    if not overlays:
        print("No OVERLAY blocks found in KanaMnemonics.kt — nothing to render yet.")
        print("(Author overlays between // OVERLAY:BEGIN <id> and // OVERLAY:END <id>.)")
        return

    cells: list[str] = []
    all_problems: list[str] = []
    print(f"{'id':<8} {'glyph':<5} {'paths':>5}  status")
    print("-" * 34)
    for kana_id, paths in overlays.items():
        strokes, view_box, glyph = load_character(kana_id)
        problems: list[str] = []
        if len(paths) > MAX_PATHS_PER_KANA:
            problems.append(f"{kana_id}: {len(paths)} paths (max {MAX_PATHS_PER_KANA})")
        for i, (d, _filled) in enumerate(paths):
            problems.extend(validate_path(kana_id, i, d))
        ok = not problems
        all_problems.extend(problems)
        cells.append(svg_cell(kana_id, glyph, strokes, view_box, paths, ok))
        print(f"{kana_id:<8} {glyph:<5} {len(paths):>5}  {'PASS' if ok else 'FAIL'}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(HTML_SHELL.format(cells="\n".join(cells)), encoding="utf-8")
    print(f"\nContact sheet: {OUT_FILE}")

    if all_problems:
        print("\nVALIDATION FAILURES:", file=sys.stderr)
        for p in all_problems:
            print(f"  - {p}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
