@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.timestablequest.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.progress.FactStatus
import com.timestablequest.app.ui.theme.Atlas
import kotlin.random.Random

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Atlas.Paper, titleContentColor = Atlas.Navy),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Atlas.Navy,
        modifier = modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Paper card with a thin grid-line border. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    color: Color = Atlas.PaperCard,
    border: Color = Atlas.GridLine,
    content: @Composable () -> Unit,
) {
    Surface(
        color = color,
        border = BorderStroke(1.dp, border),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
        content = content,
    )
}

/** Status shown with a symbol and text, never colour alone. */
@Composable
fun StatusLabel(status: FactStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        FactStatus.NOT_PRACTICED -> Atlas.TextMuted
        FactStatus.PRACTICING -> Atlas.Blue
        FactStatus.CONFIDENT -> Atlas.Correct
    }
    Text(
        "${status.symbol} ${status.label}",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

/** Multi-select of rows ×1…×12 as filter chips; at least one row must stay selected. */
@Composable
fun RowSelector(
    selected: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    singleChoice: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FACTORS.forEach { r ->
                val on = r in selected
                FilterChip(
                    selected = on,
                    onClick = {
                        val next = if (on) selected - r else selected + r
                        if (next.isNotEmpty()) onChange(next)
                    },
                    label = { Text("×$r", style = MaterialTheme.typography.titleSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Atlas.Yellow,
                        selectedLabelColor = Atlas.Navy,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "Row $r"
                            stateDescription = if (on) "Selected" else "Not selected"
                        },
                )
            }
        }
        if (!singleChoice) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onChange(FACTORS.toSet()) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Select all") }
            }
            Text(
                "${selected.size} of 12 rows selected. At least one row is required.",
                style = MaterialTheme.typography.bodySmall,
                color = Atlas.TextMuted,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) } },
    )
}

/**
 * Adult-entry check before destructive actions: a two-digit multiplication typed into a text field
 * (works with screen readers and switch access). It prevents accidental changes; it is not
 * authentication. Optionally offers a "also delete saved history" checkbox.
 */
@Composable
fun AdultGateDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    onConfirm: (alsoClearHistory: Boolean) -> Unit,
    onDismiss: () -> Unit,
    historyOptionLabel: String? = null,
) {
    val a by rememberSaveable { mutableIntStateOf(Random.nextInt(13, 20)) }
    val b by rememberSaveable { mutableIntStateOf(Random.nextInt(13, 20)) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    var clearHistory by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation)
                if (historyOptionLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = clearHistory, role = Role.Checkbox, onValueChange = { clearHistory = it }),
                    ) {
                        Checkbox(checked = clearHistory, onCheckedChange = null)
                        Text(historyOptionLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text("For grown-ups: what is $a × $b?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = input,
                    onValueChange = { v ->
                        input = v.filter { it.isDigit() }.take(4)
                        wrong = false
                    },
                    label = { Text("Answer") },
                    singleLine = true,
                    isError = wrong,
                    supportingText = { if (wrong) Text("That's not right. Please try again.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (input.toIntOrNull() == a * b) onConfirm(clearHistory) else wrong = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

/** Shown when another non-daily session is unfinished. */
@Composable
fun UnfinishedSessionDialog(
    sessionLabel: String,
    onResume: () -> Unit,
    onEndAndStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unfinished practice") },
        text = { Text("You have an unfinished $sessionLabel session. Resume it, or end it and start the new one? Answers you already gave are kept.") },
        confirmButton = { TextButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("Resume") } },
        dismissButton = { TextButton(onClick = onEndAndStart, modifier = Modifier.heightIn(min = 48.dp)) { Text("End it and start new") } },
    )
}

fun Modifier.minTouch(): Modifier = this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)

fun formatPercent(p: Int?): String = if (p == null) "Not practiced yet" else "$p%"

fun Modifier.buttonRole(): Modifier = this.semantics { role = Role.Button }
