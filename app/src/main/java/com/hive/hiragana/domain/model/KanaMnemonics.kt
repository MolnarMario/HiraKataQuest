package com.hive.hiragana.domain.model

/**
 * One accent stroke of a mnemonic overlay drawing: an SVG-style path in the
 * SAME viewBox-109 coordinate space as the character's KanjiVG `strokes[]`,
 * restricted to absolute M/L/C/Q(/Z) commands with exact per-command arity
 * (M/L take 2 numbers, C takes 6, Q takes 4, Z none — one command letter per
 * segment, no implicit repetition). Coordinates stay within the viewBox with
 * a 2-unit tolerance. [filled] draws the path as a filled shape (an eye dot,
 * a flag); the default strokes it as a line, matching the glyph's ink.
 *
 * Overlays are ORIGINAL art positioned relative to the KanjiVG geometry; they
 * must never trace a KanjiVG stroke (that would inherit its CC BY-SA license).
 */
data class OverlayPath(val d: String, val filled: Boolean = false)

/**
 * A hand-authored accent drawing rendered OVER the grey stroke-rendered
 * glyph, so the mnemonic image sits on the kana's real shape (a barb turning
 * し into a fishhook, ribbon ends closing の into a knot). One accent color
 * for all paths, applied at draw time by the presentation layer; the domain
 * stays free of Compose types.
 */
data class MnemonicOverlay(val paths: List<OverlayPath>)

/**
 * One memory aid for a kana: an English [keyword] anchoring the SOUND plus a
 * 1-2 sentence [story] tying the kana's SHAPE to that keyword. The story's
 * final sentence always spells out the sound anchor ("Apple starts with
 * a."). [overlay] is reserved on every entry and authored only where the
 * shape association is clearest.
 */
data class KanaMnemonic(
    val keyword: String,
    val story: String,
    val overlay: MnemonicOverlay? = null,
)

/**
 * Character limits for a learner-authored ("Add your own") mnemonic — the
 * title (shown uppercase, like the built-in keywords) and the description.
 * Enforced both by the input fields and when the ViewModel stores the entry.
 */
const val CUSTOM_MNEMONIC_TITLE_MAX = 30
const val CUSTOM_MNEMONIC_STORY_MAX = 200

/**
 * Keyword-and-story mnemonics for the base gojūon hiragana — editorial
 * knowledge that cannot be derived from Unicode or the asset data, so the map
 * below is the one source of truth (the [PrintDivergence] discipline: Kotlin,
 * not strings.xml, not the tool-generated asset JSONs). Stories describe the
 * HANDWRITTEN school form the app's KanjiVG ghosts teach, not the print form.
 *
 * Every kana ships a LIST of mnemonics: the first is the default (picked by
 * the project owner from authored candidates), the rest are alternates for
 * the in-app "More ways to remember" picker — different images click for
 * different brains, so the learner can promote whichever alternate works for
 * them (persisted per kana as glyph -> keyword via
 * `SettingsRepository.getMnemonicChoices`; [mnemonicFor] resolves it).
 *
 * Lookup is EXACT-glyph, deliberately unlike [PrintDivergence]'s NFD
 * inheritance: a keyword anchors one exact sound, and inheriting き's "ki"
 * keyword onto ぎ (gi) or きゃ (kya) would anchor the wrong one. Derived kana
 * get derived mnemonics later, via [derive]:
 *
 *  1. Katakana: a second exact-match table consulted before derivation
 *     (different shapes, own keywords; the へ/ヘ and り/リ shape twins of
 *     [com.hive.hiragana.domain.scoring.KanaLookalikes] share entries).
 *  2. Voiced/handakuten: NFD-decompose (see [KanaKind]); return the base
 *     entry wrapped with a templated voicing note ("The ゛ mark voices か ka
 *     into が ga.") via an additive nullable field.
 *  3. Yōon: classify via [KanaUnits]; compose the base and small-kana entries
 *     into one derived entry ("き + small ゃ = きゃ kya.").
 *  4. Extended kana: rule-based notes (small っ holds a pause).
 *
 * Every UI surface funnels through [mnemonicFor] over an item's reading-unit
 * characters, so those phases land here without touching any call site.
 *
 * Overlay path literals are wrapped in `// OVERLAY:BEGIN <id>` and
 * `// OVERLAY:END <id>` marker comments (one quoted path literal per line):
 * `tools/render_mnemonic_overlays.py` extracts them through that contract to
 * render the authoring contact sheet, so the shipped Kotlin stays the single
 * source of truth. Keep the markers when editing overlays.
 *
 * Purely informative: nothing here touches scoring, reading tallies, or
 * scheduling. Copy rules: no em-dashes, final sentence is the sound anchor,
 * stories cap at 160 characters, keywords are globally unique (all pinned by
 * KanaMnemonicsTest).
 */
object KanaMnemonics {

    private val BASE_HIRAGANA: Map<String, List<KanaMnemonic>> = mapOf(
        "あ" to listOf(
            KanaMnemonic(
                keyword = "APPLE",
                story = "An apple with a stem: the bar and curve cross at the " +
                    "top, and the fruit swings below in one plump loop. Apple " +
                    "starts with a.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // A big leaf sprouting from the apple's stem.
                        // OVERLAY:BEGIN a
                        OverlayPath("M 52 24 C 58 14 68 10 70 13 C 71 17 62 24 52 24 Z", filled = true),
                        // OVERLAY:END a
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "ANTENNA",
                story = "A TV antenna stands on the roof. Its signal swirls out " +
                    "below in one big loop. Antenna starts with a.",
            ),
            KanaMnemonic(
                keyword = "ANCHOR",
                story = "An anchor: the crossbar sits high on the shaft and the " +
                    "curved arms swing below in one wide loop. Anchor starts with " +
                    "a.",
            ),
            KanaMnemonic(
                keyword = "ACROBAT",
                story = "An acrobat cartwheels across the page: arms crossed up " +
                    "top, body sweeping through one giant loop below. Acrobat " +
                    "starts with a.",
            ),
            KanaMnemonic(
                keyword = "AHH",
                story = "Say ahh at the dentist: the wide open mouth is the big " +
                    "loop under the crossed lines. Ahh is exactly the sound a.",
            ),
        ),
        "い" to listOf(
            KanaMnemonic(
                keyword = "HAWAII",
                story = "Hawaii ends with two little i letters standing side by " +
                    "side, just like these two strokes. Hawaii ends with i.",
            ),
            KanaMnemonic(
                keyword = "EELS",
                story = "Two eels swim side by side. The left one is longer and " +
                    "flicks its tail up, the right one stays short. Say eels: it " +
                    "begins with i.",
            ),
            KanaMnemonic(
                keyword = "EASEL",
                story = "An easel stands on two legs. The tall left leg kicks up " +
                    "a little foot, the short right leg leans in beside it. Say " +
                    "easel: it begins with i.",
            ),
        ),
        "う" to listOf(
            KanaMnemonic(
                keyword = "OOF",
                story = "A punch lands and he doubles over, oof. The short stroke " +
                    "is his head, the long curl his folded body. Oof starts with " +
                    "u.",
            ),
            KanaMnemonic(
                keyword = "OOZE",
                story = "A blob of ooze slips off the short ledge above. It " +
                    "bulges out and drips down in one long curling wave. Say " +
                    "ooze: it begins with u.",
            ),
            KanaMnemonic(
                keyword = "OOLONG",
                story = "A short puff of steam floats up top. Below it, oolong " +
                    "tea arcs from the pot and curls down in a long pour. Say " +
                    "oolong: it begins with u.",
            ),
            KanaMnemonic(
                keyword = "UMBRELLA",
                story = "An umbrella caught by the wind: the small canopy on top, " +
                    "the handle curving away underneath. Umbrella starts with the " +
                    "letter u.",
            ),
        ),
        "え" to listOf(
            KanaMnemonic(
                keyword = "EXOTIC BIRD",
                story = "An exotic bird perches on a branch, crest flicking up " +
                    "behind its head. Exotic starts with e.",
            ),
            KanaMnemonic(
                keyword = "EXIT",
                story = "An exit sign hangs up top. Below it a figure dashes down " +
                    "the slope, doubles back, and skids out flat along the floor. " +
                    "Exit starts with e.",
            ),
            KanaMnemonic(
                keyword = "ELF",
                story = "A tiny cap floats up top. Below it an elf leaps, legs " +
                    "zigzagging down the hill, the long shoe swooshing out " +
                    "behind. Elf starts with e.",
            ),
        ),
        "お" to listOf(
            KanaMnemonic(
                keyword = "OPERA",
                story = "An opera singer holds a big round Oh, one arm swung " +
                    "wide, a high note flying off at the top right. Opera starts " +
                    "with o.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // A whole eighth note beside the top-right dash: the
                        // singer's high note flying off.
                        // OVERLAY:BEGIN o
                        OverlayPath("M 86.5 32 C 86.5 28.7 93.5 28.7 93.5 32 C 93.5 35.3 86.5 35.3 86.5 32 Z", filled = true),
                        OverlayPath("M 93.5 31 L 93.5 14"),
                        OverlayPath("M 93.5 14 C 99 16 100 21 96 26"),
                        // OVERLAY:END o
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "OAK",
                story = "A branch crosses the oak's trunk, roots curl below in " +
                    "one wide loop, and a leaf drifts at the top right, the " +
                    "mark the plain a kana never has. Oak starts with o.",
            ),
            KanaMnemonic(
                keyword = "OCEAN",
                story = "A mast crosses the boom and the sail bellies out in one " +
                    "big loop, while a fleck of ocean spray flies at the top " +
                    "right. Ocean starts with o.",
            ),
            KanaMnemonic(
                keyword = "OCTOPUS",
                story = "An octopus curls one long arm through a big loop below " +
                    "the crossed lines, then squirts a jet of ink at the top " +
                    "right. Octopus starts with o.",
            ),
        ),
        "か" to listOf(
            KanaMnemonic(
                keyword = "CUT",
                story = "A knife cuts through the loaf in one long slash, a crumb " +
                    "flying off beside it. Cut starts with k, the k of ka.",
            ),
            KanaMnemonic(
                keyword = "KARATE",
                story = "A karate kick frozen mid-move: the long slash is the " +
                    "leg, the hook is the bending body, the little dash a belt " +
                    "tip. Karate starts with ka.",
            ),
            KanaMnemonic(
                keyword = "CAR",
                story = "A car corners fast: the road bends and hooks down, the " +
                    "car streaks across in a long slash, a pebble flicks aside. " +
                    "Car starts with ka.",
            ),
            KanaMnemonic(
                keyword = "KANGAROO",
                story = "A kangaroo leans back on its curling tail, one long leg " +
                    "stretched in a slash, while a tiny joey hops beside it. " +
                    "Kangaroo starts with ka.",
            ),
            KanaMnemonic(
                keyword = "KITE",
                story = "A kite leans in the wind, its long string streaming down " +
                    "beside it, a short bow flicking off the tail. Kite starts " +
                    "with k, the k of ka.",
            ),
        ),
        "き" to listOf(
            KanaMnemonic(
                keyword = "KEY",
                story = "An old key: two teeth on the shaft, and the ring hanging " +
                    "loose below. Key sounds exactly like ki.",
            ),
            KanaMnemonic(
                keyword = "KIWI",
                story = "A kiwi bird dips its long beak across two thin twigs, " +
                    "its plump body sitting apart below. Kiwi starts with ki.",
            ),
            KanaMnemonic(
                keyword = "KIMONO",
                story = "A kimono rests on a stand: two sleeve bars, a long sash " +
                    "sweeping across them, and the round hem lying loose below. " +
                    "Kimono starts with ki.",
            ),
            KanaMnemonic(
                keyword = "KITCHEN",
                story = "Stacked kitchen racks: two shelves across the rail, the " +
                    "pan hanging loose below. Kitchen starts with ki.",
            ),
        ),
        "く" to listOf(
            KanaMnemonic(
                keyword = "CUCKOO",
                story = "A cuckoo pops from its clock, beak open wide to call. " +
                    "The whole kana is that single open beak, one stroke. Cuckoo " +
                    "starts with ku.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // The back of the bird's head closes the open beak
                        // into a full profile, with a round eye above the jaw.
                        // OVERLAY:BEGIN ku
                        OverlayPath("M 65 18 C 90 30 90 78 66 90"),
                        OverlayPath("M 59.5 36 C 59.5 31.4 66.5 31.4 66.5 36 C 66.5 40.6 59.5 40.6 59.5 36 Z", filled = true),
                        // OVERLAY:END ku
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "COO",
                story = "A dove turns its head and coos softly. The single bent " +
                    "stroke is its little open beak pointing left. Coo sounds " +
                    "exactly like ku.",
            ),
            KanaMnemonic(
                keyword = "COOKIE",
                story = "Someone bites a big wedge out of the side of a cookie. " +
                    "The kana is the notch the bite leaves, one bent stroke. " +
                    "Cookie starts with ku.",
            ),
        ),
        "け" to listOf(
            KanaMnemonic(
                keyword = "KEG",
                story = "A tall keg with its tap bar on the right and the pour " +
                    "curving down to the floor. Keg starts with ke.",
            ),
            KanaMnemonic(
                keyword = "KENDO",
                story = "A kendo student stands at the left holding a tall bamboo " +
                    "sword, its small guard near the top, its tip curving away. " +
                    "Kendo starts with ke.",
            ),
            KanaMnemonic(
                keyword = "KELP",
                story = "Kelp sways underwater: one strand rises on the left, " +
                    "another bends at the base, one leaf reaching across. Kelp " +
                    "starts with ke.",
            ),
            KanaMnemonic(
                keyword = "KEBAB",
                story = "A kebab stall: the tall skewer stands on the right with " +
                    "its short handle bar, while smoke curls up on the left. " +
                    "Kebab starts with ke.",
            ),
            KanaMnemonic(
                keyword = "KETCHUP",
                story = "The bottle stands tall on the left and a squirt of " +
                    "ketchup arcs down the right. Ketchup starts with ke.",
            ),
        ),
        "こ" to listOf(
            KanaMnemonic(
                keyword = "KOI",
                story = "Two koi swim one above the other: the top fish curls its " +
                    "tail down, the lower one glides in a long smiling curve. Koi " +
                    "starts with ko.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // An eye at each koi's head and bubbles rising in the
                        // water to their right.
                        // OVERLAY:BEGIN ko
                        OverlayPath("M 28 21 C 28 17 34 17 34 21 C 34 25 28 25 28 21 Z", filled = true),
                        OverlayPath("M 24 63 C 24 59 30 59 30 63 C 30 67 24 67 24 63 Z", filled = true),
                        OverlayPath("M 85.5 52 C 85.5 48.7 90.5 48.7 90.5 52 C 90.5 55.3 85.5 55.3 85.5 52 Z"),
                        OverlayPath("M 89.5 41 C 89.5 36.4 96.5 36.4 96.5 41 C 96.5 45.6 89.5 45.6 89.5 41 Z"),
                        // OVERLAY:END ko
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "COCOA",
                story = "A mug of hot cocoa: the long smiling curve is its round " +
                    "bottom, the short stroke above is steam curling at the tip. " +
                    "Cocoa starts with ko.",
            ),
            KanaMnemonic(
                keyword = "KOALA",
                story = "A koala naps: the top stroke is its branch, the long " +
                    "curve below is its round body sagging in sleep. Koala starts " +
                    "with ko.",
            ),
            KanaMnemonic(
                keyword = "COIN STACK",
                story = "Two flat coins rest one above the other, not quite " +
                    "touching. Coin starts with co, close to ko.",
            ),
        ),
        "さ" to listOf(
            KanaMnemonic(
                keyword = "SAMURAI",
                story = "A samurai bows: his staff lies level, his sash swings " +
                    "down across it and hooks, and his cushion sits apart below. " +
                    "Samurai starts with sa.",
            ),
            KanaMnemonic(
                keyword = "SARI",
                story = "A sari dries on a rod: the cloth sweeps down across it " +
                    "and hooks, and a loose hem curls apart below. Sari starts " +
                    "with sa.",
            ),
            KanaMnemonic(
                keyword = "SALSA",
                story = "A crisp chip dives down past the table edge and hooks, " +
                    "while the salsa bowl waits apart below. Salsa starts with " +
                    "sa.",
            ),
        ),
        "し" to listOf(
            KanaMnemonic(
                keyword = "FISHHOOK",
                story = "One stroke drops deep and curves up at the tip: the " +
                    "classic fishhook. Cast it quietly, shh: shi.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // The hook's tie-eye ring above the shank and a solid
                        // barb spiking inside the point.
                        // OVERLAY:BEGIN shi
                        OverlayPath("M 35 9 C 35 3.7 43 3.7 43 9 C 43 14.3 35 14.3 35 9 Z"),
                        OverlayPath("M 81 77 L 72 72 L 79 84 Z", filled = true),
                        // OVERLAY:END shi
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "SHIP",
                story = "A ship drops its anchor line straight down. At the " +
                    "bottom it curves up into a hook. Ship starts with shi.",
            ),
            KanaMnemonic(
                keyword = "SHE",
                story = "She lowers her fishing line straight into the pond until " +
                    "the end bends up in a little hook. She sounds exactly like " +
                    "shi.",
            ),
            KanaMnemonic(
                keyword = "SHIN",
                story = "One long shin runs straight down to the foot, and the " +
                    "toes curl gently upward. Shin starts with shi.",
            ),
        ),
        "す" to listOf(
            KanaMnemonic(
                keyword = "SOUP",
                story = "A ladle hangs over the pot's rim, stirs one small loop " +
                    "in the soup, and trails away to the left. Soup starts with " +
                    "su.",
            ),
            KanaMnemonic(
                keyword = "SUMO",
                story = "A sumo wrestler hangs from the bar: his belly makes one " +
                    "round loop, and his leg sweeps down left. Sumo starts with " +
                    "su.",
            ),
            KanaMnemonic(
                keyword = "SUIT",
                story = "A suit hangs on the rail. The tie falls straight, loops " +
                    "once around itself, and the tip swings down to the left. " +
                    "Suit starts with su.",
            ),
            KanaMnemonic(
                keyword = "SPOON",
                story = "A spoon hangs over the rail with one drop looping off " +
                    "its tip. Spoon carries the oo of su.",
            ),
        ),
        "せ" to listOf(
            KanaMnemonic(
                keyword = "SENSEI",
                story = "Your sensei paints a gate: a level bar across two posts. " +
                    "The short post hooks, the tall one bends along the ground. " +
                    "Sensei starts with se.",
            ),
            KanaMnemonic(
                keyword = "SEVEN",
                story = "Seven shirts dry on a line strung between two poles. One " +
                    "pole is short and hooks, the other bends along the ground. " +
                    "Seven starts with se.",
            ),
            KanaMnemonic(
                keyword = "SETTEE",
                story = "A garden settee: the back rail runs level, one arm hooks " +
                    "under it, and the left leg bends into the long seat. Settee " +
                    "starts with se.",
            ),
            KanaMnemonic(
                keyword = "SEWING",
                story = "The thread runs level while two stitches cross it, one " +
                    "hooking, one bending along the hem. Sewing starts with se.",
            ),
        ),
        "そ" to listOf(
            KanaMnemonic(
                keyword = "SOAP",
                story = "A wet bar of soap shoots away, leaving one slippery " +
                    "zigzag skid and a long trailing curve. Soap starts with so.",
            ),
            KanaMnemonic(
                keyword = "SEW",
                story = "A needle sews one zigzag stitch, then the thread pulls " +
                    "away in a long curve. Sew sounds exactly like so.",
            ),
            KanaMnemonic(
                keyword = "SOLO",
                story = "A solo skater never lifts the blade: zigzag turns across " +
                    "the top, then one long sweeping curve to the finish. Solo " +
                    "starts with so.",
            ),
            KanaMnemonic(
                keyword = "SONAR",
                story = "A submarine's sonar traces the seabed: sharp zigzag " +
                    "peaks, then one smooth valley curve. Sonar starts with so.",
            ),
        ),
        "た" to listOf(
            KanaMnemonic(
                keyword = "TACO",
                story = "The left two strokes cross into a t and the right two " +
                    "curl like a small a, so the kana spells its own sound. Taco " +
                    "starts with ta.",
            ),
            KanaMnemonic(
                keyword = "TANGO",
                story = "A tall dancer, the crossed t, sweeps one long leg to the " +
                    "left while the partner marks two short steps on the right. " +
                    "Tango starts with ta.",
            ),
            KanaMnemonic(
                keyword = "TADPOLE",
                story = "The long stroke whips down like a tadpole's tail under " +
                    "the crossbar, and the two short strokes are its sprouting " +
                    "legs. Tadpole starts with ta.",
            ),
            KanaMnemonic(
                keyword = "TABLE",
                story = "A tabletop crossed at the left, with the leg and stool " +
                    "marks holding it up on the right. Table is spelled with ta.",
            ),
        ),
        "ち" to listOf(
            KanaMnemonic(
                keyword = "CHEEK",
                story = "A small bar marks the brow, then one long stroke swells " +
                    "into a round open curve, like a plump cheek in profile. " +
                    "Cheek starts with chi.",
            ),
            KanaMnemonic(
                keyword = "CHEESE",
                story = "The crossbar is a cutting wire, and the stroke below " +
                    "curves like the round edge of the cheese wheel it sliced. " +
                    "Cheese starts with chi.",
            ),
            KanaMnemonic(
                keyword = "CHEETAH",
                story = "Under the crossbar, the long stroke dives down, then " +
                    "curls wide like a cheetah's tail swinging through a sharp " +
                    "turn. Cheetah starts with chi.",
            ),
            KanaMnemonic(
                keyword = "CHEERLEADER",
                story = "A cheerleader arches back under the top bar, pom-pom " +
                    "swung overhead in a wide curve. Cheerleader starts with chi.",
            ),
        ),
        "つ" to listOf(
            KanaMnemonic(
                keyword = "TSUNAMI",
                story = "One single stroke rises, rolls over the top, and curls " +
                    "down like a great wave about to break on the shore. Tsunami " +
                    "starts with tsu.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // A big foam spiral curling inside the wave and spray
                        // drops flying off the crest.
                        // OVERLAY:BEGIN tsu
                        OverlayPath("M 74 46 C 60 42 50 52 58 62 C 64 69 74 64 71 55"),
                        OverlayPath("M 67.5 19 C 67.5 15.7 72.5 15.7 72.5 19 C 72.5 22.3 67.5 22.3 67.5 19 Z", filled = true),
                        OverlayPath("M 78 25 C 78 21 84 21 84 25 C 84 29 78 29 78 25 Z", filled = true),
                        OverlayPath("M 87.5 16 C 87.5 12.7 92.5 12.7 92.5 16 C 92.5 19.3 87.5 19.3 87.5 16 Z", filled = true),
                        // OVERLAY:END tsu
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "TSETSE",
                story = "The whole kana is one loop of flight: a tsetse fly " +
                    "swings up, arcs around, and glides down to land. Say tsetse: " +
                    "its buzzing ts is the ts of tsu.",
            ),
        ),
        "て" to listOf(
            KanaMnemonic(
                keyword = "TELEPHONE",
                story = "The flat bar on top is an old wall telephone, and the " +
                    "single curve below is its long cord swinging gently. " +
                    "Telephone starts with te.",
            ),
            KanaMnemonic(
                keyword = "TENNIS",
                story = "The serve skims flat across the top, then the ball drops " +
                    "in one long curve and rolls away along the ground. Tennis " +
                    "starts with te.",
            ),
            KanaMnemonic(
                keyword = "TEMPLE",
                story = "A wide temple roof runs along the top, and a single " +
                    "pillar bends in a gentle curve all the way down to the " +
                    "ground. Temple starts with te.",
            ),
            KanaMnemonic(
                keyword = "HAND",
                story = "The flat top and long curve make an open cupped hand, " +
                    "and hand in Japanese is te.",
            ),
        ),
        "と" to listOf(
            KanaMnemonic(
                keyword = "TOE",
                story = "The big curve is a bare toe seen from the side, and the " +
                    "short first stroke is a splinter stuck into it. Toe sounds " +
                    "exactly like to.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // Big pain sparks radiating off the splinter. Ouch.
                        // OVERLAY:BEGIN to
                        OverlayPath("M 33 12 L 28 4"),
                        OverlayPath("M 40 10 L 41 3"),
                        OverlayPath("M 46 14 L 52 8"),
                        // OVERLAY:END to
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "TOAD",
                story = "A toad crouches in one big round curve, and a small fly " +
                    "drops down toward its nose from above. Toad starts with to.",
            ),
            KanaMnemonic(
                keyword = "TOAST",
                story = "The short stroke is a knife dipping down, and the long " +
                    "curve sweeps like jam being swirled across a slice of toast. " +
                    "Toast starts with to.",
            ),
        ),
        "な" to listOf(
            KanaMnemonic(
                keyword = "NUN",
                story = "A nun kneels in prayer beneath the cross, the loop her " +
                    "folded robe. Nun starts with n, the n of na.",
            ),
            KanaMnemonic(
                keyword = "NACHOS",
                story = "Two chips cross on the left, a salsa drop sits at top " +
                    "right, and the last stroke loops like melted cheese. Nachos " +
                    "starts with na.",
            ),
            KanaMnemonic(
                keyword = "NAIL",
                story = "A nail hammered through the plank makes the cross on the " +
                    "left, and the last stroke curls into a loop where it hit a " +
                    "knot. Nail starts with na.",
            ),
            KanaMnemonic(
                keyword = "NAVY",
                story = "A navy cadet rigs the crossed mast, hoists a small flag " +
                    "at top right, and ties the last stroke into a loop of rope. " +
                    "Navy starts with na.",
            ),
        ),
        "に" to listOf(
            KanaMnemonic(
                keyword = "NINJA",
                story = "A ninja presses flat against the tall wall and leaves " +
                    "only two short smudges on the right. Ninja starts with ni.",
            ),
            KanaMnemonic(
                keyword = "KNEE",
                story = "The tall stroke is a leg, and the two short strokes are " +
                    "pads strapped above and below the knee. Knee sounds exactly " +
                    "like ni.",
            ),
            KanaMnemonic(
                keyword = "NIECE",
                story = "A tall parent stands on the left while your little niece " +
                    "lifts two short arms to be carried. Niece starts with ni.",
            ),
        ),
        "ぬ" to listOf(
            KanaMnemonic(
                keyword = "NOODLES",
                story = "Two strokes cross like slurped noodles, and the extra " +
                    "loop tied on the tail is exactly what its twin me is " +
                    "missing. Noodles starts with nu.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // Chopsticks pinching the noodle's looped tail from
                        // the open space at the right.
                        // OVERLAY:BEGIN nu
                        OverlayPath("M 82 38 L 91 64"),
                        OverlayPath("M 97 40 L 93 64"),
                        // OVERLAY:END nu
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "GNU",
                story = "Two horns cross at the top, the big stroke rounds into a " +
                    "gnu's body, and the tail ends in the extra loop. Gnu sounds " +
                    "exactly like nu.",
            ),
            KanaMnemonic(
                keyword = "NUT",
                story = "A squirrel curls the big stroke around a buried nut and " +
                    "flicks its tail into the extra loop at the end. Nut starts " +
                    "with nu.",
            ),
        ),
        "ね" to listOf(
            KanaMnemonic(
                keyword = "NECK",
                story = "A cat rubs its neck on the tall post, and its looping " +
                    "tail reminds you that cat is neko in Japanese. Neck starts " +
                    "with ne.",
            ),
            KanaMnemonic(
                keyword = "NEST",
                story = "A bird winds the long second stroke around the tall " +
                    "trunk and loops it into a snug nest at the bottom. Nest " +
                    "starts with ne.",
            ),
            KanaMnemonic(
                keyword = "NET",
                story = "The tall stroke is the handle, and the sweeping stroke " +
                    "curls around and closes its loop like a butterfly net. Net " +
                    "starts with ne.",
            ),
        ),
        "の" to listOf(
            KanaMnemonic(
                keyword = "KNOT",
                story = "The single stroke crosses over itself like a loose knot " +
                    "in a ribbon. Say knot: it begins with no.",
            ),
            KanaMnemonic(
                keyword = "NO-ENTRY",
                story = "One single stroke draws the slash and the circle of a " +
                    "no-entry sign in one motion. No-entry starts with no.",
            ),
            KanaMnemonic(
                keyword = "NOSE",
                story = "One curly stroke doodles a big round nose with the " +
                    "nostril swirling inside. Nose starts with no.",
            ),
        ),
        "は" to listOf(
            KanaMnemonic(
                keyword = "HARBOR",
                story = "Two mooring posts stand at the harbor. A bar crosses the " +
                    "right one, and its base curls into a rope loop for your " +
                    "boat. Harbor starts with ha.",
            ),
            KanaMnemonic(
                keyword = "HAMMOCK",
                story = "Two posts stand in the yard. A bar crosses the right " +
                    "one, and the loop sagging at its foot is your little " +
                    "hammock. Hammock starts with ha.",
            ),
            KanaMnemonic(
                keyword = "HARP",
                story = "The left stroke is the harp's tall pillar. The right " +
                    "side holds the crossbar and one long string that curls at " +
                    "the bottom. Harp starts with ha.",
            ),
            KanaMnemonic(
                keyword = "HANGER",
                story = "A clothes hanger hangs on the tall left post, its bar " +
                    "across and hook curling below. Hanger starts with ha.",
            ),
        ),
        "ひ" to listOf(
            KanaMnemonic(
                keyword = "HEE-HEE",
                story = "A wide mouth grins from ear to ear: it dips deep in the " +
                    "middle, rises at both corners, and flicks out at the end. " +
                    "Say hee-hee: it begins with hi.",
            ),
            KanaMnemonic(
                keyword = "HEEL",
                story = "One stroke rolls like a heel: it steps down, rounds " +
                    "along the ground, and lifts off at the back with a small " +
                    "flick. Say heel: it begins with hi.",
            ),
            KanaMnemonic(
                keyword = "HEAT",
                story = "One stroke wobbles like heat over a campfire: it dips, " +
                    "rounds the coals, and rises with a flick at the tip. Say " +
                    "heat: it begins with hi.",
            ),
        ),
        "ふ" to listOf(
            KanaMnemonic(
                keyword = "FUJI",
                story = "A puff of steam floats over the peak, the long slope " +
                    "swings down and curls, and a small cloud drifts at each " +
                    "side. Fuji starts with fu.",
            ),
            KanaMnemonic(
                keyword = "FUTON",
                story = "Four gentle marks: a pillow floats on top, the sleeper " +
                    "curls under the middle quilt, and a slipper rests at each " +
                    "side. Futon starts with fu.",
            ),
            KanaMnemonic(
                keyword = "FOOTPRINT",
                story = "Four separate marks, like footprints in snow: a hop on " +
                    "top, a long skid in the middle, one print at each side. Say " +
                    "footprint: it begins with fu.",
            ),
        ),
        "へ" to listOf(
            KanaMnemonic(
                keyword = "HEAVEN",
                story = "An arrow tip pointing up toward heaven. Heaven starts " +
                    "with he.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // A full arrow under the peak: the kana is its head,
                        // the red is the shaft and feathers.
                        // OVERLAY:BEGIN he
                        OverlayPath("M 41 42 L 41 74"),
                        OverlayPath("M 33 82 L 41 72"),
                        OverlayPath("M 49 82 L 41 72"),
                        // OVERLAY:END he
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "HELICOPTER",
                story = "One stroke makes a mountain: a short climb, a peak, then " +
                    "a long glide down, like a helicopter crossing the ridge. " +
                    "Helicopter starts with he.",
            ),
            KanaMnemonic(
                keyword = "HEDGEHOG",
                story = "One little stroke rises to a point and eases down: the " +
                    "prickly back of a hedgehog crossing the path. Hedgehog " +
                    "starts with he.",
            ),
            KanaMnemonic(
                keyword = "HELMET",
                story = "One stroke tips up to a small peak and slopes gently " +
                    "away: the pointed top of an old helmet. Helmet starts with " +
                    "he.",
            ),
        ),
        "ほ" to listOf(
            KanaMnemonic(
                keyword = "HOTEL",
                story = "It is the ha kana with one extra bar: the house gained " +
                    "a floor and became a hotel. The loop at the base is its " +
                    "revolving door. Hotel starts with ho.",
            ),
            KanaMnemonic(
                keyword = "HOSE",
                story = "A tall post stands on the left. On the right, the hose " +
                    "hangs across two bars and coils into a neat loop at the " +
                    "bottom. Hose starts with ho.",
            ),
            KanaMnemonic(
                keyword = "HOLE",
                story = "A tall post stands on the left. On the right, the long " +
                    "stroke slips under two bars and curls into a round hole at " +
                    "the bottom. Hole starts with ho.",
            ),
            KanaMnemonic(
                keyword = "HOSPITAL",
                story = "A tall hospital: the flagpole on the left, two floors " +
                    "crossing the tower, the loop its revolving door. Hospital " +
                    "starts with ho.",
            ),
        ),
        "ま" to listOf(
            KanaMnemonic(
                keyword = "MANDOLIN",
                story = "A mandolin stands upright: two frets cross the long " +
                    "neck, and the body curls into a round loop at the bottom. " +
                    "Mandolin starts with ma.",
            ),
            KanaMnemonic(
                keyword = "MAMA",
                story = "Mama hangs two laundry lines on the tall pole and ties " +
                    "the rope in a loop at the bottom. Mama starts with ma.",
            ),
            KanaMnemonic(
                keyword = "MAST",
                story = "A ship's mast carries two crossbeams, and the rigging " +
                    "rope ties a loop at its foot. Mast starts with ma.",
            ),
        ),
        "み" to listOf(
            KanaMnemonic(
                keyword = "ME",
                story = "This kana is the number 21 squeezed together. Who just " +
                    "turned 21? Me! Me sounds exactly like mi.",
            ),
            KanaMnemonic(
                keyword = "METEOR",
                story = "A meteor dips low, loops once, and streaks a long trail " +
                    "to the right, one spark falling across it. Say meteor: it " +
                    "begins with mi.",
            ),
            KanaMnemonic(
                keyword = "MEAT",
                story = "A long strip of meat curls through one loop on the " +
                    "grill, and the skewer slides across it. Say meat: it begins " +
                    "with mi.",
            ),
            KanaMnemonic(
                keyword = "MITTEN",
                story = "The mitten string loops at one cuff and runs far to the " +
                    "other side, and a mitten dangles down over it. Mitten starts " +
                    "with mi.",
            ),
        ),
        "む" to listOf(
            KanaMnemonic(
                keyword = "MOO",
                story = "A cow leans over the fence rail, curls its nose in a " +
                    "small loop, and one ear twitches at the upper right. Moo " +
                    "sounds exactly like mu.",
            ),
            KanaMnemonic(
                keyword = "MOON",
                story = "A crescent moon hangs at the upper right while the river " +
                    "loops once and sweeps away under the bridge rail. Say moon: " +
                    "it begins with mu.",
            ),
            KanaMnemonic(
                keyword = "MOVIE",
                story = "Film drops past the shelf edge, winds once around a " +
                    "reel, and spools across the floor, one clip at the right. " +
                    "Say movie: it begins with mu.",
            ),
        ),
        "め" to listOf(
            KanaMnemonic(
                keyword = "MEND",
                story = "The mending thread sweeps through the needle's wide eye " +
                    "with no knot at its tail (and me means eye in Japanese). " +
                    "Mend starts with me.",
            ),
            KanaMnemonic(
                keyword = "MELON",
                story = "A curved leaf hangs down while the vine crosses it and " +
                    "swells into one plump round melon, open at the end. Melon " +
                    "starts with me.",
            ),
            KanaMnemonic(
                keyword = "MEDAL",
                story = "A short clasp curves down, and the ribbon sweeps across " +
                    "it and circles into one big round medal. Medal starts with " +
                    "me.",
            ),
        ),
        "も" to listOf(
            KanaMnemonic(
                keyword = "MOLE",
                story = "A mole digs straight down past two soil lines, then " +
                    "curves up to the right to peek out. Mole starts with mo.",
            ),
            KanaMnemonic(
                keyword = "MOAT",
                story = "The long first stroke drops like a castle wall and curls " +
                    "round into the moat, while two bridge beams cross it. Moat " +
                    "starts with mo.",
            ),
            KanaMnemonic(
                keyword = "MOLAR",
                story = "A molar's long root runs down past two gum lines and " +
                    "hooks up at the tip. Molar starts with mo.",
            ),
            KanaMnemonic(
                keyword = "MOP",
                story = "A mop stands tall, two drip lines crossing its handle, " +
                    "the head curling up at the floor. Mop starts with mo.",
            ),
        ),
        "や" to listOf(
            KanaMnemonic(
                keyword = "YACHT",
                story = "A yacht leans in the wind: the long mast slants down " +
                    "through the curving sail, and a tiny pennant flies at the " +
                    "top. Yacht starts with ya.",
            ),
            KanaMnemonic(
                keyword = "YARN",
                story = "A strand of yarn curls across the page, a long needle " +
                    "slants down through it, and a little snip sits on top. Yarn " +
                    "starts with ya.",
            ),
            KanaMnemonic(
                keyword = "YAM",
                story = "A plump yam hangs on a long skewer that slants down " +
                    "through it, with a little sprout at the top. Yam starts with " +
                    "ya.",
            ),
        ),
        "ゆ" to listOf(
            KanaMnemonic(
                keyword = "U-TURN",
                story = "A car sweeps through a wide U-turn, and the road line " +
                    "runs straight down through the loop. Say U-turn: it begins " +
                    "with yu.",
            ),
            KanaMnemonic(
                keyword = "UNICYCLE",
                story = "A unicycle's tall seat post runs straight down through " +
                    "its one big wheel to the ground. Say unicycle: it begins " +
                    "with yu.",
            ),
            KanaMnemonic(
                keyword = "UKULELE",
                story = "A ukulele's round body makes the big loop, and its long " +
                    "neck runs straight down through it. Say ukulele: it begins " +
                    "with yu.",
            ),
            KanaMnemonic(
                keyword = "UFO RIDE",
                story = "An amusement ride: the round gondola spins around the " +
                    "tall center pole. Say UFO: it begins with yu.",
            ),
        ),
        "よ" to listOf(
            KanaMnemonic(
                keyword = "YO-YO",
                story = "A yo-yo spins in the loop at the bottom of its long " +
                    "string, and a finger sticks out near the top to hold it. " +
                    "Yo-yo starts with yo.",
            ),
            KanaMnemonic(
                keyword = "YOGA",
                story = "A yogi sits in yoga with a straight back, legs folded " +
                    "into a loop beneath, one arm reaching out. Yoga starts with " +
                    "yo.",
            ),
            KanaMnemonic(
                keyword = "YOGURT",
                story = "A spoon stands tall in a yogurt jar, its tip stirring a " +
                    "loop at the bottom while the rim peeks out near the top. " +
                    "Yogurt starts with yo.",
            ),
        ),
        "ら" to listOf(
            KanaMnemonic(
                keyword = "RAMEN",
                story = "Hot ramen: the long stroke drops down and curls into a " +
                    "wide open bowl, and the short stroke above is one noodle " +
                    "lifted out. Ramen starts with ra.",
            ),
            KanaMnemonic(
                keyword = "RATTLE",
                story = "A baby's rattle: the long handle swings down into its " +
                    "round open head, and the short top stroke is a bead shaken " +
                    "loose. Rattle starts with ra.",
            ),
            KanaMnemonic(
                keyword = "RACCOON",
                story = "A raccoon curls up to nap: the long stroke wraps into " +
                    "its round open body, and the short flick above is one " +
                    "sideways ear. Raccoon starts with ra.",
            ),
        ),
        "り" to listOf(
            KanaMnemonic(
                keyword = "RIVER",
                story = "A river flows between two banks: the two strokes stand " +
                    "apart, and the longer right one sweeps away downstream. " +
                    "River starts with ri.",
                overlay = MnemonicOverlay(
                    paths = listOf(
                        // Long current lines flowing down between the banks.
                        // OVERLAY:BEGIN ri
                        OverlayPath("M 49 30 C 53 38 45 44 49 52 C 53 60 45 66 49 74"),
                        OverlayPath("M 55 40 C 58 47 52 53 55 60"),
                        // OVERLAY:END ri
                    ),
                ),
            ),
            KanaMnemonic(
                keyword = "REED",
                story = "Two reeds grow side by side in separate strokes, and the " +
                    "taller right one sweeps low in the wind. Reed starts with " +
                    "ri.",
            ),
            KanaMnemonic(
                keyword = "RIBBON",
                story = "Two ribbon ends hang side by side, cut in two separate " +
                    "strokes, and the longer right one curls in at its tip. " +
                    "Ribbon starts with ri.",
            ),
        ),
        "る" to listOf(
            KanaMnemonic(
                keyword = "ROOT",
                story = "One stroke zigzags like a 3, then the root sweeps around " +
                    "and curls into a knot at the end. Its twin ro has no knot. " +
                    "Root starts with ru.",
            ),
            KanaMnemonic(
                keyword = "ROOSTER",
                story = "A rooster in one stroke: the comb zigzags, the body " +
                    "sweeps around, and the tail curls into a loop at the end. " +
                    "Rooster starts with ru.",
            ),
            KanaMnemonic(
                keyword = "RUBY",
                story = "The single stroke swings a zigzag, then a wide curve, " +
                    "and closes a round ring at the bottom that holds a ruby. " +
                    "Ruby starts with ru.",
            ),
        ),
        "れ" to listOf(
            KanaMnemonic(
                keyword = "REPTILE",
                story = "A reptile climbs a pole: the second stroke crosses the " +
                    "upright, then its long tail sweeps out and flicks up to the " +
                    "right. Reptile starts with re.",
            ),
            KanaMnemonic(
                keyword = "WRESTLER",
                story = "A wrestler leans on the ring post: the second stroke " +
                    "wraps across the upright, then a leg kicks out to the right. " +
                    "Say wrestler: it begins with re.",
            ),
            KanaMnemonic(
                keyword = "REST",
                story = "A tired walker takes a rest against the straight pole, " +
                    "arms crossing it, one leg swung out to the right. Rest " +
                    "starts with re.",
            ),
        ),
        "ろ" to listOf(
            KanaMnemonic(
                keyword = "ROPE",
                story = "A rope in one stroke: it zigzags at the top, sweeps a " +
                    "wide open coil, and ends with no knot. Its twin ru ties " +
                    "the knot. Rope starts with ro.",
            ),
            KanaMnemonic(
                keyword = "ROAD",
                story = "A mountain road: it switchbacks sharply at the top, then " +
                    "rounds one wide bend and runs out open at the bottom, never " +
                    "looping. Road starts with ro.",
            ),
            KanaMnemonic(
                keyword = "ROBOT",
                story = "Drawn in one stroke, a wind-up robot: the antenna " +
                    "zigzags on top, then the round belly curve swings around " +
                    "without closing. Robot starts with ro.",
            ),
        ),
        "わ" to listOf(
            KanaMnemonic(
                keyword = "WASABI",
                story = "A chopstick stands tall. One stroke crosses it, then " +
                    "swings into a big round swirl of wasabi on the right. Wasabi " +
                    "starts with wa.",
            ),
            KanaMnemonic(
                keyword = "WAND",
                story = "A wand stands upright. Its spell crosses it and loops " +
                    "into one big round flourish on the right. Wand starts with " +
                    "wa.",
            ),
            KanaMnemonic(
                keyword = "WALTZ",
                story = "A dancer stands straight, then swings into one wide " +
                    "round waltz turn to the side. Waltz starts with wa.",
            ),
            KanaMnemonic(
                keyword = "WAVE",
                story = "A wave rears up beside the tall breakwater post and " +
                    "curls over on the right. Wave starts with wa.",
            ),
        ),
        "を" to listOf(
            KanaMnemonic(
                keyword = "WORM",
                story = "A worm arches over the bar and curls through the " +
                    "crossing below. It sounds like o, typed wo. You will meet " +
                    "it as the little word that marks an object.",
            ),
            KanaMnemonic(
                keyword = "OVERPASS",
                story = "One road runs flat on top while two more cross beneath. " +
                    "Overpass starts with o, typed wo. You will meet it as the " +
                    "little word that marks an object.",
            ),
            KanaMnemonic(
                keyword = "OTTER",
                story = "An otter floats on its back, paws crossed under the " +
                    "flat waterline. Otter starts with o, typed wo. You will " +
                    "meet it as the little word that marks an object.",
            ),
            KanaMnemonic(
                keyword = "ORIGAMI",
                story = "A flat fold on top, two creases crossing beneath: " +
                    "half-done origami. Origami starts with o, typed wo. You " +
                    "will meet it as the little word that marks an object.",
            ),
        ),
        "ん" to listOf(
            KanaMnemonic(
                keyword = "LETTER N",
                story = "This kana is simply a lowercase n written in one cursive " +
                    "motion, and n is exactly its sound.",
            ),
            KanaMnemonic(
                keyword = "SWAN",
                story = "A swan dips its neck into the water, back and tail " +
                    "rising behind. This kana only ever ends a sound. Swan " +
                    "ends with n.",
            ),
            KanaMnemonic(
                keyword = "DOLPHIN",
                story = "In one stroke a dolphin dives, touches the low water, " +
                    "and leaps up again in a smooth curve. Dolphin ends with n.",
            ),
            KanaMnemonic(
                keyword = "PENGUIN",
                story = "A penguin sleds down the long icy slope on its belly, " +
                    "dips, and glides up the far side. Penguin ends with n.",
            ),
        ),
    )

    /**
     * Every shipped mnemonic for [glyph]'s exact glyph, default first.
     * Empty for kana without mnemonics yet (derived kana, katakana).
     */
    fun candidatesFor(glyph: String): List<KanaMnemonic> =
        BASE_HIRAGANA[glyph].orEmpty()

    /**
     * The mnemonic shown for one reading unit's [glyph], or null. When the
     * learner promoted an alternate ([chosenKeyword], from the persisted
     * per-kana choices), that entry wins; an unknown or absent choice falls
     * back to the default. Exact-glyph match: voiced kana, yōon, katakana,
     * small and extended kana all return null until their derivation rules
     * land in [derive] (see the class doc for why they must not inherit the
     * base entry as-is).
     */
    fun mnemonicFor(glyph: String, chosenKeyword: String? = null): KanaMnemonic? {
        val candidates = candidatesFor(glyph)
        if (candidates.isEmpty()) return derive(glyph)
        return candidates.firstOrNull { it.keyword == chosenKeyword }
            ?: candidates.first()
    }

    /** The long-horizon derivation seam (class doc, phases 1-4). v1: none. */
    @Suppress("UNUSED_PARAMETER")
    private fun derive(glyph: String): KanaMnemonic? = null
}
