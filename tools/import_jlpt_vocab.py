# -*- coding: utf-8 -*-
"""Enrich the hiragana vocabulary deck from the JLPT N5 usage CSV.

Source of truth: jlpt_n5_vocab_full_usage.csv (columns:
kanji, hiragana, romaji, english, category, part_of_speech, kanji_usage).

For every hiragana-track CSV row we build reading -> {kanji, category, kanji_usage}.
Then for each existing word in assets/hiragana/vocabulary.json we BACKFILL:
  - kanji        (only when the CSV's 1st column has a Han ideograph or 々)
  - category     (the CSV category, e.g. "family")
  - kanji_usage  (usually_kanji | mixed | usually_kana) -- only emitted when kanji present
and we APPEND any CSV hiragana words not already in the deck.

Katakana loanwords (1st column all-katakana) are ignored: the katakana deck is
untouched, and the category/kanji features are hiragana-only.

Re-runnable: enrichment is keyed by reading and overwrites the three derived
fields, so running it again is a no-op. Usage:  python tools/import_jlpt_vocab.py
"""
import csv, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV = os.environ.get("JLPT_CSV", r"C:\Users\Mario\Downloads\jlpt_n5_vocab_full_usage.csv")
HIRA = os.path.join(ROOT, "app", "src", "main", "assets", "hiragana", "vocabulary.json")
KATA = os.path.join(ROOT, "app", "src", "main", "assets", "katakana", "vocabulary.json")


def is_katakana(c):
    return "\u30a0" <= c <= "\u30ff"


def is_hiragana(c):
    return "\u3041" <= c <= "\u309f"


def is_han(c):
    return "\u4e00" <= c <= "\u9fff" or c == "\u3005"  # incl. 々 iteration mark


def all_katakana(s):
    return len(s) > 0 and all(is_katakana(c) for c in s)


def pure_hiragana(s):
    return len(s) > 0 and all(is_hiragana(c) for c in s)


def primary_reading(hira_col):
    return hira_col.split("/")[0].strip()  # "きゅう / く" -> "きゅう"


def to_katakana(s):
    return "".join(
        chr(ord(c) + 0x60) if "ぁ" <= c <= "ゖ" else c for c in s
    )


def katakana_deck_readings():
    """Readings already covered (as katakana) by the katakana deck.

    The newest CSV lists a few loanwords (bread/TV/bus) as plain hiragana rows,
    but they are really katakana words and the katakana deck already carries
    them as パン/テレビ/バス. We drop the hiragana duplicates so bread isn't
    "practiced" as ぱん in the hiragana track.
    """
    with open(KATA, encoding="utf-8") as f:
        return {w["kana"] for w in json.load(f)["words"]}


def build_csv_map():
    """Returns (info, order, forms).

    info[reading]   = the first CSV occurrence (meaning/category for kana-only
                      readings; also the appended-word default).
    order           = readings in first-seen order (for the append pass).
    forms[reading]  = every DISTINCT-kanji row for that reading, in CSV order:
                      {kanji, meaning, category, kanji_usage}. This is what lets
                      us pick a meaning-matched primary and list the alternates
                      for homophones (はな -> 花 flower / 鼻 nose).
    """
    info = {}
    order = []
    forms = {}
    with open(CSV, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            kanji_col = (row["kanji"] or "").strip()
            if all_katakana(kanji_col):
                continue  # katakana loanword -> ignored
            reading = primary_reading((row["hiragana"] or "").strip())
            meaning = (row["english"] or "").strip()
            if not pure_hiragana(reading) or not meaning:
                continue
            if reading not in info:
                info[reading] = {
                    "category": (row["category"] or "").strip() or None,
                    "meaning": meaning,
                }
                order.append(reading)
            if any(is_han(c) for c in kanji_col):
                bucket = forms.setdefault(reading, [])
                if all(f["kanji"] != kanji_col for f in bucket):
                    bucket.append({
                        "kanji": kanji_col,
                        "meaning": meaning,
                        "category": (row["category"] or "").strip() or None,
                        "kanji_usage": (row["kanji_usage"] or "").strip() or None,
                    })
    return info, order, forms


def _norm(s):
    """Loose meaning key: lowercase, drop parentheticals + punctuation."""
    s = re.sub(r"\([^)]*\)", " ", s.lower())
    return re.sub(r"[^a-z ]+", " ", s).split()


def pick_primary(reading_forms, target_meaning):
    """The kanji form whose meaning best matches [target_meaning].

    Exact word-set match wins, then any token overlap, else CSV order. Used so a
    homophone's shown kanji matches the word's meaning (はな 'flower' -> 花).
    """
    if not reading_forms:
        return None
    if not target_meaning:
        return reading_forms[0]
    target = set(_norm(target_meaning))

    def score(f):
        words = set(_norm(f["meaning"]))
        if words == target:
            return 2
        return 1 if words & target else 0

    best = max(range(len(reading_forms)), key=lambda i: (score(reading_forms[i]), -i))
    return reading_forms[best]


def build_entry(kana, meaning, reading_forms, fallback_category=None):
    """A deck entry: meaning-matched primary kanji + any 'other kanji' alts.

    Field order kept readable: kana, meaning, kanji, category, kanji_usage,
    other_kanji. The category comes from the primary kanji form (so a homophone's
    group matches its meaning), falling back to [fallback_category] for kana-only
    readings. Non-homophone words get no other_kanji.
    """
    out = {"kana": kana, "meaning": meaning}
    primary = pick_primary(reading_forms, meaning)
    category = (primary["category"] if primary else None) or fallback_category
    if primary:
        out["kanji"] = primary["kanji"]
    if category:
        out["category"] = category
    if primary:
        if primary["kanji_usage"]:
            out["kanji_usage"] = primary["kanji_usage"]
        others = [f for f in reading_forms if f["kanji"] != primary["kanji"]]
        if others:
            out["other_kanji"] = [
                {
                    k: v
                    for k, v in (
                        ("kanji", f["kanji"]),
                        ("meaning", f["meaning"]),
                        ("kanji_usage", f["kanji_usage"]),
                    )
                    if v
                }
                for f in others
            ]
    return out


def main():
    info, order, forms = build_csv_map()
    kata_readings = katakana_deck_readings()
    with open(HIRA, encoding="utf-8") as f:
        deck = json.load(f)
    words = deck["words"]

    # Drop loanword duplicates: a hiragana word whose katakana form is already a
    # katakana-deck word (bread/TV/bus). Those belong to the katakana track.
    removed = [w["kana"] for w in words if to_katakana(w["kana"]) in kata_readings]
    words = [w for w in words if to_katakana(w["kana"]) not in kata_readings]
    present = {w["kana"] for w in words}

    enriched = 0
    new_out = []
    for w in words:
        reading = w["kana"]
        if reading in info:
            # Keep the deck word's own meaning; match the primary kanji to it.
            new_out.append(build_entry(
                reading, w.get("meaning", ""), forms.get(reading, []),
                fallback_category=info[reading]["category"],
            ))
            enriched += 1
        else:
            # original hand-authored word not in the CSV: keep as-is.
            new_out.append(w)

    # Append CSV hiragana words not already in the deck (net-new).
    added = 0
    for reading in order:
        if reading in present or to_katakana(reading) in kata_readings:
            continue
        reading_forms = forms.get(reading, [])
        # New words take their meaning from the primary kanji form (or the first
        # CSV occurrence when the reading is kana-only).
        primary = pick_primary(reading_forms, None)
        meaning = primary["meaning"] if primary else info[reading]["meaning"]
        new_out.append(build_entry(
            reading, meaning, reading_forms, fallback_category=info[reading]["category"],
        ))
        added += 1

    deck["words"] = new_out
    with open(HIRA, "w", encoding="utf-8") as f:
        json.dump(deck, f, ensure_ascii=False, indent=2)
        f.write("\n")

    with_kanji = sum(1 for w in new_out if "kanji" in w)
    with_cat = sum(1 for w in new_out if "category" in w)
    with_other = sum(1 for w in new_out if "other_kanji" in w)
    sys.stdout.buffer.write(
        (
            f"deck words: {len(new_out)} | enriched-from-csv: {enriched} | appended: {added}\n"
            f"with kanji: {with_kanji} | with category: {with_cat} | with other_kanji: {with_other}\n"
            f"removed loanword duplicates: {removed}\n"
        ).encode("utf-8")
    )


if __name__ == "__main__":
    main()
