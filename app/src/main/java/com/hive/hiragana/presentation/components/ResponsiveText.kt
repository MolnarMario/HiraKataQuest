package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Font-scale resilience primitives.
 *
 * The app's sizing policy (see CLAUDE.md):
 * 1. CONTENT text (readings, meanings, prompts) scales with the system font
 *    setting, and its container grows or scrolls — never a fixed height
 *    around scaling text.
 * 2. CHROME text — icon-like labels living inside dp-sized chrome (the
 *    1×1/2×2/3×3 segment labels, the あ ghost toggle, gojūon chart cells,
 *    corner score badges) — is font-scale-INDEPENDENT via [toNonScalingSp],
 *    exactly like an icon. A fully-scaling representation of the same
 *    information (e.g. the Library list view) always exists.
 * 3. One-line rows that can't fit prefer a uniform shrink
 *    ([rememberUniformTextScale], [AutoShrinkText]) with a floor, then
 *    ellipsis — never wrapping chrome onto a second line.
 */

/**
 * This dp expressed as an sp value that renders at exactly this dp size
 * regardless of the user's font-scale setting. Use ONLY for chrome text
 * (policy rule 2) — content text must keep honoring the font scale.
 */
@Composable
@ReadOnlyComposable
fun Dp.toNonScalingSp(): TextUnit = with(LocalDensity.current) { toSp() }

/**
 * The system font scale, floored at 1 and capped at [cap]. Multiply a dp
 * container dimension by this when the container holds scaling text and must
 * grow with it (e.g. a fixed-width text field, an adaptive grid's min cell).
 */
@Composable
@ReadOnlyComposable
fun fontScaleFraction(cap: Float = 2f): Float =
    LocalDensity.current.fontScale.coerceIn(1f, cap)

// Line height for wrapped AutoShrinkText, as a multiple of the font size.
private const val WRAPPED_LINE_HEIGHT = 1.25f

/**
 * A [Text] of at most [maxLines] lines that shrinks its font size (binary
 * search, measured with a [rememberTextMeasurer] before layout — no visible
 * reflow) until the text fits the incoming max width, bottoming out at
 * [minFontSize] where it ellipsizes instead. Line height shrinks
 * proportionally.
 *
 * With [maxLines] > 1 the text wraps, and it must also fit the incoming max
 * HEIGHT — for a fixed-height slot (an answer pill) that may take two lines
 * before it has to shrink. Wrapped text also caps its line height at
 * [WRAPPED_LINE_HEIGHT] x the font size.
 *
 * The starting size is [style]'s font size, so at font scale 1 an
 * already-fitting single-line label renders identically to a plain [Text].
 */
@Composable
fun AutoShrinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    minFontSize: TextUnit = 9.sp,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        val wraps = maxLines > 1
        val fittedStyle = remember(text, style, minFontSize, maxWidthPx, maxHeightPx, maxLines, density) {
            val baseSize = if (style.fontSize.isSp) style.fontSize.value else 14f
            val floor = if (minFontSize.isSp) minFontSize.value else 9f
            if (maxWidthPx == Constraints.Infinity) return@remember style

            fun sizedStyle(sizeSp: Float): TextStyle {
                var lineHeightSp = if (style.lineHeight.isSp) {
                    style.lineHeight.value * sizeSp / baseSize
                } else {
                    Float.NaN
                }
                // Wrapped labels take a tighter line height (1.25x the font,
                // vs Material's ~1.5x): at a 2.0 font scale two lines at the
                // default spacing overflowed a 72dp answer pill even at the
                // size floor.
                if (wraps) {
                    val tight = sizeSp * WRAPPED_LINE_HEIGHT
                    lineHeightSp = if (lineHeightSp.isNaN()) tight else minOf(lineHeightSp, tight)
                }
                return style.copy(
                    fontSize = sizeSp.sp,
                    lineHeight = if (lineHeightSp.isNaN()) style.lineHeight else lineHeightSp.sp,
                )
            }

            fun fits(sizeSp: Float): Boolean {
                val sized = sizedStyle(sizeSp)
                val result = measurer.measure(
                    text = text,
                    style = sized,
                    softWrap = wraps,
                    maxLines = maxLines,
                    constraints = Constraints(
                        maxWidth = maxWidthPx,
                        maxHeight = if (wraps) maxHeightPx else Constraints.Infinity,
                    ),
                )
                return !result.didOverflowWidth && !(wraps && result.didOverflowHeight)
            }

            var size = baseSize
            if (!fits(size)) {
                var lo = floor
                var hi = baseSize
                repeat(7) {
                    val mid = (lo + hi) / 2f
                    if (fits(mid)) lo = mid else hi = mid
                }
                size = lo
            }
            sizedStyle(size)
        }
        Text(
            text = text,
            style = fittedStyle,
            maxLines = maxLines,
            softWrap = wraps,
            overflow = TextOverflow.Ellipsis,
            color = color,
            textAlign = textAlign,
        )
    }
}

/**
 * One shared shrink factor (1 down to [minScale]) that makes ALL [labels],
 * rendered side by side in [style] with [reservedPerItem] of non-text width
 * each (button padding, spacing), fit inside [availableWidth]. Apply it to
 * every label's font size so a whole row shrinks uniformly instead of one
 * button wrapping (policy rule 3).
 */
@Composable
fun rememberUniformTextScale(
    labels: List<String>,
    style: TextStyle,
    availableWidth: Dp,
    reservedPerItem: Dp = 0.dp,
    minScale: Float = 0.55f,
    // Each item is at least this wide however short its label, e.g. a
    // button's 48dp minimum touch target (plus the row spacing).
    minItemWidth: Dp = 0.dp,
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, availableWidth, reservedPerItem, minScale, minItemWidth, density) {
        with(density) {
            val availablePx = availableWidth.toPx()
            val reservedPx = reservedPerItem.toPx()
            val minItemPx = minItemWidth.toPx()
            largestFittingScale(minScale) { scale ->
                labels.sumOf { label ->
                    maxOf(measurer.labelWidth(label, style, scale) + reservedPx, minItemPx).toDouble()
                } <= availablePx
            }
        }
    }
}

/**
 * The largest scale in [minScale]..1 for which [fits] holds, or [minScale]
 * when none does. A binary search over real measurements rather than one
 * width ratio: Android 14+ scales fonts nonlinearly, so at a 2.0 font scale
 * halving a 14sp label's sp shrinks it by well under half, and a ratio-based
 * factor still overflowed ("Dele", "Charact…" at 360dp).
 */
private fun largestFittingScale(minScale: Float, fits: (Float) -> Boolean): Float {
    if (fits(1f)) return 1f
    var lo = minScale
    var hi = 1f
    repeat(8) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

private fun TextMeasurer.labelWidth(label: String, style: TextStyle, scale: Float): Int =
    measure(
        text = label,
        style = style.copy(fontSize = style.fontSize * scale),
        softWrap = false,
        maxLines = 1,
    ).size.width

/**
 * One shared shrink factor (1 down to [minScale]) for labels laid out in
 * EQUAL-width columns — e.g. a full-width [androidx.compose.material3.TabRow],
 * where every tab gets the same 1/N share of the width regardless of content.
 * The binding constraint is the WIDEST label fitting its own share of
 * [availableWidth] minus [reservedPerItem]; unlike [rememberUniformTextScale]
 * (which fits the SUM of labels into the total, for content-packed rows), this
 * fits the widest into a single share so no equal-width cell clips.
 */
@Composable
fun rememberEqualShareTextScale(
    labels: List<String>,
    style: TextStyle,
    availableWidth: Dp,
    reservedPerItem: Dp = 0.dp,
    minScale: Float = 0.7f,
): Float = rememberEqualShareFit(labels, style, availableWidth, reservedPerItem, minScale) ?: minScale

/**
 * [rememberEqualShareTextScale] that admits failure: null when even
 * [minScale] doesn't fit the widest label into its share, so the caller can
 * switch to a layout with more room per label instead of clipping.
 */
@Composable
fun rememberEqualShareFit(
    labels: List<String>,
    style: TextStyle,
    availableWidth: Dp,
    reservedPerItem: Dp = 0.dp,
    minScale: Float = 0.7f,
): Float? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, availableWidth, reservedPerItem, minScale, density) {
        with(density) {
            if (labels.isEmpty()) return@with 1f
            val sharePx = availableWidth.toPx() / labels.size - reservedPerItem.toPx()
            if (sharePx <= 0f) return@with null
            val fits = { scale: Float ->
                labels.maxOf { label -> measurer.labelWidth(label, style, scale) } <= sharePx
            }
            largestFittingScale(minScale, fits).takeIf(fits)
        }
    }
}
