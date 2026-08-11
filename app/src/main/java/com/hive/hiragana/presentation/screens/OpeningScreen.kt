package com.hive.hiragana.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hive.hiragana.domain.model.AppMode
import com.hive.hiragana.presentation.components.ConceptInfo
import com.hive.hiragana.presentation.components.ConceptInfoIcon
import com.hive.hiragana.presentation.components.KanaOverviewButton
import com.hive.hiragana.presentation.components.PrintFontFamily

/**
 * The full gojūon (hiragana then katakana, no spaces) used as the opening
 * screen's decorative background wall. Purely visual — see [KanaWall].
 */
private const val KANA_WALL_TEXT =
    "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん" +
        "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン"

/**
 * The app's home / launcher: pick a track — Reading (recognize the printed
 * forms) or Writing (handwriting practice). Shown whenever
 * [com.hive.hiragana.presentation.viewmodels.PracticeUiState.appMode] is null.
 * Selecting a card calls [onPick]; back from here exits the app.
 *
 * Redesigned 2026-08-08 from a Claude Design handoff
 * (`Japanese learning app homescreen.zip`): a decorative kana-wall
 * background, a bilingual title lockup, and specimen-panel mode cards. Purely
 * cosmetic — the contract, state, and footer logic are unchanged.
 */
@Composable
fun OpeningScreen(
    onPick: (AppMode) -> Unit,
    beginnerMode: Boolean = false,
    onSetBeginnerMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            KanaWall()
            Scrim()

            Column(modifier = Modifier.fillMaxSize()) {
                // The title sits right above the cards (a small fixed gap, like
                // the internal card spacing) and that whole group sits at the
                // BOTTOM of this region, right above the footer. A single
                // flexible weight(1f) spacer ABOVE the title absorbs whatever
                // room is left, so the proportions stay consistent across any
                // screen height — more of the kana wall shows through on a tall
                // phone, less on a short one — rather than a fixed dp gap that
                // would only look right on one device (user-requested
                // 2026-08-08).
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 25.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    TitleBlock()

                    Spacer(modifier = Modifier.height(44.dp))

                    ModeCard(
                        title = "Reading",
                        subtitle = "Recognize the printed kana you meet on signs, apps, and online.",
                        onClick = { onPick(AppMode.READING) },
                    ) {
                        Text(
                            text = "あ",
                            fontFamily = PrintFontFamily,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ModeCard(
                        title = "Writing",
                        subtitle = "Trace the stroke-order form and get instant feedback on your shape.",
                        onClick = { onPick(AppMode.WRITING) },
                    ) {
                        StrokeGuideCross()
                        Text(
                            text = "ア",
                            fontFamily = PrintFontFamily,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Beginner mode lives in a small, horizontally-centered footer
                // ALWAYS pinned to the bottom of the screen (not in the flow
                // with the cards, and not part of the scrollable region above —
                // it must stay put regardless of how tall the card content is).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(top = 4.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The "New to kana?" overview is itself a beginner tooltip, so it
                    // only shows when Beginner mode is on — but its space is ALWAYS
                    // reserved (rendered at alpha 0, non-interactive when off), so
                    // toggling reveals it in place without growing the footer and
                    // shifting the cards above.
                    KanaOverviewButton(
                        enabled = beginnerMode,
                        modifier = Modifier
                            .alpha(if (beginnerMode) 1f else 0f)
                            .padding(bottom = 2.dp),
                    )
                    BeginnerModeRow(
                        beginnerMode = beginnerMode,
                        onSetBeginnerMode = onSetBeginnerMode,
                    )
                }
            }
        }
    }
}

/**
 * Decorative full-gojūon background text, purely visual — hidden from
 * TalkBack (100+ characters of noise) and clipped to the screen since it
 * naturally overflows below the fold.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KanaWall() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Text(
            text = KANA_WALL_TEXT,
            fontFamily = PrintFontFamily,
            fontSize = 47.sp,
            lineHeight = 63.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.18.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f),
            modifier = Modifier
                .padding(start = 13.dp, top = 45.dp)
                .semantics { invisibleToUser() },
        )
    }
}

/** Vertical gradient over the [KanaWall] so the title + cards stay readable. */
@Composable
private fun Scrim() {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to background.copy(alpha = 0.55f),
                        0.34f to background.copy(alpha = 0.94f),
                        1f to background.copy(alpha = 0.98f),
                    ),
                ),
            ),
    )
}

/** The bilingual title lockup: Japanese name, Latin wordmark, tagline. */
@Composable
private fun TitleBlock() {
    Text(
        text = "ひらカタ・クエスト",
        fontFamily = PrintFontFamily,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.em,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(11.dp))
    Text(
        text = "HiraKataQuest",
        style = MaterialTheme.typography.headlineMedium,
        fontSize = 29.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "249 characters. Two ways in.",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        lineHeight = 21.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * The dashed stroke-practice cross-guide behind the Writing card's specimen
 * glyph — a hint at the tracing grid, drawn as real dashed lines (never a
 * bundled image).
 */
@Composable
private fun StrokeGuideCross() {
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.size(66.dp)) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 6.dp.toPx()), 0f)
        val strokeWidthPx = 2.5.dp.toPx()
        drawLine(
            color = guideColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
            pathEffect = dash,
        )
        drawLine(
            color = guideColor,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
            pathEffect = dash,
        )
    }
}

/** The plain-language explainer for what Beginner mode does and who it helps. */
private val BeginnerModeInfo = ConceptInfo(
    title = "Beginner mode",
    body = "Beginner mode adds small ⓘ help icons and a \"New to kana?\" guide " +
        "around the app, explaining terms like hiragana, katakana, and voiced or " +
        "combined kana in plain language. Turn it on if you are new to Japanese " +
        "or want those explanations; leave it off for a cleaner screen once the " +
        "terms feel familiar.",
)

/**
 * The compact "Beginner mode" control (leading ⓘ explainer, small label,
 * trailing [Switch]) shown centered in the opening screen's bottom footer — it
 * is the control for the tooltips, not a tooltip itself. Default OFF (kept
 * clean); ON reveals the kana-concept help. The switch is scaled down and
 * tracks the system font scale so it stays proportional to the small label
 * (never larger than a default switch).
 */
@Composable
private fun BeginnerModeRow(
    beginnerMode: Boolean,
    onSetBeginnerMode: (Boolean) -> Unit,
) {
    val switchScale = (0.72f * LocalDensity.current.fontScale).coerceIn(0.5f, 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ConceptInfoIcon(concept = BeginnerModeInfo)
        Text(
            text = "Beginner mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
        Switch(
            checked = beginnerMode,
            onCheckedChange = onSetBeginnerMode,
            modifier = Modifier.scale(switchScale),
        )
    }
}

/**
 * A mode-picker card: a fixed-width specimen panel (leading, full card
 * height, clipped by the card's own rounded shape) beside a title + body text
 * block. [specimen] renders the printed glyph (and, for Writing, its dashed
 * stroke-guide backdrop) — always real text/vector drawing, never a bundled
 * image.
 */
@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    specimen: @Composable BoxScope.() -> Unit,
) {
    // "One step" more saturated than the card container, so the specimen
    // panel reads as a distinct inset — works in both themes since it derives
    // from the resolved surfaceVariant/onSurfaceVariant pair rather than a
    // hardcoded hex.
    val specimenColor = lerp(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
        0.08f,
    )
    Card(
        onClick = onClick,
        // Cap-then-fill: full-width on phones, 420dp on very wide screens
        // (centered by the parent column). Was fillMaxWidth().width(420.dp) —
        // a contradictory pair where the fixed width silently lost out.
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(specimenColor),
                contentAlignment = Alignment.Center,
                content = specimen,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 15.dp, vertical = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}
