package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One plain-language explainer for a kana concept a first-time learner meets in
 * the Study Scope sheet or on the opening screen: a short bold [title] and a
 * one-paragraph [body]. Copy is UI-only (hardcoded, matching the app's
 * convention — only app_name lives in strings.xml).
 */
data class ConceptInfo(val title: String, val body: String)

/**
 * The kana concepts the app never explained in-app. Beginners can open the
 * per-topic ones ([voiced] / [combined] / [extended]) from the ⓘ next to the
 * matching control in the Study-scope sheet. [overview] is the first-run
 * summary shown from the opening screen's "New to kana?" button; it is curated
 * (intro + hiragana + katakana + kanji + a pointer to the per-topic ⓘ) rather
 * than every entry. No em-dashes in copy (user style rule).
 */
object KanaConcepts {
    val intro = ConceptInfo(
        title = "What are kana?",
        body = "Kana are the two phonetic alphabets of Japanese, hiragana and " +
            "katakana. Each symbol stands for a whole syllable (a sound like ka " +
            "or mi) rather than a single letter, and between them they can spell " +
            "every Japanese sound. Writing each kana with the correct stroke " +
            "order and direction matters more than it first looks: it is how the " +
            "shapes are taught and recognized, it keeps your writing legible and " +
            "well proportioned, and it builds the muscle memory that lets you " +
            "write smoothly and read other people's handwriting later. That is " +
            "why this app has you trace each character in order instead of just " +
            "copying its outline.",
    )

    val hiragana = ConceptInfo(
        title = "Hiragana",
        body = "Hiragana is one of Japan's two phonetic alphabets. Its 46 base " +
            "characters each stand for a whole syllable (a sound like ka or mi), " +
            "not a single letter. It writes native Japanese words, grammar " +
            "particles, and verb endings, and is normally the first script " +
            "learners study. Together with katakana and kanji it makes up " +
            "written Japanese.",
    )

    val katakana = ConceptInfo(
        title = "Katakana",
        body = "Katakana is the second phonetic alphabet. It covers the same 46 " +
            "sounds as hiragana but with sharper, more angular shapes. It is " +
            "used mainly for foreign loanwords (for example コーヒー, koohii, " +
            "coffee), foreign names, sound effects, and emphasis.",
    )

    val voiced = ConceptInfo(
        title = "Voiced kana",
        body = "A base kana can be voiced by adding two small strokes (゛, " +
            "dakuten) or a small circle (゜, handakuten). The shape stays the " +
            "same but the sound changes: か ka becomes が ga, は ha becomes ば " +
            "ba or ぱ pa. Turn this on once the base kana feel familiar.",
    )

    val combined = ConceptInfo(
        title = "Combined kana",
        body = "Some syllables are written by pairing a base kana with a small " +
            "ゃ, ゅ, or ょ, for example き plus ゃ makes きゃ (kya). The small " +
            "kana blends in, so the pair is read as one syllable. These are the " +
            "contracted, or combined, sounds.",
    )

    val extended = ConceptInfo(
        title = "Extended kana",
        body = "Extra kana for special cases: the small pause っ that doubles " +
            "the next consonant, small vowels, and katakana combinations created " +
            "to spell foreign sounds (ファ fa, ティ ti, ウェ we). You mostly meet " +
            "these in loanwords and precise spellings.",
    )

    val kanji = ConceptInfo(
        title = "What about kanji?",
        body = "Kanji are the third piece of written Japanese: characters " +
            "borrowed from Chinese that carry meaning, not just sound. There are " +
            "thousands of them, each with several readings and many strokes. They " +
            "matter enormously for real reading, but they are a far larger and " +
            "longer study, so this app leaves them out on purpose and focuses on " +
            "giving you a solid foundation in the two kana alphabets first. Once " +
            "your kana are fluent you will be well prepared to take on kanji " +
            "elsewhere.",
    )

    val more = ConceptInfo(
        title = "Voiced, combined and extended kana",
        body = "Beyond the basic sounds there are voiced, combined, and extended " +
            "kana for extra and foreign sounds. You can switch these on in the " +
            "Study scope menu whenever you are ready, and each one has its own ⓘ " +
            "there that explains what it is.",
    )

    /**
     * The curated first-run overview (opening screen): intro, the two kana, a
     * note on kanji, and a pointer to the per-topic ⓘ for the advanced groups
     * (voiced / combined / extended are NOT expanded here — they live behind
     * their own ⓘ in the Study-scope sheet).
     */
    val overview = listOf(intro, hiragana, katakana, kanji, more)
}

/**
 * A small ⓘ icon button that opens a plain-language explainer dialog for one or
 * more [concepts]. Owns its own open/close state, so callers just drop it in
 * next to the control it explains. Mirrors the app's existing info affordance
 * (see [ScoringInfoDialog]): [Icons.Outlined.Info] in an [IconButton] toggling
 * a [rememberSaveable] flag, opening an [AlertDialog] of [InfoSection]s.
 */
@Composable
fun ConceptInfoIcon(
    concepts: List<ConceptInfo>,
    dialogTitle: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // The IconButton owns its own click, which is consumed here — so when this
    // sits inside a SegmentedButton label, tapping the ⓘ opens the dialog
    // WITHOUT also triggering the segment's selection.
    IconButton(
        onClick = { showDialog = true },
        modifier = modifier.size(24.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = contentDescription,
            // A muted, neutral tint: this is a "here's something helpful to
            // know" affordance, not a warning (the primary vermilion read as
            // alarmist).
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
    }

    if (showDialog) {
        ConceptInfoDialog(
            title = dialogTitle,
            concepts = concepts,
            onDismiss = { showDialog = false },
        )
    }
}

/** Convenience: an [ConceptInfoIcon] for a single concept, titled by it. */
@Composable
fun ConceptInfoIcon(
    concept: ConceptInfo,
    modifier: Modifier = Modifier,
) {
    ConceptInfoIcon(
        concepts = listOf(concept),
        dialogTitle = concept.title,
        contentDescription = "About ${concept.title}",
        modifier = modifier,
    )
}

/**
 * A low-emphasis "New to kana?" text button (leading ⓘ) that opens the full
 * [KanaConcepts.overview] as a first-run summary. Used on the opening screen,
 * where nothing else explains what kana are.
 */
@Composable
fun KanaOverviewButton(modifier: Modifier = Modifier) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // Neutral grey content (not the primary vermilion) so it reads as a calm
    // helper link rather than a warning.
    TextButton(
        onClick = { showDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "New to kana?",
            modifier = Modifier.padding(start = 6.dp),
        )
    }

    if (showDialog) {
        ConceptInfoDialog(
            title = "About kana",
            concepts = KanaConcepts.overview,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ConceptInfoDialog(
    title: String,
    concepts: List<ConceptInfo>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // A single concept is already named by the dialog title, so show
                // just its body; a multi-concept overview keeps section headings.
                if (concepts.size == 1) {
                    Text(
                        text = concepts.first().body,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    concepts.forEach { concept ->
                        InfoSection(title = concept.title, body = concept.body)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
