package com.hive.hiragana.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A multi-select category dropdown for the Library Words tab (and word-set
 * selection): tap to open a checkable list of the categories present, pick one
 * or more to narrow the list, "All" to clear. Renders nothing when there are no
 * [categories] (katakana has none — the feature is hiragana-only).
 *
 * Stateless: the caller owns [selected] (a de-duped set of category names) and
 * the [onToggle] / [onClear] mutations, so the same control can drive both the
 * browse list and the set-selection list.
 */
@Composable
fun CategoryFilter(
    categories: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    // Fixed button text; when null the button reflects the current selection.
    // The browse tab passes "Category" because it shows the selection as
    // separate removable chips instead.
    fixedLabel: String? = null,
) {
    if (categories.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val label = fixedLabel ?: when (selected.size) {
        0 -> "Category"
        1 -> selected.first().replaceFirstChar { it.uppercase() }
        else -> "${selected.size} categories"
    }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All categories") },
                onClick = { onClear() },
                trailingIcon = {
                    if (selected.isEmpty()) {
                        Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
                    }
                },
            )
            HorizontalDivider(color = DividerDefaults.color)
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(category.replaceFirstChar { it.uppercase() }, maxLines = 1)
                        },
                        onClick = { onToggle(category) },
                        leadingIcon = {
                            Checkbox(checked = category in selected, onCheckedChange = null)
                        },
                    )
                }
            }
        }
    }
}
