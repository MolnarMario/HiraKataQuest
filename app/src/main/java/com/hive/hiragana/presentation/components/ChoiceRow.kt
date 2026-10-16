package com.hive.hiragana.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A pick-one control: a Material segmented button row while every label fits
 * its segment, and a full-width vertical list when one doesn't.
 *
 * Why not a plain [SingleChoiceSegmentedButtonRow]: each segment gets an equal
 * share of the width, and M3 places the label 26dp right of the segment's
 * padding (room for the checkmark) without shrinking it, so a label wider than
 * the share minus [SegmentReserve] runs off the edge. At a 2.0 font scale on a
 * 360dp phone that cut "Kana → romaji" to "Kana →" and "System" to "Syste/m".
 *
 * So the labels first shrink together (one factor, measured outside the row,
 * since anything that subcomposes inside a segment label crashes on M3's
 * intrinsic measurement) down to [MinLabelScale]. Past that the choices stack,
 * one per line at full width, where the label keeps the user's font size and
 * may wrap: these are content labels, not chrome.
 *
 * [icons] are the per-option icons shown when NOT selected (the selected one
 * shows a checkmark, as SegmentedButtonDefaults.Icon does). [trailing] draws
 * extra content after option i's label, such as an ⓘ, and must fit
 * [trailingWidth].
 */
@Composable
fun ChoiceRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    trailingWidth: Dp = 0.dp,
    trailing: (@Composable (index: Int) -> Unit)? = null,
) {
    // Measure in bold: the selected segment is bold, and any segment can be
    // the selected one.
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val scale = rememberEqualShareFit(
            labels = labels,
            style = style,
            availableWidth = maxWidth,
            reservedPerItem = SegmentReserve + trailingWidth,
            minScale = MinLabelScale,
        )
        if (scale != null) {
            SegmentedChoices(labels, selectedIndex, onSelect, icons, trailing, scale)
        } else {
            StackedChoices(labels, selectedIndex, onSelect, icons, trailing)
        }
    }
}

@Composable
private fun SegmentedChoices(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icons: List<ImageVector?>,
    trailing: (@Composable (index: Int) -> Unit)?,
    scale: Float,
) {
    val base = MaterialTheme.typography.labelLarge
    val fontSize = base.fontSize * scale
    val lineHeight = if (base.lineHeight.isSp) base.lineHeight * scale else base.lineHeight
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val icon = icons.getOrNull(index)
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                icon = {
                    SegmentedButtonDefaults.Icon(
                        active = selected,
                        inactiveContent = icon?.let {
                            {
                                Icon(
                                    imageVector = it,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            }
                        },
                    )
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                        )
                        trailing?.invoke(index)
                    }
                },
            )
        }
    }
}

@Composable
private fun StackedChoices(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icons: List<ImageVector?>,
    trailing: (@Composable (index: Int) -> Unit)?,
) {
    // Same colors as the segmented row, so the control reads as the same
    // thing turned on its side.
    val colors = SegmentedButtonDefaults.colors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.inactiveContainerColor,
        border = BorderStroke(1.dp, colors.inactiveBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        Column {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                if (index > 0) HorizontalDivider(color = colors.inactiveBorderColor)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        )
                        .background(if (selected) colors.activeContainerColor else Color.Transparent)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    val contentColor =
                        if (selected) colors.activeContentColor else colors.inactiveContentColor
                    Box(modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) {
                        val icon = if (selected) Icons.Filled.Check else icons.getOrNull(index)
                        if (icon != null) {
                            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    trailing?.invoke(index)
                }
            }
        }
    }
}

/**
 * Width a segment spends on things other than its label: 12dp padding each
 * side ([androidx.compose.material3.ButtonDefaults.TextButtonContentPadding])
 * plus the 18dp check icon and the 8dp gap M3 always reserves before it.
 */
private val SegmentReserve = 12.dp + 18.dp + 8.dp + 12.dp

/**
 * How far the labels may shrink before the choices stack instead. Below 0.75
 * a 14sp label drops under ~11sp, too small for a control people tap.
 */
private const val MinLabelScale = 0.75f
