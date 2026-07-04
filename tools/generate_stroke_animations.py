#!/usr/bin/env python3
"""Generate timed stroke-order animation data for Hiragana Trace (Phase 2).

WHAT
----
For every character JSON listed in app/src/main/assets/hiragana/index.json,
this tool derives a `strokeAnimations` array from the existing `strokes[]`
SVG paths and writes it back into the same file. Each entry pairs a 1-based
`order` (matching the stroke's index+1 in `strokes[]`) with timed pen
positions:

    "strokeAnimations": [
      {
        "order": 1,
        "points": [
          { "x": 22.0, "y": 33.0, "time": 0 },
          { "x": 24.1, "y": 32.7, "time": 14 }
        ]
      }
    ]

Coordinates stay in the character's square `viewBox` space (0..viewBox, NOT
normalized 0..1) so animations, `strokes[]` geometry, and the app's renderers
share one coordinate system. `time` is milliseconds since the start of THAT
stroke (per-stroke clock), first point always 0, strictly increasing.
Full schema contract: docs/PHASE2-DESIGN.md section 1; generator spec:
section 4.

WHY
---
The animation engine (drawing/animation/StrokeAnimationEngine.kt) replays a
recorded writing rhythm rather than tweening each stroke over a fixed
duration, so long sweeping strokes take visibly longer than short ticks and
the demonstration reads like real handwriting. No human recording exists for
these glyphs, so the rhythm is synthesized here from stroke geometry with a
natural-writing model:

  - pen travels at a base speed of 160 viewBox-units/second,
  - every stroke lasts at least 180 ms (tiny ticks should still be legible),
  - a gentle ease-in/ease-out velocity profile (the pen accelerates from and
    settles into the paper instead of moving at constant speed; blended with
    a linear term so the velocity never reaches zero at the endpoints).

PIPELINE
--------
1. Parse each stroke's SVG path. Only ABSOLUTE `M`/`L`/`C`/`Q` commands are
   supported (all the bundled assets use `M`+`C`); anything else — including
   relative lowercase commands, `H`/`V`/`S`/`T`/`A`/`Z` — is a hard error so
   silently-wrong geometry can never ship.
2. Flatten Bezier segments into a dense polyline (~0.5-unit steps).
3. Resample the polyline by arc length to ~2 viewBox-unit spacing, clamped
   to 8..48 points per stroke.
4. Assign times: total duration = max(180 ms, length / 160 u/s); point i at
   arc-length fraction d gets time = duration * inverse_ease(d), rounded to
   whole ms and forced strictly increasing.
5. Rewrite the JSON in place: `strokeAnimations` replaced (or appended on
   first run), every other field byte-preserved semantically, key order kept
   stable, 2-space indent, UTF-8 without BOM, original newline style kept.
   The output is a pure function of `strokes[]`, so re-running is idempotent.
6. Self-validate by RE-READING the written files from disk (counts match
   `strokes[]`, orders are 1..N, first time 0, times strictly increasing,
   coordinates within [0, viewBox], 8..48 points/stroke) and print a
   per-character summary table. Any violation exits non-zero.

USAGE
-----
    py -3 tools/generate_stroke_animations.py            (Windows)
    python3 tools/generate_stroke_animations.py          (elsewhere)

No arguments; the asset directory is located relative to this script
(../app/src/main/assets/hiragana). Requires Python 3.8+, stdlib only.
`strokeAnimations` is derived data — never hand-edit it; re-run this tool.
"""

from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

# Windows consoles default to a legacy code page (e.g. cp1252) that cannot
# print hiragana; the summary table would crash the run after a successful
# write. Force UTF-8 (replace as a last resort) so output never kills the tool.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ASSETS_ROOT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
# One asset subdirectory per script; each has its own index.json. A directory
# that doesn't exist yet is skipped (see main()).
SCRIPT_DIRS = ("hiragana", "katakana")

SAMPLE_SPACING = 2.0        # target arc-length gap between emitted points (viewBox units)
MIN_POINTS = 8              # per stroke, after resampling
MAX_POINTS = 48
BASE_SPEED = 160.0          # pen speed, viewBox units per second
MIN_STROKE_DURATION_MS = 180.0
FLATTEN_STEP = 0.5          # dense-polyline step used before resampling (viewBox units)
EASE_STRENGTH = 0.6         # 0 = constant speed, 1 = full smoothstep (stops at endpoints)

_TOKEN_RE = re.compile(r"[A-Za-z]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")

# Command letter -> number of coordinate values per parameter group. Per the
# SVG spec a command's group may repeat (e.g. "C a b c d e f  g h i j k l"),
# and extra groups after M are implicit L; both are handled below.
_ARITY = {"M": 2, "L": 2, "C": 6, "Q": 4}


def _dist(a, b):
    return math.hypot(b[0] - a[0], b[1] - a[1])


def _cubic_point(p0, c1, c2, p1, t):
    mt = 1.0 - t
    return (
        mt * mt * mt * p0[0] + 3 * mt * mt * t * c1[0] + 3 * mt * t * t * c2[0] + t * t * t * p1[0],
        mt * mt * mt * p0[1] + 3 * mt * mt * t * c1[1] + 3 * mt * t * t * c2[1] + t * t * t * p1[1],
    )


def _quad_point(p0, c, p1, t):
    mt = 1.0 - t
    return (
        mt * mt * p0[0] + 2 * mt * t * c[0] + t * t * p1[0],
        mt * mt * p0[1] + 2 * mt * t * c[1] + t * t * p1[1],
    )


def flatten_stroke(path_data, label):
    """Parse one absolute M/L/C/Q SVG path string into a dense polyline.

    Beziers are sampled every ~FLATTEN_STEP units of control-polygon length,
    which over-samples slightly (control polygon >= arc length) — cheap and
    plenty for the ~2-unit resampling that follows. Hard-errors (SystemExit
    via fail()) on any unsupported or relative command so bad geometry is
    caught at generation time, not at runtime on a device.
    """
    tokens = _TOKEN_RE.findall(path_data)
    points = []
    pos = None
    cmd = None
    i = 0

    def take_numbers(count):
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

    while i < len(tokens):
        token = tokens[i]
        if token.isalpha():
            if token not in _ARITY:
                fail(
                    f"{label}: unsupported SVG command {token!r} in {path_data!r} "
                    "(only absolute M/L/C/Q are supported; relative/lowercase "
                    "and H/V/S/T/A/Z are not)"
                )
            cmd = token
            i += 1
            if cmd == "M":
                x, y = take_numbers(2)
                pos = (x, y)
                points = [pos]
                cmd = "L"  # SVG: further coordinate groups after M are implicit lineto
                continue
        if cmd is None or pos is None:
            fail(f"{label}: path must start with an absolute M command: {path_data!r}")
        if cmd == "L":
            x, y = take_numbers(2)
            points.append((x, y))
            pos = (x, y)
        elif cmd == "C":
            x1, y1, x2, y2, x, y = take_numbers(6)
            c1, c2, p1 = (x1, y1), (x2, y2), (x, y)
            est = _dist(pos, c1) + _dist(c1, c2) + _dist(c2, p1)
            steps = max(8, min(256, math.ceil(est / FLATTEN_STEP)))
            points.extend(_cubic_point(pos, c1, c2, p1, k / steps) for k in range(1, steps + 1))
            pos = p1
        elif cmd == "Q":
            x1, y1, x, y = take_numbers(4)
            c, p1 = (x1, y1), (x, y)
            est = _dist(pos, c) + _dist(c, p1)
            steps = max(8, min(256, math.ceil(est / FLATTEN_STEP)))
            points.extend(_quad_point(pos, c, p1, k / steps) for k in range(1, steps + 1))
            pos = p1
    if len(points) < 1:
        fail(f"{label}: path produced no points: {path_data!r}")
    return points


def resample_by_arc_length(polyline):
    """Return (points, total_length): points evenly spaced along the arc.

    Point count targets one sample per SAMPLE_SPACING units, clamped to
    MIN_POINTS..MAX_POINTS. A degenerate zero-length stroke (single point /
    dot) still emits MIN_POINTS coincident samples so the timing model and
    the engine's dot rendering have frames to work with.
    """
    cumulative = [0.0]
    for a, b in zip(polyline, polyline[1:]):
        cumulative.append(cumulative[-1] + _dist(a, b))
    total = cumulative[-1]
    count = max(MIN_POINTS, min(MAX_POINTS, int(round(total / SAMPLE_SPACING)) + 1))
    if total == 0.0:
        return [polyline[0]] * count, 0.0

    samples = []
    seg = 0
    for k in range(count):
        target = total * k / (count - 1)
        while seg < len(cumulative) - 2 and cumulative[seg + 1] < target:
            seg += 1
        seg_len = cumulative[seg + 1] - cumulative[seg]
        t = 0.0 if seg_len == 0.0 else (target - cumulative[seg]) / seg_len
        ax, ay = polyline[seg]
        bx, by = polyline[seg + 1]
        samples.append((ax + (bx - ax) * t, ay + (by - ay) * t))
    return samples, total


def _ease(u):
    """Distance fraction covered at normalized time u (gentle ease-in/out).

    A blend of linear motion and smoothstep: strictly increasing, endpoint
    velocity (1 - EASE_STRENGTH) of the average instead of zero, peak
    mid-stroke velocity ~1.3x average. Strict monotonicity is what keeps the
    inverted times strictly increasing.
    """
    return (1.0 - EASE_STRENGTH) * u + EASE_STRENGTH * (u * u * (3.0 - 2.0 * u))


def _ease_inverse(d):
    """Invert _ease by bisection (monotonic on [0, 1], so this always converges)."""
    lo, hi = 0.0, 1.0
    for _ in range(60):
        mid = (lo + hi) / 2.0
        if _ease(mid) < d:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2.0


def assign_times(count, length):
    """Millisecond timestamps for `count` points evenly spaced over `length`.

    Duration honours BASE_SPEED but never drops below MIN_STROKE_DURATION_MS.
    Times come from inverting the easing curve (uniform-distance points ->
    non-uniform times), rounded to ms, then nudged +1 ms where rounding
    collapsed neighbours so the strictly-increasing contract always holds.
    """
    duration = max(MIN_STROKE_DURATION_MS, 1000.0 * length / BASE_SPEED)
    times = [int(round(duration * _ease_inverse(k / (count - 1)))) for k in range(count)]
    times[0] = 0
    for k in range(1, count):
        if times[k] <= times[k - 1]:
            times[k] = times[k - 1] + 1
    return times


def build_stroke_animations(strokes, label):
    animations = []
    for index, path_data in enumerate(strokes):
        polyline = flatten_stroke(path_data, f"{label} stroke {index + 1}")
        samples, length = resample_by_arc_length(polyline)
        times = assign_times(len(samples), length)
        animations.append(
            {
                "order": index + 1,
                "points": [
                    {"x": round(x, 2), "y": round(y, 2), "time": t}
                    for (x, y), t in zip(samples, times)
                ],
            }
        )
    return animations


_POINT_OBJECT_RE = re.compile(
    r'\{\s*"x": ([-+0-9.eE]+),\s*"y": ([-+0-9.eE]+),\s*"time": (\d+)\s*\}'
)


def write_character_json(path, data, newline):
    """Serialize with 2-space indent, UTF-8 no BOM, one animation point per line.

    json.dumps would explode every {x, y, time} object onto five lines; the
    regex collapses exactly those objects back to one line each (nothing else
    in the schema matches the pattern), keeping the files reviewable. Key
    order is whatever the parsed dict carries — Python preserves document
    order, so untouched fields keep their position and byte content.
    """
    text = json.dumps(data, indent=2, ensure_ascii=False)
    text = _POINT_OBJECT_RE.sub(r'{ "x": \1, "y": \2, "time": \3 }', text)
    path.write_bytes((text + "\n").replace("\n", newline).encode("utf-8"))


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def validate_written_file(path):
    """Re-read `path` from disk and enforce the section-4 invariants.

    Returns summary rows for the report table. Validation deliberately parses
    the file again (strict JSON) instead of trusting in-memory state, so it
    also catches serialization bugs.
    """
    data = json.loads(path.read_text(encoding="utf-8"))
    view_box = float(data.get("viewBox", 100))
    strokes = data["strokes"]
    animations = data.get("strokeAnimations")
    label = f"{path.name} ({data.get('character', '?')})"

    if not isinstance(animations, list):
        fail(f"{label}: strokeAnimations missing or not an array after write")
    if len(animations) != len(strokes):
        fail(
            f"{label}: strokeAnimations has {len(animations)} entries "
            f"but strokes has {len(strokes)}"
        )

    rows = []
    for index, animation in enumerate(animations):
        stroke_label = f"{label} stroke {index + 1}"
        if animation["order"] != index + 1:
            fail(f"{stroke_label}: order is {animation['order']}, expected {index + 1}")
        points = animation["points"]
        if not (MIN_POINTS <= len(points) <= MAX_POINTS):
            fail(f"{stroke_label}: {len(points)} points, expected {MIN_POINTS}..{MAX_POINTS}")
        if points[0]["time"] != 0:
            fail(f"{stroke_label}: first time is {points[0]['time']}, expected 0")
        previous_time = -1
        for point in points:
            if point["time"] <= previous_time:
                fail(f"{stroke_label}: times not strictly increasing at t={point['time']}")
            previous_time = point["time"]
            if not (0.0 <= point["x"] <= view_box and 0.0 <= point["y"] <= view_box):
                fail(f"{stroke_label}: point ({point['x']}, {point['y']}) outside [0, {view_box}]")
        length = sum(
            math.hypot(b["x"] - a["x"], b["y"] - a["y"]) for a, b in zip(points, points[1:])
        )
        rows.append((index + 1, len(points), length, points[-1]["time"]))
    return data.get("character", "?"), data.get("romaji", "?"), rows


def generate_for_dir(asset_dir):
    """Writes + validates strokeAnimations for one asset directory's index.json.

    Returns the number of characters processed.
    """
    index = json.loads((asset_dir / "index.json").read_text(encoding="utf-8"))
    names = index["characters"]

    for name in names:
        path = asset_dir / f"{name}.json"
        raw = path.read_bytes()
        newline = "\r\n" if b"\r\n" in raw else "\n"
        data = json.loads(raw.decode("utf-8-sig"))
        data["strokeAnimations"] = build_stroke_animations(data["strokes"], path.name)
        write_character_json(path, data, newline)

    print(f"Wrote strokeAnimations for {len(names)} characters in '{asset_dir.name}'. Validating...\n")
    print(f"{'char':<6}{'romaji':<8}{'stroke':<8}{'points':<8}{'length':<10}{'duration'}")
    for name in names:
        character, romaji, rows = validate_written_file(asset_dir / f"{name}.json")
        for order, point_count, length, duration in rows:
            print(
                f"{character:<5}{romaji:<8}{order:<8}{point_count:<8}"
                f"{length:<10.1f}{duration} ms"
            )
        total = sum(duration for _, _, _, duration in rows)
        print(f"{'':<27}total animated {total} ms\n")
    return len(names)


def main():
    # One pass per script directory; each is driven by its own index.json. A
    # directory that doesn't exist yet is skipped, mirroring import_kanjivg.py.
    total = 0
    for script_dir in SCRIPT_DIRS:
        asset_dir = ASSETS_ROOT / script_dir
        if not asset_dir.is_dir():
            print(f"(skipping '{script_dir}': directory not found)\n")
            continue
        total += generate_for_dir(asset_dir)
    if total == 0:
        fail(f"no asset directories found under {ASSETS_ROOT}")
    print("All validations passed.")


if __name__ == "__main__":
    main()
