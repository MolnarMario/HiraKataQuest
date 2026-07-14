# Bundled font subsets

## `app/src/main/res/font/print_forms.ttf`

The **connected printed forms** of the divergent kana (see
`domain/model/PrintDivergence.kt`) — an 8-glyph merge of two faces, ~3.4 KB:

| Glyphs | Source face | License |
|---|---|---|
| き ぎ さ ざ ゃ ゅ ょ | **Kosugi** Regular (© Motoya) | Apache 2.0 — `Kosugi-LICENSE.txt` |
| り | **IBM Plex Sans JP** Regular (© IBM) | OFL 1.1 — `IBMPlexSansJP-OFL.txt` |

Bundling a font subset was explicitly approved by the project owner on
2026-07-14 (an unapproved font was rejected first — ask before adding any
third-party asset). Per his decision the CONNECTED form is what every
typed/printed surface shows for さ/き/り: the comparison dialog's "In print"
specimen, the Reading track's flashcard front and quiz prompt, all Library
glyph views, and the writing guide panel's header glyph (`PrintFontFamily` in
`PrintDivergenceInfo.kt`).

Why a merge: さ/き/り diverge from school handwriting in their *connected*
print strokes, but Android's system Japanese font (Noto Sans CJK JP) uses
detached, handwriting-style forms for all three — and **no single free gothic
face connects all three** (verified by rendering at 400px+; small renders
mislead — BIZ UDGothic looks connected at 120px but is detached, as are Zen
Kaku Gothic, Sawarabi Gothic, and Noto variants for some of the trio). Kosugi
connects さ/き but not り; IBM Plex Sans JP connects り.

Everything outside these 8 glyphs falls back to the system font automatically
(the family is applied to whole strings; only these glyphs resolve from the
subset).

Regenerate (Python 3 + fonttools; sources from
https://github.com/google/fonts — apache/kosugi + ofl/ibmplexsansjp):

    py -3 -m fontTools.subset Kosugi-Regular.ttf --unicodes=U+304D,U+304E,U+3055,U+3056,U+3083,U+3085,U+3087 --output-file=kosugi7.ttf --name-IDs=0,1,2,13,14
    py -3 -m fontTools.subset IBMPlexSansJP-Regular.ttf --unicodes=U+308A --output-file=plex_ri.ttf --name-IDs=0,1,2,13,14
    py -3 -c "from fontTools.ttLib import TTFont; from fontTools.ttLib.scaleUpem import scale_upem; f=TTFont('kosugi7.ttf'); scale_upem(f,1000); f.save('kosugi7_1000.ttf')"
    py -3 -c "from fontTools.merge import Merger; Merger().merge(['kosugi7_1000.ttf','plex_ri.ttf']).save('app/src/main/res/font/print_forms.ttf')"

(The upem rescale matters: Kosugi is 1024/em, IBM Plex 1000/em — merging
without it distorts glyph sizes.)

If `PrintDivergence` ever gains a new kana, add its glyph (and voiced/small
companions) to the subset AND verify the new glyph's connectedness at 400px+
first.
