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

    private val KATAKANA: Map<String, List<KanaMnemonic>> = mapOf(
        "ア" to listOf(
            KanaMnemonic(keyword = "AXE", story = "An axe: a flat handle top and a blade swinging down and out to the left. Axe starts with a."),
            KanaMnemonic(keyword = "ARM", story = "A flexed arm: the flat shoulder line on top and the forearm swinging down and out to the left. Arm starts with a."),
        ),
        "イ" to listOf(
            KanaMnemonic(keyword = "IVY", story = "A sprig of ivy: one short leaf flicking off the top and a long stem trailing straight down. Ivy starts with i."),
            KanaMnemonic(keyword = "INCHWORM", story = "An inchworm: a little raised hump up top and a long body stretching straight down. Inchworm starts with i."),
        ),
        "ウ" to listOf(
            KanaMnemonic(keyword = "UFO", story = "A UFO hovering: a small dome on top of a wide saucer that curves down on both sides. Ufo starts with u."),
            KanaMnemonic(keyword = "URCHIN", story = "A sea urchin: a small spike on top of a round shell curving down each side. Urchin starts with u."),
        ),
        "エ" to listOf(
            KanaMnemonic(keyword = "ELEVATOR", story = "Stacked girders of an elevator shaft: a top rail, a middle post, and a bottom rail. Elevator starts with e."),
            KanaMnemonic(keyword = "ENGINEER", story = "An I-beam girder: a top flange, a central web, and a bottom flange, stamped by an engineer. Engineer starts with e."),
        ),
        "オ" to listOf(
            KanaMnemonic(keyword = "OARS", story = "Crossed oars leaning over a hooked rudder post at the stern. Oar sounds like o."),
            KanaMnemonic(keyword = "OLIVE", story = "An olive tree: crossed branches up top and a trunk that drops and hooks left into a gnarled root. Olive starts with o."),
        ),
        "カ" to listOf(
            KanaMnemonic(keyword = "KATANA", story = "A katana mid-slash: the blade sweeps right then hooks down, and a short guard cuts across it. Katana starts with ka."),
            KanaMnemonic(keyword = "KAYAKER", story = "A kayaker leaning hard: the paddle sweeps across and the hull hooks down. Kayaker starts with ka."),
        ),
        "キ" to listOf(
            KanaMnemonic(keyword = "KILT", story = "A kilt on a rail: two flat pleats crossed by a long pin dropping down. Kilt starts with ki."),
            KanaMnemonic(keyword = "KINDLING", story = "Kindling twigs: two short sticks crossed by a longer one that dips at the end. Kindling starts with ki."),
        ),
        "ク" to listOf(
            KanaMnemonic(keyword = "CROISSANT", story = "A croissant: a short flaky tip and a long crescent curving down to the left. Croissant sounds like ku."),
            KanaMnemonic(keyword = "KOOKABURRA", story = "A kookaburra's beak: a short brow and a long bill curving down to the left. Kookaburra starts with ku."),
        ),
        "ケ" to listOf(
            KanaMnemonic(keyword = "KENNEL", story = "A kennel: a short eave, a slanted roof, and a post dropping straight down. Kennel starts with ke."),
            KanaMnemonic(keyword = "KETCH", story = "A ketch boat: a short jib, a slanted mainsail, and a mast dropping straight down. Ketch starts with ke."),
        ),
        "コ" to listOf(
            KanaMnemonic(keyword = "CORNER", story = "Two walls meeting square: the top and the right side of a box corner drawn in one turn. Corner starts with ko."),
            KanaMnemonic(keyword = "KOTATSU", story = "A kotatsu table seen from the side: a flat top and a sturdy leg turning down. Kotatsu starts with ko."),
        ),
        "サ" to listOf(
            KanaMnemonic(keyword = "SAPLING", story = "A sapling: two crossed twigs and a stem curving up out of the soil to the right. Sapling starts with sa."),
            KanaMnemonic(keyword = "SATELLITE", story = "A satellite: crossed antennae and a mast curving up to catch a signal. Satellite starts with sa."),
        ),
        "シ" to listOf(
            KanaMnemonic(keyword = "SHINE", story = "Two glints and a long upward swoosh, like a sparkle catching light. Shine starts with the sound shi."),
            KanaMnemonic(keyword = "SHIATSU", story = "Two thumb presses up top and a long stroke sweeping up, a shiatsu massage. Shiatsu starts with shi."),
        ),
        "ス" to listOf(
            KanaMnemonic(keyword = "STORK", story = "A stork on one leg: a flat back and a single leg trailing out low behind it. Stork sounds like su."),
            KanaMnemonic(keyword = "SLIDE", story = "A playground slide: a flat top platform and a long ramp swooping down to the left. Slide sounds like su."),
        ),
        "セ" to listOf(
            KanaMnemonic(keyword = "SEED", story = "A seed sprouting: an upright sprout crossed low, with a leaf curling off to the right. Seed starts with se."),
            KanaMnemonic(keyword = "SEESAW", story = "A seesaw: a plank crossed on a pivot, one end curling up off the ground. Seesaw starts with se."),
        ),
        "ソ" to listOf(
            KanaMnemonic(keyword = "SOAR", story = "A gull soaring: a short wingtip up top and a long wing angling down to the left. Soar starts with so."),
            KanaMnemonic(keyword = "SOWER", story = "A sower flicking one seed up top and a long arm sweeping down to scatter more. Sower starts with so."),
        ),
        "タ" to listOf(
            KanaMnemonic(keyword = "TALON", story = "A hawk's talon: a curved claw tucked over a long toe sweeping down to the left. Talon starts with ta."),
            KanaMnemonic(keyword = "TARANTULA", story = "A tarantula: a curled front leg tucked over a long leg sweeping down to the left. Tarantula starts with ta."),
        ),
        "チ" to listOf(
            KanaMnemonic(keyword = "CHIMNEY", story = "A chimney stack: a slanted cap, a ledge, and a flue dropping with a small kink. Chimney starts with the sound chi."),
            KanaMnemonic(keyword = "CHAIR", story = "A chair in profile: a slanted back, a flat seat, and a leg dropping with a small hook. Chair starts with the sound chi."),
        ),
        "ツ" to listOf(
            KanaMnemonic(keyword = "TSAR", story = "A tsar's two medals and a sash sweeping down across the chest from the top right. Tsar starts with tsu."),
            KanaMnemonic(keyword = "TWINKLE", story = "Two sparkles up top and a swoosh curling down from the top right, stardust falling. Twinkle sounds like tsu."),
        ),
        "テ" to listOf(
            KanaMnemonic(keyword = "TENT", story = "A tent: a ridge line on top, a wider brim below, and a pole dropping through the middle. Tent starts with te."),
        ),
        "ト" to listOf(
            KanaMnemonic(keyword = "TOMAHAWK", story = "A tomahawk: a long handle with a single blade jutting out to the right near the top. Tomahawk starts with to."),
            KanaMnemonic(keyword = "TOTEM", story = "A totem pole: a tall post with one carved wing jutting out to the right near the top. Totem starts with to."),
        ),
        "ナ" to listOf(
            KanaMnemonic(keyword = "NARWHAL", story = "A narwhal: a flat back crossed by one long tusk bending as it juts forward. Narwhal starts with na."),
            KanaMnemonic(keyword = "NAP", story = "A cot to nap on: a flat frame crossed by one leg that bends under the weight. Nap starts with na."),
        ),
        "ニ" to listOf(
            KanaMnemonic(keyword = "NIGIRI", story = "Two flat slabs stacked in parallel, like two pieces of nigiri side by side. Nigiri starts with ni."),
            KanaMnemonic(keyword = "NEEDLES", story = "Two knitting needles laid flat and parallel, one above the other. Needle starts with the sound ni."),
        ),
        "ヌ" to listOf(
            KanaMnemonic(keyword = "NUNCHUCK", story = "Two nunchuck sticks: one laid across and the other swinging down through it. Nunchuck starts with the sound nu."),
            KanaMnemonic(keyword = "NOODLE", story = "A twist of noodle: one strand laid across and another looping down through it. Noodle starts with the sound nu."),
        ),
        "ネ" to listOf(
            KanaMnemonic(keyword = "NECKTIE", story = "A necktie on a hook: a knot, a crossbar, and the tie dangling in loose folds below. Necktie starts with ne."),
            KanaMnemonic(keyword = "NEWEL", story = "A newel post at the stairfoot: a knob, a rail joint, and tassels hanging below. Newel starts with ne."),
        ),
        "ノ" to listOf(
            KanaMnemonic(keyword = "NOSEDIVE", story = "One clean diagonal streak: a plane in a nosedive from the top right to the bottom left. Nosedive starts with no."),
            KanaMnemonic(keyword = "NOMAD", story = "A single slanted trail across the sand, a nomad's path from top right to lower left. Nomad starts with no."),
        ),
        "ハ" to listOf(
            KanaMnemonic(keyword = "HARE", story = "A hare's two ears, or two legs, splayed apart and leaning outward. Hare starts with ha."),
            KanaMnemonic(keyword = "HAKAMA", story = "Hakama trousers: two wide legs splaying apart as the wearer stands. Hakama starts with ha."),
        ),
        "ヒ" to listOf(
            KanaMnemonic(keyword = "HIP", story = "A seated figure: the back upright and one hip swung out flat along the ground. Hip starts with hi."),
            KanaMnemonic(keyword = "HIBACHI", story = "A hibachi grill in profile: an upright back and a wide base jutting out to cook on. Hibachi starts with hi."),
        ),
        "フ" to listOf(
            KanaMnemonic(keyword = "FANG", story = "A single curved fang: one smooth stroke hooking down from the top to a sharp point. Fang starts with the sound fu."),
            KanaMnemonic(keyword = "FEATHER", story = "A single feather: one smooth curve bending down to a fine tip. Feather sounds like fu."),
        ),
        "ヘ" to listOf(
            KanaMnemonic(keyword = "HENHOUSE", story = "A henhouse roof: a gentle rise and a longer slope down the back. Henhouse starts with he."),
            KanaMnemonic(keyword = "HEDGE", story = "A low hedge: a gentle slope up and a longer slope down, drawn as one gliding line. Hedge starts with he."),
        ),
        "ホ" to listOf(
            KanaMnemonic(keyword = "HOLLY", story = "A holly bush: crossed sprigs up top and two little leaves flicking out at the foot. Holly starts with ho."),
            KanaMnemonic(keyword = "HAWTHORN", story = "A hawthorn twig: crossed branches and two little thorns kicking out at the base. Hawthorn starts with the sound ho."),
        ),
        "マ" to listOf(
            KanaMnemonic(keyword = "MANTA", story = "A manta ray gliding: one wide wing sweeping down and curling into a tail. Manta starts with ma."),
            KanaMnemonic(keyword = "MAGNET", story = "A horseshoe magnet: one arm sweeping down and curling back toward the other. Magnet starts with ma."),
        ),
        "ミ" to listOf(
            KanaMnemonic(keyword = "MINCE", story = "Three quick knife strokes slanting down, mincing something fine. Mince starts with mi."),
            KanaMnemonic(keyword = "MINNOWS", story = "Three little minnows darting in a row, all slanting downstream. Minnow starts with mi."),
        ),
        "ム" to listOf(
            KanaMnemonic(keyword = "MUG", story = "A tilted mug: one side angling down and a little foot kicking out so it sits flat. Mug starts with mu."),
        ),
        "メ" to listOf(
            KanaMnemonic(keyword = "MELEE", story = "Two swords clashing in an X, the first blow of a melee. Melee starts with me."),
            KanaMnemonic(keyword = "MESH", story = "Two threads crossing in an X, the first knot of a woven mesh. Mesh starts with me."),
        ),
        "モ" to listOf(
            KanaMnemonic(keyword = "MOOSE", story = "A moose: two antler tines crossing a snout and a long neck curling at the base. Moose starts with mo."),
            KanaMnemonic(keyword = "MOTH", story = "A moth on a stem: two feelers crossing an antenna and a body curling at the tip. Moth starts with mo."),
        ),
        "ヤ" to listOf(
            KanaMnemonic(keyword = "YAK", story = "A yak: one horn hooking up and a shaggy body slashing down across it. Yak starts with ya."),
        ),
        "ユ" to listOf(
            KanaMnemonic(keyword = "YURT", story = "A yurt seen from the side: a short peak dropping onto a long flat floor. Yurt starts with the sound yu."),
            KanaMnemonic(keyword = "UTURN", story = "A boxy U-turn arrow: a short stub dropping into a long flat base. U-turn starts with the sound yu."),
        ),
        "ヨ" to listOf(
            KanaMnemonic(keyword = "YOYO", story = "A yoyo's frame: a spine on the left and three winds of string jutting out right. Yoyo starts with yo."),
            KanaMnemonic(keyword = "YOKE", story = "A yoke: a spine on the left with three rungs jutting out to the right, like a comb. Yoke starts with yo."),
        ),
        "ラ" to listOf(
            KanaMnemonic(keyword = "RACK", story = "A coat rack: a top shelf and a hooked arm curving down to hang a coat. Rack starts with ra."),
            KanaMnemonic(keyword = "RADAR", story = "A radar dish on a mast: a flat scanner up top and a hooked support curving down. Radar starts with ra."),
        ),
        "リ" to listOf(
            KanaMnemonic(keyword = "RIGGING", story = "Two rigging lines on a mast, the right one longer and curling at the bottom. Rigging starts with ri."),
            KanaMnemonic(keyword = "REEDS", story = "Two riverside reeds, the right one taller and curling at the tip. Reed sounds like ri."),
        ),
        "ル" to listOf(
            KanaMnemonic(keyword = "RUNNER", story = "A sprinter: a short trailing arm and a long leg curling up in full stride. Runner starts with ru."),
            KanaMnemonic(keyword = "RUINS", story = "Two broken pillars of old ruins, the right one taller and curling up at its tip. Ruins starts with ru."),
        ),
        "レ" to listOf(
            KanaMnemonic(keyword = "RECLINE", story = "A single reclining line: it drops steeply, then kicks back up to the right. Recline starts with re."),
            KanaMnemonic(keyword = "REEF", story = "A single reef slope: dropping steep, then kicking up to the surface. Reef sounds like re."),
        ),
        "ロ" to listOf(
            KanaMnemonic(keyword = "ROADBLOCK", story = "A square roadblock seen head-on: four barred sides closing off a box. Roadblock starts with ro."),
            KanaMnemonic(keyword = "RICEBOX", story = "A square rice box seen head-on: four wooden sides closing a cube. Ricebox sounds like ro."),
        ),
        "ワ" to listOf(
            KanaMnemonic(keyword = "WALRUS", story = "A walrus head: a flat brow and a jaw swinging down and in, tusks hidden. Walrus starts with wa."),
            KanaMnemonic(keyword = "WAIL", story = "A wide-open mouth mid-wail: the upper lip flat and the jaw swinging down and in. Wail starts with wa."),
        ),
        "ヲ" to listOf(
            KanaMnemonic(keyword = "OBELISK", story = "An old marker: two crossbars on a hooking post. This rare katakana still sounds like o."),
        ),
        "ン" to listOf(
            KanaMnemonic(keyword = "HORN", story = "A short nick up top and a long stroke swooping up from the bottom left, like a blaring horn. Horn ends with n."),
            KanaMnemonic(keyword = "YEN", story = "A short tick and a long stroke swooping up from the lower left, the tail of a scrawled yen sign. Yen ends with n."),
        ),
        "ガ" to listOf(
            KanaMnemonic(keyword = "GALAXY", story = "A spiral galaxy sweeps across the void, two bright stars flaring at its outer arm. Galaxy starts with ga."),
            KanaMnemonic(keyword = "GAVEL", story = "A judge's gavel mid-swing striking a block, two shockwaves pinging off at the top. Gavel starts with ga."),
        ),
        "ギ" to listOf(
            KanaMnemonic(keyword = "GIRDER", story = "A steel girder frame, cross-braced, with two rivets bolted at the top corner. Girder starts with gi."),
            KanaMnemonic(keyword = "GIGGLE", story = "A grinning face with a toothy line, two laugh-tears squeezing out at the corner. Giggle starts with gi."),
        ),
        "グ" to listOf(
            KanaMnemonic(keyword = "GUITAR", story = "A guitar's angular headstock hooks down, two tuning pegs jutting off at the top. Guitar starts with gu."),
            KanaMnemonic(keyword = "GUAVA", story = "A guava dangles bitten open, its stem curving down, two seeds flicked out at the corner. Guava starts with gu."),
        ),
        "ゲ" to listOf(
            KanaMnemonic(keyword = "GEODE", story = "A geode cracked open on a stand, two crystals glinting at its rim. Geode starts with ge."),
            KanaMnemonic(keyword = "GEAR", story = "A gear meshes on a bracket, teeth angling out, two bolts pinned at the top corner. Gear starts with ge."),
        ),
        "ゴ" to listOf(
            KanaMnemonic(keyword = "GONG", story = "A gong hangs square in its frame, two mallets crossed and ready at the top. Gong starts with go."),
            KanaMnemonic(keyword = "GONDOLA", story = "A gondola boxed against the dock, two mooring posts capped at the corner. Gondola starts with go."),
        ),
        "ザ" to listOf(
            KanaMnemonic(keyword = "ZAMBONI", story = "A zamboni scrapes the rink in crossing passes, two ice chips spraying at the corner. Zamboni starts with za."),
            KanaMnemonic(keyword = "ZIGZAG", story = "A zigzag bolt of lightning cuts the sky, two sparks splitting off at the top. Zigzag starts with za."),
        ),
        "ジ" to listOf(
            KanaMnemonic(keyword = "JINGLEBELL", story = "Two ticks of a jingle bell and a long ribbon swoosh, two chimes ringing at the top. Jingle starts with ji."),
            KanaMnemonic(keyword = "JITTERBUG", story = "A jitterbug dancer kicks two heels up top and swings one long arm down. Jitterbug starts with ji."),
        ),
        "ズ" to listOf(
            KanaMnemonic(keyword = "ZIPLINE", story = "A rider zips down a long zipline cable, two sparks flying off the pulley at the top. Zipline starts with zu."),
            KanaMnemonic(keyword = "ZULU", story = "A Zulu shield stands flat-topped with one long spear beside it, two beads strung at the corner. Zulu starts with zu."),
        ),
        "ゼ" to listOf(
            KanaMnemonic(keyword = "ZERO", story = "A scoreboard reset: a bar, a post, a curved base, two zeros blinking at the corner. Zero starts with ze."),
            KanaMnemonic(keyword = "ZEBU", story = "A zebu ox with a humped shoulder and a long dewlap, two horns tipped at the top corner. Zebu starts with ze."),
        ),
        "ゾ" to listOf(
            KanaMnemonic(keyword = "ZOMBIE", story = "A zombie lurches, one arm short and stiff, a long leg dragging, two flies buzzing at its head. Zombie starts with zo."),
            KanaMnemonic(keyword = "ZORI", story = "A zori sandal from the side: one thong up top and a long sole, two straps pinned at the corner. Zori starts with zo."),
        ),
        "ダ" to listOf(
            KanaMnemonic(keyword = "DACHSHUND", story = "A dachshund: a short front paw tucked over a long low body, two ears flopping at its head. Dachshund starts with da."),
            KanaMnemonic(keyword = "DAISY", story = "A daisy droops on its stem, one leaf tucked up top, two petals fallen at the corner. Daisy starts with da."),
        ),
        "ヂ" to listOf(
            KanaMnemonic(keyword = "DIADEM", story = "A slanted diadem crown on a level band, a jewel hanging, two gems set at the corner. Diadem starts with di."),
            KanaMnemonic(keyword = "DINGO", story = "A dingo lifts a slanted ear over a level muzzle and a long leg, two paw-prints at the corner. Dingo starts with di."),
        ),
        "ヅ" to listOf(
            KanaMnemonic(keyword = "DEWDROP", story = "Two ticks of dew over a long leaf-blade sweeping down, two more drops beading at the tip. Dew starts with du."),
            KanaMnemonic(keyword = "DUOMO", story = "A duomo's twin spires above a sweeping nave roof, two bells hung at the top. Duomo starts with du."),
        ),
        "デ" to listOf(
            KanaMnemonic(keyword = "DECK", story = "A ship's deck: two rails and a mast hooking a sail, two flags snapping at the top. Deck starts with de."),
            KanaMnemonic(keyword = "DELI", story = "A deli counter: two shelf lines and a hook, two salamis dangling at the corner. Deli starts with de."),
        ),
        "ド" to listOf(
            KanaMnemonic(keyword = "DONKEY", story = "A donkey stands stiff, one ear flicked right off the tall head, two flies at its crown. Donkey starts with do."),
            KanaMnemonic(keyword = "DONUT", story = "A donut skewered on a post, two sprinkles flicked off at the top corner. Donut starts with do."),
        ),
        "バ" to listOf(
            KanaMnemonic(keyword = "BAMBOO", story = "Two bamboo stalks lean apart from one clump, two leaves fluttering at the top. Bamboo starts with ba."),
            KanaMnemonic(keyword = "BAOBAB", story = "A baobab trunk splits into two thick branches, two birds perched at the top corner. Baobab starts with ba."),
        ),
        "ビ" to listOf(
            KanaMnemonic(keyword = "BINOCULARS", story = "Binoculars tip up in a curved case, two eyecups poking out at the top. Binocular starts with bi."),
            KanaMnemonic(keyword = "BISCUIT", story = "A biscuit bends soft in a curve, two crumbs flaking off at the top corner. Biscuit starts with bi."),
        ),
        "ブ" to listOf(
            KanaMnemonic(keyword = "BUOY", story = "A buoy bobs in one round curve, two warning flags flapping at its top. Buoy starts with bu."),
            KanaMnemonic(keyword = "BUGLE", story = "A bugle curves in one long loop, two puffs of sound blasting from the bell at the top. Bugle starts with bu."),
        ),
        "ベ" to listOf(
            KanaMnemonic(keyword = "BERET", story = "A beret sits at a jaunty slope, two pompom threads dangling at the peak. Beret starts with be."),
            KanaMnemonic(keyword = "BEAVER", story = "A beaver's flat tail slaps in a shallow curve, two teeth showing at the corner. Beaver starts with be."),
        ),
        "ボ" to listOf(
            KanaMnemonic(keyword = "BOBSLED", story = "A bobsled frame cross-braced on runners, two helmets bobbing at the top corner. Bobsled starts with bo."),
            KanaMnemonic(keyword = "BOBBIN", story = "A bobbin of thread on a spindle, crossed winds, two loose ends flicking at the top. Bobbin starts with bo."),
        ),
        "パ" to listOf(
            KanaMnemonic(keyword = "PAGODA", story = "A pagoda's tiered eaves splay apart, one round lantern hung as a circle at the top. Pagoda starts with pa."),
            KanaMnemonic(keyword = "PARROT", story = "A parrot spreads two wings apart, one round eye ringed at the top corner. Parrot starts with pa."),
        ),
        "ピ" to listOf(
            KanaMnemonic(keyword = "PICKAXE", story = "A pickaxe leans upright with a curved head, one round boulder circled at the top. Pickaxe starts with pi."),
            KanaMnemonic(keyword = "PIPER", story = "A piper's flute curves up, one round note circled just above it. Piper starts with pi."),
        ),
        "プ" to listOf(
            KanaMnemonic(keyword = "PUFFIN", story = "A puffin dives in a curved swoop, one round fish circled at its beak. Puffin starts with pu."),
            KanaMnemonic(keyword = "PUMA", story = "A puma coils to pounce, its back one loose curve, one round moon circled above. Puma starts with pu."),
        ),
        "ペ" to listOf(
            KanaMnemonic(keyword = "PELICAN", story = "A pelican skims low in a long curve, one round fish circled in its pouch. Pelican starts with pe."),
            KanaMnemonic(keyword = "PETAL", story = "A single petal curves off a stem, one round dewdrop circled at its tip. Petal starts with pe."),
        ),
        "ポ" to listOf(
            KanaMnemonic(keyword = "POGO", story = "A pogo stick with crossed footpegs and a long spring, one round bounce-puff circled at the top. Pogo starts with po."),
            KanaMnemonic(keyword = "PORTAL", story = "A shrine portal of crossed beams, one round paper lantern circled at the top. Portal starts with po."),
        ),
        "キャ" to listOf(
            KanaMnemonic(keyword = "CAYENNE", story = "A big pepper plant with a small cayenne pod at its lower right. Cayenne sounds like kya."),
        ),
        "キュ" to listOf(
            KanaMnemonic(keyword = "CUBE", story = "A big rack with a small ice cube stacked at its lower corner. Cube sounds like kyu."),
            KanaMnemonic(keyword = "QUEUE", story = "A big counter with a small queue of people at its lower right. Queue sounds like kyu."),
        ),
        "キョ" to listOf(
            KanaMnemonic(keyword = "KYOTO", story = "A big temple gate with a small stone lantern at its lower right, old Kyoto. Kyoto starts with kyo."),
        ),
        "シャ" to listOf(
            KanaMnemonic(keyword = "SHAMPOO", story = "A big bottle with a small shampoo bubble at its lower right. Shampoo starts with sha."),
            KanaMnemonic(keyword = "SHAWL", story = "A big coat with a small shawl draped at its lower corner. Shawl sounds like sha."),
        ),
        "シュ" to listOf(
            KanaMnemonic(keyword = "SHURIKEN", story = "A big ninja with a small shuriken star ready at its lower right. Shuriken starts with shu."),
            KanaMnemonic(keyword = "SUGAR", story = "A big bowl with a small sugar cube at its lower corner. Sugar holds the sound shu."),
        ),
        "ショ" to listOf(
            KanaMnemonic(keyword = "SHOWROOM", story = "A big garage with a small showroom car at its lower corner. Showroom starts with sho."),
            KanaMnemonic(keyword = "SHORTS", story = "A big hanger with a small pair of shorts hung at its lower right. Shorts start with sho."),
        ),
        "チャ" to listOf(
            KanaMnemonic(keyword = "CHAMPION", story = "A big podium with a small champion trophy at its lower right. Champion starts with cha."),
            KanaMnemonic(keyword = "CHALLAH", story = "A big oven with a small challah loaf at its lower corner. Challah sounds like cha."),
        ),
        "チュ" to listOf(
            KanaMnemonic(keyword = "CHUNK", story = "A big log with a small chunk split off at its lower corner. Chunk starts with chu."),
            KanaMnemonic(keyword = "CHURRO", story = "A big fryer with a small churro twist at its lower right. Churro starts with chu."),
        ),
        "チョ" to listOf(
            KanaMnemonic(keyword = "CHOWDER", story = "A big pot with a small chowder bowl ladled at its lower right. Chowder starts with cho."),
            KanaMnemonic(keyword = "CHORUS", story = "A big stage with a small chorus line at its lower corner. Chorus starts with cho."),
        ),
        "ニャ" to listOf(
            KanaMnemonic(keyword = "LASAGNA", story = "A big tray with a small lasagna square cut at its lower right. Lasagna holds the sound nya."),
        ),
        "ニュ" to listOf(
            KanaMnemonic(keyword = "NEURON", story = "A big brain with a small neuron sparking at its lower corner. Neuron sounds like nyu."),
        ),
        "ニョ" to listOf(
            KanaMnemonic(keyword = "MIGNON", story = "A big plate with a small filet mignon at its lower right. Mignon holds the sound nyo."),
        ),
        "ヒャ" to listOf(
            KanaMnemonic(keyword = "HYBRID", story = "A big engine with a small hybrid battery at its lower corner. Hybrid starts with hya."),
        ),
        "ヒュ" to listOf(
            KanaMnemonic(keyword = "HUMANOID", story = "A big robot with a small humanoid drone at its lower right. Humanoid sounds like hyu."),
        ),
        "ヒョ" to listOf(
            KanaMnemonic(keyword = "LEOPARD", story = "A big tree with a small spotted leopard resting at its lower right. In Japanese the leopard is a hyo."),
        ),
        "ミャ" to listOf(
            KanaMnemonic(keyword = "MYALGIA", story = "A big muscle with a small ache flaring at its lower corner, myalgia. Myalgia starts with mya."),
        ),
        "ミュ" to listOf(
            KanaMnemonic(keyword = "MEWL", story = "A big cat with a small kitten mewling at its lower right. Mewl sounds like myu."),
        ),
        "ミョ" to listOf(
            KanaMnemonic(keyword = "MYOGA", story = "A big garden with a small myoga ginger shoot at its lower right. Myoga starts with myo."),
        ),
        "リャ" to listOf(
            KanaMnemonic(keyword = "RIYAL", story = "A big vault with a small riyal coin at its lower corner. Riyal sounds like rya."),
        ),
        "リュ" to listOf(
            KanaMnemonic(keyword = "RUBLE", story = "A big till with a small ruble note at its lower corner. Ruble sounds like ryu."),
        ),
        "リョ" to listOf(
            KanaMnemonic(keyword = "RIOJA", story = "A big cellar with a small Rioja bottle at its lower right. Rioja sounds like ryo."),
        ),
        "ギャ" to listOf(
            KanaMnemonic(keyword = "GALLERY", story = "A big wall with a small gallery frame hung at its lower corner. Gallery sounds like gya."),
        ),
        "ギュ" to listOf(
            KanaMnemonic(keyword = "GUINEA", story = "A big cage with a small guinea pig at its lower right. Guinea sounds like gyu."),
        ),
        "ギョ" to listOf(
            KanaMnemonic(keyword = "GYRO", story = "A big pita wraps with a small gyro slice at its lower corner. Gyro sounds like gyo."),
        ),
        "ジャ" to listOf(
            KanaMnemonic(keyword = "JAB", story = "A big glove with a small jab-spark flicking at its lower corner. Jab starts with ja."),
            KanaMnemonic(keyword = "JASMINE", story = "A big trellis with a small jasmine flower at its lower right. Jasmine starts with ja."),
        ),
        "ジュ" to listOf(
            KanaMnemonic(keyword = "JUNGLE", story = "A big canopy with a small jungle vine dropping at its lower corner. Jungle starts with ju."),
            KanaMnemonic(keyword = "JUJUBE", story = "A big branch with a small jujube berry at its lower right. Jujube starts with ju."),
        ),
        "ジョ" to listOf(
            KanaMnemonic(keyword = "JOURNAL", story = "A big desk with a small journal open at its lower corner. Journal starts with jo."),
        ),
        "ビャ" to listOf(
            KanaMnemonic(keyword = "BAYONET", story = "A big rifle with a small bayonet fixed at its lower right. Bayonet sounds like bya."),
        ),
        "ビュ" to listOf(
            KanaMnemonic(keyword = "BEAUTY", story = "A big salon with a small beauty compact at its lower corner. Beauty sounds like byu."),
        ),
        "ビョ" to listOf(
            KanaMnemonic(keyword = "BIOME", story = "A big terrarium with a small biome dome at its lower right. Biome sounds like byo."),
        ),
        "ピャ" to listOf(
            KanaMnemonic(keyword = "PIANO", story = "A big grand piano with a small pianola roll at its lower corner. Piano sounds like pya."),
        ),
        "ピュ" to listOf(
            KanaMnemonic(keyword = "PUREE", story = "A big blender with a small puree cup at its lower right. Puree sounds like pyu."),
        ),
        "ピョ" to listOf(
            KanaMnemonic(keyword = "PYONGYANG", story = "A big map with a small Pyongyang pin at its lower right. Pyongyang starts with pyo."),
        ),
        "ァ" to listOf(
            KanaMnemonic(keyword = "APPLESEED", story = "A single appleseed, tiny beside the whole apple. Appleseed starts with a."),
        ),
        "ィ" to listOf(
            KanaMnemonic(keyword = "INCH", story = "One small inch marked at the corner of a ruler. Inch starts with i."),
        ),
        "ゥ" to listOf(
            KanaMnemonic(keyword = "UPTICK", story = "A small uptick, one tiny notch rising in the corner. Uptick starts with u."),
        ),
        "ェ" to listOf(
            KanaMnemonic(keyword = "EMBER", story = "One small ember glowing in the corner of the hearth. Ember starts with e."),
        ),
        "ォ" to listOf(
            KanaMnemonic(keyword = "OPAL", story = "A tiny opal chip glinting in the corner. Opal sounds like o."),
        ),
        "ヰ" to listOf(
            KanaMnemonic(keyword = "WINCH", story = "A rusty winch on an old dock, once read wi. Winch starts with wi."),
        ),
        "ヱ" to listOf(
            KanaMnemonic(keyword = "WEDGE", story = "An old wooden wedge left in a doorframe, once read we. Wedge starts with we."),
        ),
        "ヴ" to listOf(
            KanaMnemonic(keyword = "VOLT", story = "A small volt of current sparking in the corner. Volt sounds like vu."),
        ),
        "ウィ" to listOf(
            KanaMnemonic(keyword = "WINK", story = "A big eye with a small wink-line flicking at its lower corner. Wink starts with wi."),
            KanaMnemonic(keyword = "WIFI", story = "A big router with a small wifi signal blinking at its lower right. Wifi starts with wi."),
        ),
        "ウェ" to listOf(
            KanaMnemonic(keyword = "WEDDING", story = "A big cake with a small wedding topper at its lower corner. Wedding starts with we."),
            KanaMnemonic(keyword = "WEBSITE", story = "A big screen with a small website tab open at its lower right. Website starts with we."),
        ),
        "ウォ" to listOf(
            KanaMnemonic(keyword = "WALKMAN", story = "A big belt clip with a small Walkman hooked at its lower right. Walkman starts with wo."),
            KanaMnemonic(keyword = "WATT", story = "A big bulb with a small watt-spark glowing at its lower corner. Watt sounds like wo."),
        ),
        "ヴァ" to listOf(
            KanaMnemonic(keyword = "VAMPIRE", story = "A big cape with a small vampire fang at its lower corner. Vampire starts with va."),
            KanaMnemonic(keyword = "VACUUM", story = "A big carpet with a small vacuum nozzle at its lower right. Vacuum starts with va."),
        ),
        "ヴィ" to listOf(
            KanaMnemonic(keyword = "VIOLIN", story = "A big bow with a small violin scroll at its lower right. Violin starts with vi."),
            KanaMnemonic(keyword = "VISA", story = "A big passport with a small visa stamp at its lower corner. Visa starts with vi."),
        ),
        "ヴェ" to listOf(
            KanaMnemonic(keyword = "VEST", story = "A big coat with a small vest pocket at its lower corner. Vest starts with ve."),
            KanaMnemonic(keyword = "VELVET", story = "A big drape with a small velvet swatch at its lower right. Velvet starts with ve."),
        ),
        "ヴォ" to listOf(
            KanaMnemonic(keyword = "VOLCANO", story = "A big mountain with a small volcano vent smoking at its lower right. Volcano starts with vo."),
            KanaMnemonic(keyword = "VOTE", story = "A big box with a small vote slip dropped at its lower corner. Vote sounds like vo."),
        ),
        "シェ" to listOf(
            KanaMnemonic(keyword = "SHELL", story = "A big beach with a small seashell at its lower corner. Shell sounds like she."),
            KanaMnemonic(keyword = "CHEF", story = "A big kitchen with a small chef hat at its lower right. Chef sounds like she."),
        ),
        "ジェ" to listOf(
            KanaMnemonic(keyword = "JET", story = "A big runway with a small jet taking off at its lower right. Jet starts with je."),
            KanaMnemonic(keyword = "JELLY", story = "A big jar with a small jelly blob wobbling at its lower corner. Jelly starts with je."),
        ),
        "チェ" to listOf(
            KanaMnemonic(keyword = "CHESS", story = "A big board with a small chess pawn at its lower right. Chess starts with che."),
            KanaMnemonic(keyword = "CHERRY", story = "A big cake with a small cherry on its lower corner. Cherry starts with che."),
        ),
        "ティ" to listOf(
            KanaMnemonic(keyword = "TEA", story = "A big pot with a small tea bag steeping at its lower corner. Tea sounds like ti."),
            KanaMnemonic(keyword = "PARTY", story = "A big banner with a small party hat at its lower right, the ti you hear in party. Ti."),
        ),
        "ディ" to listOf(
            KanaMnemonic(keyword = "DISCO", story = "A big floor with a small disco ball spinning at its lower right. Disco starts with di."),
            KanaMnemonic(keyword = "DIGITAL", story = "A big clock with a small digital readout at its lower corner. Digital starts with di."),
        ),
        "トゥ" to listOf(
            KanaMnemonic(keyword = "TOUR", story = "A big bus with a small tour flag at its lower right. Tour starts with the sound tu."),
            KanaMnemonic(keyword = "TOOTH", story = "A big jaw with a small tooth poking at its lower corner. Tooth sounds like tu."),
        ),
        "ドゥ" to listOf(
            KanaMnemonic(keyword = "DOODLE", story = "A big pad with a small doodle scribbled in its lower corner. Doodle starts with du."),
            KanaMnemonic(keyword = "DUET", story = "A big stage with a small duet mic at its lower right. Duet starts with du."),
        ),
        "ファ" to listOf(
            KanaMnemonic(keyword = "FAMILY", story = "A big house with a small family photo at its lower corner. Family starts with fa."),
            KanaMnemonic(keyword = "FAN", story = "A big stadium with a small fan waving at its lower right. Fan starts with fa."),
        ),
        "フィ" to listOf(
            KanaMnemonic(keyword = "FILM", story = "A big reel with a small strip of film at its lower right. Film starts with fi."),
            KanaMnemonic(keyword = "FIGURE", story = "A big shelf with a small figure toy at its lower corner. Figure starts with fi."),
        ),
        "フェ" to listOf(
            KanaMnemonic(keyword = "FERRY", story = "A big harbor with a small ferry pulling in at its lower right. Ferry starts with fe."),
            KanaMnemonic(keyword = "FESTIVAL", story = "A big stage with a small festival lantern at its lower corner. Festival starts with fe."),
        ),
        "フォ" to listOf(
            KanaMnemonic(keyword = "FORK", story = "A big plate with a small fork at its lower right. Fork starts with fo."),
            KanaMnemonic(keyword = "PHOTO", story = "A big album with a small photo tucked in its lower corner. Photo sounds like fo."),
        ),
        "ツァ" to listOf(
            KanaMnemonic(keyword = "PIZZA", story = "A big oven with a small pizza slice at its lower right, the tsa you hear in pizza. Tsa."),
            KanaMnemonic(keyword = "MOZZARELLA", story = "A big pie with a small mozzarella blob melting at its lower corner. Mozzarella holds the sound tsa."),
        ),
        "ツィ" to listOf(
            KanaMnemonic(keyword = "TZIMMES", story = "A big pot with a small tzimmes stew at its lower right. Tzimmes starts with the sound tsi."),
        ),
        "ツェ" to listOf(
            KanaMnemonic(keyword = "PRETZEL", story = "A big cart with a small pretzel twist hung at its lower right. Pretzel holds the sound tse."),
        ),
        "ツォ" to listOf(
            KanaMnemonic(keyword = "MATZO", story = "A big plate with a small matzo cracker at its lower right. Matzo holds the sound tso."),
        ),
    )

    private val HIRAGANA_EXTRA: Map<String, List<KanaMnemonic>> = mapOf(
        "が" to listOf(
            KanaMnemonic(keyword = "GARGOYLE", story = "A gargoyle crouches on a ledge, wings tucked, two beady eyes glaring from the top corner. Its growl is ga."),
            KanaMnemonic(keyword = "GARLIC", story = "A garlic bulb on its stalk, two papery cloves flaked off at the upper corner. Garlic starts with ga."),
        ),
        "ぎ" to listOf(
            KanaMnemonic(keyword = "GILLS", story = "A fish stood on its tail, two gill-slits flaring high at the corner as it breathes. Gills start with gi."),
            KanaMnemonic(keyword = "GIFT", story = "A wrapped gift: ribbon crossing the box and a bow with two loops sticking out at the top corner. Gift starts with gi."),
        ),
        "ぐ" to listOf(
            KanaMnemonic(keyword = "GULL", story = "A gull's hooked beak gaping wide, two crumbs it just tossed hanging at the corner. Gull starts with gu."),
            KanaMnemonic(keyword = "GOOSE", story = "A goose folds its neck into a tight bend, two loose down feathers drifting off the top. Goose starts with the sound gu."),
        ),
        "げ" to listOf(
            KanaMnemonic(keyword = "GEYSER", story = "A geyser shoots up between two rocks, and two droplets fly off at the very top. Geyser starts with ge."),
            KanaMnemonic(keyword = "GECKO", story = "A gecko clings upside down, legs splayed, two sticky toe-pads gripping the top corner. Gecko starts with ge."),
        ),
        "ご" to listOf(
            KanaMnemonic(keyword = "GOBLET", story = "A goblet's bowl and stem in two neat strokes, two wine drops beaded at the rim. Goblet starts with go."),
            KanaMnemonic(keyword = "GOGGLES", story = "A pair of goggles: two lenses stacked, with two strap-buckles clipped at the corner. Goggles start with go."),
        ),
        "ざ" to listOf(
            KanaMnemonic(keyword = "ZEBRA", story = "A zebra bows its striped neck, the mane sweeping down and hooking, two flies flicking at its shoulder. Zebra starts with za."),
            KanaMnemonic(keyword = "ZAP", story = "A live wire hooks down hard and two electric sparks jump off it at the top. Zap starts with za."),
        ),
        "じ" to listOf(
            KanaMnemonic(keyword = "JIGSAW", story = "A jigsaw blade drops straight and hooks up at the end, two cut chips flying off at the top. Jigsaw starts with ji."),
            KanaMnemonic(keyword = "JELLYFISH", story = "A jellyfish trails one long tentacle that curls up at the tip, two stingers dangling from its bell. Jelly starts with ji."),
        ),
        "ず" to listOf(
            KanaMnemonic(keyword = "ZUCCHINI", story = "A zucchini dangles from a curling vine, two little blossoms still clinging near the stem. Zucchini starts with zu."),
            KanaMnemonic(keyword = "ZOOM", story = "A race car loops the track leaving a long tail, two speed-lines trailing off the top. Zoom starts with zu."),
        ),
        "ぜ" to listOf(
            KanaMnemonic(keyword = "ZEN GARDEN", story = "A raked zen garden: an upright rake, a line drawn low, and two pebbles set apart at the corner. Zen starts with ze."),
            KanaMnemonic(keyword = "ZEPHYR", story = "A weathervane on a post, its arm swung by two puffs of a zephyr breeze at the top. Zephyr starts with ze."),
        ),
        "ぞ" to listOf(
            KanaMnemonic(keyword = "ZORRO", story = "Zorro slashes a curling mark, then adds two rapier nicks in the corner as his signature. Zorro starts with zo."),
            KanaMnemonic(keyword = "ZODIAC", story = "A shooting-star trail curves across the zodiac, two little stars sparking off the top. Zodiac starts with zo."),
        ),
        "だ" to listOf(
            KanaMnemonic(keyword = "DARTS", story = "A dartboard stand: a cross-brace and a curved leg, two darts stuck in the top corner. Darts start with da."),
            KanaMnemonic(keyword = "DAGGER", story = "A dagger crossed over a curved sheath, two drops of ink running off the blade at the hilt. Dagger starts with da."),
        ),
        "ぢ" to listOf(
            KanaMnemonic(keyword = "DIVER", story = "A diver arcs off the board and hooks into the water, two bubbles rising at the top. Diver starts with di."),
            KanaMnemonic(keyword = "DINGHY", story = "A dinghy tips on a wave, its keel hooking up, two oars splashing at the stern corner. Dinghy starts with di."),
        ),
        "づ" to listOf(
            KanaMnemonic(keyword = "DUNE", story = "A wind-carved dune curling like a wave, two tumbleweeds bouncing off its crest. Dune starts with du."),
            KanaMnemonic(keyword = "DUGOUT", story = "A canoe hollowed from a log, curving low, two paddles resting across the top corner. Dugout starts with du."),
        ),
        "で" to listOf(
            KanaMnemonic(keyword = "DELTA", story = "A river delta curves out to sea, and two little islands sit in its mouth at the top. Delta starts with de."),
            KanaMnemonic(keyword = "DERBY", story = "A bowler derby hat, its brim curling under, with two feathers tucked at the band. Derby starts with de."),
        ),
        "ど" to listOf(
            KanaMnemonic(keyword = "DOMINO", story = "A domino standing on end, and two pips dotted at its top corner. Domino starts with do."),
            KanaMnemonic(keyword = "DODO", story = "A dodo stands stiff and tall, a stubby wing out to one side, two feathers at its crown. Dodo starts with do."),
        ),
        "ば" to listOf(
            KanaMnemonic(keyword = "BANJO", story = "A banjo: the long neck and round body, two tuning pegs sticking out at the top. Banjo starts with ba."),
            KanaMnemonic(keyword = "BAT", story = "A bat hangs from a beam by one folded wing, two little ears poking up at the corner. Bat starts with ba."),
        ),
        "び" to listOf(
            KanaMnemonic(keyword = "BISON", story = "A bison lowers its humped back in a big curve, two horns curling up at the top corner. Bison starts with bi."),
            KanaMnemonic(keyword = "BEEHIVE", story = "A domed beehive curving to the ground, two bees hovering at the entrance corner. Beehive starts with bi."),
        ),
        "ぶ" to listOf(
            KanaMnemonic(keyword = "BUBBLE", story = "A wand blows one big looping bubble, two tiny bubbles pinching off at the corner. Bubble starts with bu."),
            KanaMnemonic(keyword = "BUTTERFLY", story = "A butterfly with dappled wings mid-flutter, two antennae curling off at the top. Butterfly starts with bu."),
        ),
        "べ" to listOf(
            KanaMnemonic(keyword = "BELL", story = "A hand bell swings in a smooth arc, two clangs ringing off at the top corner. Bell starts with be."),
            KanaMnemonic(keyword = "BEAK", story = "A pelican's beak dips in a long shallow curve, two fish flipping at the tip. Beak starts with be."),
        ),
        "ぼ" to listOf(
            KanaMnemonic(keyword = "BONSAI", story = "A bonsai tree: crossed branches over a pot, two clipped leaves resting at the top corner. Bonsai starts with bo."),
            KanaMnemonic(keyword = "BONGO", story = "Two bongo drums lashed to a stand, two drumsticks crossed above them. Bongo starts with bo."),
        ),
        "ぱ" to listOf(
            KanaMnemonic(keyword = "PARASOL", story = "A parasol on a pole, its round canopy balanced as one bright circle at the top. Parasol starts with pa."),
            KanaMnemonic(keyword = "PANDA", story = "A panda sits munching bamboo, one round ear circled at the top corner. Panda starts with pa."),
        ),
        "ぴ" to listOf(
            KanaMnemonic(keyword = "PIG", story = "A pig's curved back and snout, one round curly tail circled at the top corner. Pig starts with pi."),
            KanaMnemonic(keyword = "PIN", story = "A safety pin bent in a long curve, its round clasp circled at the top. Pin starts with pi."),
        ),
        "ぷ" to listOf(
            KanaMnemonic(keyword = "PUFFERFISH", story = "A pufferfish balloons up round, fins flicking, one bubble circled at the top corner. Puffer starts with pu."),
            KanaMnemonic(keyword = "PUDDLE", story = "A splash kicks up feathery drops, one round raindrop circled just above it. Puddle starts with pu."),
        ),
        "ぺ" to listOf(
            KanaMnemonic(keyword = "PEBBLE", story = "A pebble skips across a flat arc of water, one round ripple circled at the top. Pebble starts with pe."),
            KanaMnemonic(keyword = "PEAK", story = "A mountain peak in one clean slope, one round sun circled at its shoulder. Peak starts with pe."),
        ),
        "ぽ" to listOf(
            KanaMnemonic(keyword = "POPCORN", story = "A popcorn plant of crossed stalks, one round kernel popped and circled at the top. Popcorn starts with po."),
            KanaMnemonic(keyword = "POODLE", story = "A poodle sits with crossed legs and a curly coat, one round pompom circled at its tail. Poodle starts with po."),
        ),
        "きゃ" to listOf(
            KanaMnemonic(keyword = "KAYAK", story = "A big paddler leans back and a small kayak tucks at their heel, slicing away. Kayak sounds like kya."),
        ),
        "きゅ" to listOf(
            KanaMnemonic(keyword = "CUPID", story = "A big bow bends and a small cupid perches at its lower tip, taking aim. Cupid sounds like kyu."),
            KanaMnemonic(keyword = "CUCUMBER", story = "A big vine curls with a small cucumber hanging at its lower right. Cucumber sounds like kyu."),
        ),
        "きょ" to listOf(
            KanaMnemonic(keyword = "KIOSK", story = "A big signboard stands with a small kiosk booth tucked at its lower corner. Kiosk sounds like kyo."),
            KanaMnemonic(keyword = "CURIO", story = "A big cabinet with a small curio figurine on its lower shelf. Curio sounds like kyo."),
        ),
        "しゃ" to listOf(
            KanaMnemonic(keyword = "SHARK", story = "A big fin cuts the water with a small shark tail flicking at its lower right. Shark sounds like sha."),
            KanaMnemonic(keyword = "SHACK", story = "A big roof leans with a small lean-to shack at its lower corner. Shack sounds like sha."),
        ),
        "しゅ" to listOf(
            KanaMnemonic(keyword = "SHOE", story = "A big lace loops down with a small shoe tucked at its lower right. Shoe sounds like shu."),
            KanaMnemonic(keyword = "SHUTTLE", story = "A big loom with a small shuttle darting across its lower edge. Shuttle starts with shu."),
        ),
        "しょ" to listOf(
            KanaMnemonic(keyword = "SHOWER", story = "A big pipe bends and a small showerhead sprays at its lower corner. Shower starts with sho."),
            KanaMnemonic(keyword = "SHOGUN", story = "A big banner rises with a small shogun helmet set at its lower right. Shogun starts with sho."),
        ),
        "ちゃ" to listOf(
            KanaMnemonic(keyword = "CHAI", story = "A big teapot pours with a small chai cup catching at its lower right. Chai sounds like cha."),
            KanaMnemonic(keyword = "CHARM", story = "A big bracelet loops with a small charm dangling at its lower corner. Charm starts with cha."),
        ),
        "ちゅ" to listOf(
            KanaMnemonic(keyword = "CHOO", story = "A big engine puffs with a small caboose at its lower right, going choo. Choo sounds like chu."),
            KanaMnemonic(keyword = "CHUTE", story = "A big slide bends into a small parachute chute at its lower corner. Chute starts with chu."),
        ),
        "ちょ" to listOf(
            KanaMnemonic(keyword = "CHOPSTICKS", story = "A big bowl with two small chopsticks resting at its lower corner. Chopstick starts with cho."),
            KanaMnemonic(keyword = "CHOCOLATE", story = "A big bar snaps with a small chocolate square breaking off at its lower right. Chocolate starts with cho."),
        ),
        "にゃ" to listOf(
            KanaMnemonic(keyword = "KITTEN", story = "A big cat arches its back with a small kitten curled at its lower right, both mewing. A cat mews nya."),
            KanaMnemonic(keyword = "NYALA", story = "A big antelope with a small nyala calf at its lower corner. Nyala starts with nya."),
        ),
        "にゅ" to listOf(
            KanaMnemonic(keyword = "NEWT", story = "A big log with a small newt crawling off at its lower right. Newt sounds like nyu."),
        ),
        "にょ" to listOf(
            KanaMnemonic(keyword = "CANYON", story = "A big cliff with a small canyon notch cut at its lower corner. Canyon holds the sound nyo."),
            KanaMnemonic(keyword = "GNOCCHI", story = "A big pot with a small gnocchi dumpling bobbing at its lower right. Gnocchi sounds like nyo."),
        ),
        "ひゃ" to listOf(
            KanaMnemonic(keyword = "HYENA", story = "A big grin with a small hyena cackling at its lower corner. Hyena starts with hya."),
            KanaMnemonic(keyword = "HYACINTH", story = "A big stalk with a small hyacinth bloom opening at its lower right. Hyacinth starts with hya."),
        ),
        "ひゅ" to listOf(
            KanaMnemonic(keyword = "HUE", story = "A big palette with a small swatch of hue dabbed at its lower right. Hue sounds like hyu."),
        ),
        "ひょ" to listOf(
            KanaMnemonic(keyword = "HYPHEN", story = "A big word breaks with a small hyphen hooked at its lower right. Hyphen starts with the sound hyo."),
            KanaMnemonic(keyword = "HYOTAN", story = "A big vine with a small gourd hanging at its lower corner, a hyotan. Hyotan starts with hyo."),
        ),
        "みゃ" to listOf(
            KanaMnemonic(keyword = "MYANMAR", story = "A big map with a small flag of Myanmar pinned at its lower right. Myanmar starts with mya."),
        ),
        "みゅ" to listOf(
            KanaMnemonic(keyword = "MUSEUM", story = "A big hall with a small museum placard at its lower corner. Museum sounds like myu."),
        ),
        "みょ" to listOf(
            KanaMnemonic(keyword = "MYOPIA", story = "A big eye chart with a small blurred letter at its lower right, from myopia. Myopia starts with myo."),
        ),
        "りゃ" to listOf(
            KanaMnemonic(keyword = "RYEGRASS", story = "A big field with a small tuft of ryegrass at its lower right. Ryegrass sounds like rya."),
        ),
        "りゅ" to listOf(
            KanaMnemonic(keyword = "REUNION", story = "A big banner with a small crowd gathering at its lower corner, a reunion. Reunion sounds like ryu."),
            KanaMnemonic(keyword = "DRAGON", story = "A big cloud with a small dragon coiling at its lower right, a ryu. Ryu names the dragon."),
        ),
        "りょ" to listOf(
            KanaMnemonic(keyword = "RYOKAN", story = "A big inn with a small hot-spring bath at its lower right, a ryokan. Ryokan starts with ryo."),
        ),
        "ぎゃ" to listOf(
            KanaMnemonic(keyword = "GYARU", story = "A big mirror with a small gyaru fashion doll posing at its lower corner. Gyaru starts with gya."),
        ),
        "ぎゅ" to listOf(
            KanaMnemonic(keyword = "GYUDON", story = "A big rice bowl with a small strip of beef curling at its lower right, a gyudon. Gyudon starts with gyu."),
        ),
        "ぎょ" to listOf(
            KanaMnemonic(keyword = "GYOZA", story = "A big dumpling wrapper folds with a small pleat at its lower right, a gyoza. Gyoza starts with gyo."),
        ),
        "じゃ" to listOf(
            KanaMnemonic(keyword = "JAZZ", story = "A big trumpet flares with a small jazz note curling at its lower corner. Jazz starts with ja."),
            KanaMnemonic(keyword = "JAGUAR", story = "A big paw swipes with a small jaguar cub crouched at its lower right. Jaguar starts with ja."),
        ),
        "じゅ" to listOf(
            KanaMnemonic(keyword = "JUICE", story = "A big glass tips with a small juice drop falling at its lower right. Juice starts with ju."),
            KanaMnemonic(keyword = "JUDO", story = "A big belt knots with a small judo thrower flipping at its lower corner. Judo starts with ju."),
        ),
        "じょ" to listOf(
            KanaMnemonic(keyword = "JOGGER", story = "A big track curves with a small jogger rounding its lower corner. Jogger starts with jo."),
            KanaMnemonic(keyword = "JOKER", story = "A big deck fans out with a small joker card poking from its lower right. Joker starts with jo."),
        ),
        "びゃ" to listOf(
            KanaMnemonic(keyword = "BAYOU", story = "A big swamp with a small bayou channel winding off at its lower right. Bayou sounds like bya."),
        ),
        "びゅ" to listOf(
            KanaMnemonic(keyword = "BUREAU", story = "A big office with a small bureau desk set in its lower corner. Bureau sounds like byu."),
        ),
        "びょ" to listOf(
            KanaMnemonic(keyword = "BYOIN", story = "A big cross-sign with a small clinic at its lower right, a byoin hospital. Byoin starts with byo."),
        ),
        "ぴゃ" to listOf(
            KanaMnemonic(keyword = "PYRAMID", story = "A big desert with a small pyramid rising at its lower right. Pyramid sounds like pya."),
        ),
        "ぴゅ" to listOf(
            KanaMnemonic(keyword = "PEWTER", story = "A big shelf with a small pewter mug set at its lower corner. Pewter sounds like pyu."),
        ),
        "ぴょ" to listOf(
            KanaMnemonic(keyword = "PYLON", story = "A big wire with a small pylon tower holding it at the lower right. Pylon sounds like pyo."),
            KanaMnemonic(keyword = "FROGHOP", story = "A big frog springs with a small hop-puff at its lower right, pyon pyon. The hop is pyo."),
        ),
        "ぁ" to listOf(
            KanaMnemonic(keyword = "ATOM", story = "A tiny atom spinning in the corner, too small to see. Atom starts with a."),
        ),
        "ぃ" to listOf(
            KanaMnemonic(keyword = "IOTA", story = "A single iota, the tiniest speck of ink in the margin. Iota starts with i."),
        ),
        "ぅ" to listOf(
            KanaMnemonic(keyword = "UPDRAFT", story = "A small updraft lifting one leaf in the corner. Updraft starts with u."),
        ),
        "ぇ" to listOf(
            KanaMnemonic(keyword = "EDDY", story = "A little eddy swirling at the river's edge. Eddy starts with e."),
        ),
        "ぉ" to listOf(
            KanaMnemonic(keyword = "OUNCE", story = "Just an ounce, a tiny weight tipping the corner of a scale. Ounce sounds like o."),
        ),
        "ゐ" to listOf(
            KanaMnemonic(keyword = "WISP", story = "A faint wisp of an old letter, once read wi, curling away. Wisp starts with wi."),
        ),
        "ゑ" to listOf(
            KanaMnemonic(keyword = "WEB", story = "A dusty old web strung in an attic corner, once read we. Web starts with we."),
        ),
        "ゔ" to listOf(
            KanaMnemonic(keyword = "VODKA", story = "A small shot of vodka poured in the corner. Vodka sounds like vu."),
        ),
    )

    val KANA_COMPOSITION: Map<String, String> = mapOf(
        "が" to "The ka shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ga.",
        "ぎ" to "The ki shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to gi.",
        "ぐ" to "The ku shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to gu.",
        "げ" to "The ke shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ge.",
        "ご" to "The ko shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to go.",
        "ざ" to "The sa shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to za.",
        "じ" to "The shi shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ji.",
        "ず" to "The su shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to zu.",
        "ぜ" to "The se shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ze.",
        "ぞ" to "The so shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to zo.",
        "だ" to "The ta shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to da.",
        "ぢ" to "The chi shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to di.",
        "づ" to "The tsu shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to du.",
        "で" to "The te shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to de.",
        "ど" to "The to shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to do.",
        "ば" to "The ha shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ba.",
        "び" to "The hi shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to bi.",
        "ぶ" to "The fu shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to bu.",
        "べ" to "The he shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to be.",
        "ぼ" to "The ho shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to bo.",
        "ぱ" to "The ha shape with a small circle (the handakuten) at the upper right, turning it into pa.",
        "ぴ" to "The hi shape with a small circle (the handakuten) at the upper right, turning it into pi.",
        "ぷ" to "The fu shape with a small circle (the handakuten) at the upper right, turning it into pu.",
        "ぺ" to "The he shape with a small circle (the handakuten) at the upper right, turning it into pe.",
        "ぽ" to "The ho shape with a small circle (the handakuten) at the upper right, turning it into po.",
        "きゃ" to "The full ki shape with a small ya beside it at the lower right, blending to kya.",
        "きゅ" to "The full ki shape with a small yu beside it at the lower right, blending to kyu.",
        "きょ" to "The full ki shape with a small yo beside it at the lower right, blending to kyo.",
        "しゃ" to "The full shi shape with a small ya beside it at the lower right, blending to sha.",
        "しゅ" to "The full shi shape with a small yu beside it at the lower right, blending to shu.",
        "しょ" to "The full shi shape with a small yo beside it at the lower right, blending to sho.",
        "ちゃ" to "The full chi shape with a small ya beside it at the lower right, blending to cha.",
        "ちゅ" to "The full chi shape with a small yu beside it at the lower right, blending to chu.",
        "ちょ" to "The full chi shape with a small yo beside it at the lower right, blending to cho.",
        "にゃ" to "The full ni shape with a small ya beside it at the lower right, blending to nya.",
        "にゅ" to "The full ni shape with a small yu beside it at the lower right, blending to nyu.",
        "にょ" to "The full ni shape with a small yo beside it at the lower right, blending to nyo.",
        "ひゃ" to "The full hi shape with a small ya beside it at the lower right, blending to hya.",
        "ひゅ" to "The full hi shape with a small yu beside it at the lower right, blending to hyu.",
        "ひょ" to "The full hi shape with a small yo beside it at the lower right, blending to hyo.",
        "みゃ" to "The full mi shape with a small ya beside it at the lower right, blending to mya.",
        "みゅ" to "The full mi shape with a small yu beside it at the lower right, blending to myu.",
        "みょ" to "The full mi shape with a small yo beside it at the lower right, blending to myo.",
        "りゃ" to "The full ri shape with a small ya beside it at the lower right, blending to rya.",
        "りゅ" to "The full ri shape with a small yu beside it at the lower right, blending to ryu.",
        "りょ" to "The full ri shape with a small yo beside it at the lower right, blending to ryo.",
        "ぎゃ" to "The full gi shape with a small ya beside it at the lower right, blending to gya.",
        "ぎゅ" to "The full gi shape with a small yu beside it at the lower right, blending to gyu.",
        "ぎょ" to "The full gi shape with a small yo beside it at the lower right, blending to gyo.",
        "じゃ" to "The full ji shape with a small ya beside it at the lower right, blending to ja.",
        "じゅ" to "The full ji shape with a small yu beside it at the lower right, blending to ju.",
        "じょ" to "The full ji shape with a small yo beside it at the lower right, blending to jo.",
        "びゃ" to "The full bi shape with a small ya beside it at the lower right, blending to bya.",
        "びゅ" to "The full bi shape with a small yu beside it at the lower right, blending to byu.",
        "びょ" to "The full bi shape with a small yo beside it at the lower right, blending to byo.",
        "ぴゃ" to "The full pi shape with a small ya beside it at the lower right, blending to pya.",
        "ぴゅ" to "The full pi shape with a small yu beside it at the lower right, blending to pyu.",
        "ぴょ" to "The full pi shape with a small yo beside it at the lower right, blending to pyo.",
        "ぁ" to "A small version of the a kana, written little so it rides beside a bigger sound.",
        "ぃ" to "A small version of the i kana, written little so it rides beside a bigger sound.",
        "ぅ" to "A small version of the u kana, written little so it rides beside a bigger sound.",
        "ぇ" to "A small version of the e kana, written little so it rides beside a bigger sound.",
        "ぉ" to "A small version of the o kana, written little so it rides beside a bigger sound.",
        "ゐ" to "An old, retired kana once read wi, now seen only in a few old names and signs.",
        "ゑ" to "An old, retired kana once read we, now seen only in a few old names and signs.",
        "ゔ" to "The u shape with two short diagonal strokes (the dakuten) at the upper right, borrowed to make the foreign sound vu.",
        "ガ" to "The ka shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ga.",
        "ギ" to "The ki shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to gi.",
        "グ" to "The ku shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to gu.",
        "ゲ" to "The ke shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ge.",
        "ゴ" to "The ko shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to go.",
        "ザ" to "The sa shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to za.",
        "ジ" to "The shi shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ji.",
        "ズ" to "The su shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to zu.",
        "ゼ" to "The se shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ze.",
        "ゾ" to "The so shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to zo.",
        "ダ" to "The ta shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to da.",
        "ヂ" to "The chi shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to di.",
        "ヅ" to "The tsu shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to du.",
        "デ" to "The te shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to de.",
        "ド" to "The to shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to do.",
        "バ" to "The ha shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to ba.",
        "ビ" to "The hi shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to bi.",
        "ブ" to "The fu shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to bu.",
        "ベ" to "The he shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to be.",
        "ボ" to "The ho shape with two short diagonal strokes (the dakuten) at the upper right, voicing it to bo.",
        "パ" to "The ha shape with a small circle (the handakuten) at the upper right, turning it into pa.",
        "ピ" to "The hi shape with a small circle (the handakuten) at the upper right, turning it into pi.",
        "プ" to "The fu shape with a small circle (the handakuten) at the upper right, turning it into pu.",
        "ペ" to "The he shape with a small circle (the handakuten) at the upper right, turning it into pe.",
        "ポ" to "The ho shape with a small circle (the handakuten) at the upper right, turning it into po.",
        "キャ" to "The full ki shape with a small ya beside it at the lower right, blending to kya.",
        "キュ" to "The full ki shape with a small yu beside it at the lower right, blending to kyu.",
        "キョ" to "The full ki shape with a small yo beside it at the lower right, blending to kyo.",
        "シャ" to "The full shi shape with a small ya beside it at the lower right, blending to sha.",
        "シュ" to "The full shi shape with a small yu beside it at the lower right, blending to shu.",
        "ショ" to "The full shi shape with a small yo beside it at the lower right, blending to sho.",
        "チャ" to "The full chi shape with a small ya beside it at the lower right, blending to cha.",
        "チュ" to "The full chi shape with a small yu beside it at the lower right, blending to chu.",
        "チョ" to "The full chi shape with a small yo beside it at the lower right, blending to cho.",
        "ニャ" to "The full ni shape with a small ya beside it at the lower right, blending to nya.",
        "ニュ" to "The full ni shape with a small yu beside it at the lower right, blending to nyu.",
        "ニョ" to "The full ni shape with a small yo beside it at the lower right, blending to nyo.",
        "ヒャ" to "The full hi shape with a small ya beside it at the lower right, blending to hya.",
        "ヒュ" to "The full hi shape with a small yu beside it at the lower right, blending to hyu.",
        "ヒョ" to "The full hi shape with a small yo beside it at the lower right, blending to hyo.",
        "ミャ" to "The full mi shape with a small ya beside it at the lower right, blending to mya.",
        "ミュ" to "The full mi shape with a small yu beside it at the lower right, blending to myu.",
        "ミョ" to "The full mi shape with a small yo beside it at the lower right, blending to myo.",
        "リャ" to "The full ri shape with a small ya beside it at the lower right, blending to rya.",
        "リュ" to "The full ri shape with a small yu beside it at the lower right, blending to ryu.",
        "リョ" to "The full ri shape with a small yo beside it at the lower right, blending to ryo.",
        "ギャ" to "The full gi shape with a small ya beside it at the lower right, blending to gya.",
        "ギュ" to "The full gi shape with a small yu beside it at the lower right, blending to gyu.",
        "ギョ" to "The full gi shape with a small yo beside it at the lower right, blending to gyo.",
        "ジャ" to "The full ji shape with a small ya beside it at the lower right, blending to ja.",
        "ジュ" to "The full ji shape with a small yu beside it at the lower right, blending to ju.",
        "ジョ" to "The full ji shape with a small yo beside it at the lower right, blending to jo.",
        "ビャ" to "The full bi shape with a small ya beside it at the lower right, blending to bya.",
        "ビュ" to "The full bi shape with a small yu beside it at the lower right, blending to byu.",
        "ビョ" to "The full bi shape with a small yo beside it at the lower right, blending to byo.",
        "ピャ" to "The full pi shape with a small ya beside it at the lower right, blending to pya.",
        "ピュ" to "The full pi shape with a small yu beside it at the lower right, blending to pyu.",
        "ピョ" to "The full pi shape with a small yo beside it at the lower right, blending to pyo.",
        "ァ" to "A small version of the a kana, written little so it rides beside a bigger sound.",
        "ィ" to "A small version of the i kana, written little so it rides beside a bigger sound.",
        "ゥ" to "A small version of the u kana, written little so it rides beside a bigger sound.",
        "ェ" to "A small version of the e kana, written little so it rides beside a bigger sound.",
        "ォ" to "A small version of the o kana, written little so it rides beside a bigger sound.",
        "ヰ" to "An old, retired kana once read wi, now seen only in a few old names and signs.",
        "ヱ" to "An old, retired kana once read we, now seen only in a few old names and signs.",
        "ヴ" to "The u shape with two short diagonal strokes (the dakuten) at the upper right, borrowed to make the foreign sound vu.",
        "ウィ" to "The full u shape with a small i beside it at the lower right, borrowed to make wi.",
        "ウェ" to "The full u shape with a small e beside it at the lower right, borrowed to make we.",
        "ウォ" to "The full u shape with a small o beside it at the lower right, borrowed to make wo.",
        "ヴァ" to "The full vu shape with a small a beside it at the lower right, borrowed to make va.",
        "ヴィ" to "The full vu shape with a small i beside it at the lower right, borrowed to make vi.",
        "ヴェ" to "The full vu shape with a small e beside it at the lower right, borrowed to make ve.",
        "ヴォ" to "The full vu shape with a small o beside it at the lower right, borrowed to make vo.",
        "シェ" to "The full shi shape with a small e beside it at the lower right, borrowed to make she.",
        "ジェ" to "The full ji shape with a small e beside it at the lower right, borrowed to make je.",
        "チェ" to "The full chi shape with a small e beside it at the lower right, borrowed to make che.",
        "ティ" to "The full te shape with a small i beside it at the lower right, borrowed to make ti.",
        "ディ" to "The full de shape with a small i beside it at the lower right, borrowed to make di.",
        "トゥ" to "The full to shape with a small u beside it at the lower right, borrowed to make tu.",
        "ドゥ" to "The full do shape with a small u beside it at the lower right, borrowed to make du.",
        "ファ" to "The full fu shape with a small a beside it at the lower right, borrowed to make fa.",
        "フィ" to "The full fu shape with a small i beside it at the lower right, borrowed to make fi.",
        "フェ" to "The full fu shape with a small e beside it at the lower right, borrowed to make fe.",
        "フォ" to "The full fu shape with a small o beside it at the lower right, borrowed to make fo.",
        "ツァ" to "The full tsu shape with a small a beside it at the lower right, borrowed to make tsa.",
        "ツィ" to "The full tsu shape with a small i beside it at the lower right, borrowed to make tsi.",
        "ツェ" to "The full tsu shape with a small e beside it at the lower right, borrowed to make tse.",
        "ツォ" to "The full tsu shape with a small o beside it at the lower right, borrowed to make tso.",
    )

    /**
     * Every shipped mnemonic for [glyph]'s exact glyph, default first.
     * Consulted across the base hiragana, the non-base hiragana extras, and
     * katakana; empty only for the suppressed silent marks. Exact-glyph, no NFD
     * inheritance: a keyword anchors one exact sound.
     */
    fun candidatesFor(glyph: String): List<KanaMnemonic> =
        BASE_HIRAGANA[glyph] ?: HIRAGANA_EXTRA[glyph] ?: KATAKANA[glyph] ?: emptyList()

    /**
     * The always-shown technical definition of how a special (derived) glyph is
     * built ("the ka shape with two short diagonal strokes (the dakuten),
     * voicing it to ga"), or null for a base kana. Display-only, never a
     * pickable mnemonic (owner decision 2026-07-31).
     */
    fun compositionFor(glyph: String): String? = KANA_COMPOSITION[glyph]

    /** Every shipped mnemonic across all three maps, for test coverage. */
    internal fun allEntriesForTest(): List<Pair<String, KanaMnemonic>> =
        (BASE_HIRAGANA + HIRAGANA_EXTRA + KATAKANA)
            .flatMap { (glyph, list) -> list.map { glyph to it } }

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
