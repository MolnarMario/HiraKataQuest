<h1 align="center">HiraKataQuest</h1>

<p align="center">
  <b>Learn to read and write Japanese kana by hand.</b><br>
  An Android app that has you trace every hiragana and katakana in its
  handwritten form, shows the stroke order, scores what you actually drew,
  and drills reading the printed kana you meet on signs and screens.
</p>

<p align="center">
  <a href="https://github.com/MolnarMario/HiraKataQuest/releases/latest/download/HiraKataQuest.apk"><b>Download the APK</b></a> ·
  <a href="https://thebarbellengineer.com/projects/hirakataquest/">Project page</a> ·
  <a href="#install">Install</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="LICENSE">MIT license</a>
</p>

<p align="center">
  <img src="https://thebarbellengineer.com/assets/img/hirakata-start-480.webp" alt="The opening screen: pick Reading or Writing" width="200">
  <img src="https://thebarbellengineer.com/assets/img/hirakata-write-char-hira-480.webp" alt="Writing a hiragana over its handwritten guide, with a score" width="200">
  <img src="https://thebarbellengineer.com/assets/img/hirakata-library-480.webp" alt="The Library, every kana tinted by its average score" width="200">
</p>

## Why

Most kana apps stop at recognition: you see a character, you tap the right
answer. That teaches you to read, but not to write, and the printed font they
show is not how anyone writes by hand. HiraKataQuest has you write each
character with your finger or a stylus over its handwritten (kaisho) form,
tells you honestly how close you got, and keeps a separate track for reading
printed kana. It covers all of hiragana and katakana, and nothing is locked
behind progress.

## What it does

### Write it by hand

- **Trace the handwritten form, not the font.** A light-grey guide of the
  school handwriting form sits on a genkouyoushi practice grid. Your ink
  widens with pen pressure and thins on fast flicks, like a brush.
- **Stroke order for every character.** An animated demo draws each stroke
  with its real rhythm and pen lifts, with play, pause, replay and speed
  controls. A word gets one demo per kana.
- **A score that measures what you drew.** Stroke geometry plus on-device
  ML Kit handwriting recognition, with no consolation floor: a clean trace
  scores 95 or more, a random line 12 or less. Choose what it judges: Form
  (shape, order, direction, proportion), Fit (filling the box), both, or
  turn scoring off.
- **Write from memory.** **Quiz me** shows only the romaji and hides the
  guide. It works on whole words too, scoring each kana separately. You can
  also hide the guide or the grid in normal practice.
- **Worksheet sizes.** XL is one big scored copy; L, M and S fill the sheet
  with smaller copies for repetition. **Undo** takes back only your last
  stroke.

### Read it in print

- **A reading quiz, either way round.** See a printed kana or word and give
  its reading, or see the reading and pick the kana. Answer by multiple
  choice or by typing, and optionally get tested on a word's meaning.
- **Flashcards** that flip from the printed form to the reading, the
  meaning and audio, with an optional field to type the reading.
- **"Looks different in print."** さ, き and り join their strokes in print
  but not by hand. Wherever one is on screen, a chip shows both forms side by
  side.

### Remember it

- **Memory hints for the 46 basic hiragana.** A keyword and a short story tie
  each shape to its sound (あ is APPLE). Every kana has alternates, and the
  one you pick is used everywhere.
- **Hear it.** A speaker button reads any kana or word aloud with the phone's
  Japanese voice.
- **Kanji reference** (off by default). Words with a kanji spelling get a
  button showing the kanji, the reading and the meaning, and the other kanji
  that share that reading. It is never quizzed.

### Know what to practise next

- **A Library tinted by your scores.** Every character, word and set goes
  from red to green by your average, in a list or as a gojūon chart.
- **Practice sets.** Built-in sets for every gojūon row, kana category and a
  few word themes, plus your own. **From my strong kana** builds a word set
  out of kana you already score 85 or better on.
- **All of hiragana and katakana, without the overwhelm.** Beginners start
  with the 46 basics. Voiced (が), combined (きゃ) and extended (ファ, っ)
  kana are separate opt-ins. About 840 words ship with the app, including the
  common JLPT N5 vocabulary.

### Make it comfortable

- **A designed dark mode.** The worksheet itself darkens, and your brush gets
  its own ink colour for dark mode.
- **Any brush colour** from a full colour picker.
- **No account.** Your progress stays on your phone.

## Requirements

- An Android phone or tablet running **Android 8.0 or newer**. A stylus is
  welcome but not needed.
- An internet connection once, so ML Kit can download its Japanese
  handwriting model. Until it has, scoring uses stroke geometry only.
- Optional: a Japanese text-to-speech voice for audio (the app offers to
  install one), and a Japanese keyboard for typing kana in the
  Romaji → kana quiz.

## Install

1. On your Android device, download
   **[HiraKataQuest.apk](https://github.com/MolnarMario/HiraKataQuest/releases/latest/download/HiraKataQuest.apk)**
   (about 39 MB, always the latest release).
2. Open the downloaded file. If Android asks, allow your browser or file
   manager to install unknown apps.
3. If Play Protect warns that the app is unknown, tap **More details**, then
   **Install anyway**. It is not on the Play Store, which is the only reason
   for the warning.

**Updating:** download the APK again and install it over the old one. Your
progress is kept. Every release and its notes are on the
[releases page](https://github.com/MolnarMario/HiraKataQuest/releases).

## First ten minutes

1. **Pick a track.** The opening screen offers **Writing** (handwriting) and
   **Reading** (printed kana). You can switch any time.
2. **Write your first character.** Writing opens the Library. Tap あ, watch
   the stroke-order demo, trace it, then tap **Submit** to see your score.
   **Again** rewrites it, **Next** moves on.
3. **Take the guide away.** Hide the ghost with the あ button in the controls
   strip, or tap **Quiz me** in the Library to write from the romaji alone.
4. **Try reading.** Switch to Reading and run a quiz in either direction, or
   flip through flashcards.
5. **Set your scope.** The Study scope sheet in the Library switches between
   hiragana and katakana, adds voiced, combined or extended kana, and holds
   memory hints, the kanji reference, scoring mode and dark mode.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Android blocks the install | Allow the app you downloaded with (browser or Files) to install unknown apps, in Settings > Apps > Special app access. |
| Play Protect says the app is unknown | Tap **More details**, then **Install anyway**. |
| The speaker button opens a "voice data" screen | The phone has no Japanese voice yet. Download it there for free, then return to the app. |
| The speaker button is greyed out | The phone has no text-to-speech engine that speaks Japanese. |
| Scores ignore what the character looks like to a reader | ML Kit's handwriting model hasn't downloaded yet. Connect to the internet once. |
| You can't type kana in the Romaji → kana quiz | Add a Japanese keyboard on your phone. The quiz setup card links to the setting. |

## Development

Kotlin, Jetpack Compose (Material 3), Compose Canvas, MVVM, Room (KSP) and
manual dependency injection (`AppContainer`). Requires Android SDK 34; runs on
API 26 and up.

No Gradle wrapper JAR is committed. Either:

1. **Android Studio** (Koala or newer): *File → Open* the project root, let it
   sync and provision Gradle 8.7, then Run. Or:
2. **Command line**, with a local Gradle 8.5+ and JDK 17: run
   `gradle wrapper --gradle-version 8.7` once, then:

```sh
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
```

Release builds are signed from a gitignored `keystore.properties` at the repo
root; without it, the release build is unsigned. Machine-specific toolchain
paths and agent instructions are in [CLAUDE.md](CLAUDE.md).

| Doc | What it covers |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layer map, state ownership (`PracticeViewModel` vs `CanvasState`), the manual-DI wiring. |
| [docs/DRAWING.md](docs/DRAWING.md) | Input capture (`StrokeCapture`), rendering pipeline, the pressure-to-width model. |
| [docs/DATA.md](docs/DATA.md) | The character and vocabulary JSON formats and the Room persistence layer. |
| [docs/HISTORY.md](docs/HISTORY.md) | How the project got here: phases, what was built when, known warts. |
| [docs/PHASE3-DESIGN.md](docs/PHASE3-DESIGN.md) | The spaced-repetition study mode, removed on 2026-07-05. Kept for history. |

```
app/src/main/java/com/hive/hiragana/
├── HiraganaApplication.kt   AppContainer (manual DI)
├── MainActivity.kt
├── presentation/            screens, components, PracticeViewModel, theme
├── domain/
│   ├── model/               characters, words, practice sets, default sets, scripts
│   ├── scoring/             geometry scorer, ML Kit legibility, calibration
│   └── repository/          repository interfaces
├── data/
│   ├── assets/              JSON loader for assets/hiragana and assets/katakana
│   ├── local/               Room entities, DAOs and migrations (schemas in app/schemas/)
│   └── repository/          implementations
└── drawing/
    ├── stroke/              StrokeCapture and the stroke model
    ├── canvas/              CanvasState, StrokeRenderer, grid and ghost renderers
    └── animation/           StrokeAnimationEngine (stroke-order playback)
```

### Character data

Each character is one JSON file in `assets/<script>/`, listed in `index.json`.
`strokes` holds one SVG path per stroke in a 109×109 viewBox and drives the
stroke-order demo; `strokeAnimations` holds timed pen positions generated from
it. Both are derived data: re-run `tools/import_kanjivg.py` and then
`tools/generate_stroke_animations.py` (both self-validate) rather than editing
them by hand. Each script's `vocabulary.json` lists words as
`{ "kana": "いえ", "meaning": "house" }`; the characters a word needs are
derived from its kana, never stored.

### Design decisions worth knowing

- **The guide you trace is drawn by the system font**, which guarantees a
  correct shape. The stroke-order demo comes from KanjiVG's stroke paths, so
  both match real calligraphic proportions.
- **Scores are measurements, never caps.** Recognition can only trim a score
  a little, and `GeometryScorerCalibrationTest` pins the bands a learner
  sees. Tune the scorer's constants only against those bands.
- **A spaced-repetition study mode was built and then removed** (2026-07-04
  to 07-05) in favour of free practice, quizzes and practice sets. Its tables
  stay in the database unused, because dropping them would take a
  destructive migration.

> **Attribution**: stroke-order geometry is from the
> [KanjiVG](http://kanjivg.tagaini.net) project, © Ulrich Apel, used and
> adapted under
> [CC BY-SA 3.0](http://creativecommons.org/licenses/by-sa/3.0/). The
> vendored SVGs in `tools/kanjivg/` retain their original license headers.
> The printed forms of さ/き/り (shown on every typed surface: flashcards,
> the reading quiz, the Library, the guide panel, and the comparison
> dialog's "In print" specimen) use an 8-glyph merged subset of
> [Kosugi](https://fonts.google.com/specimen/Kosugi) (© Motoya,
> [Apache License 2.0](tools/fonts/Kosugi-LICENSE.txt)) and
> [IBM Plex Sans JP](https://fonts.google.com/specimen/IBM+Plex+Sans+JP)
> (© IBM, [SIL OFL 1.1](tools/fonts/IBMPlexSansJP-OFL.txt)) — see
> `tools/fonts/README.md`.

## License

[MIT](LICENSE), except the third-party stroke data and fonts credited above,
which keep their own licenses. Built by Mario Molnar.
