package com.timestablequest.app.ui.row

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.progress.FactStats
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.common.StatusLabel
import com.timestablequest.app.ui.common.UnfinishedSessionDialog
import com.timestablequest.app.ui.common.formatPercent
import com.timestablequest.app.ui.theme.Atlas

@Composable
fun RowScreen(
    onBack: () -> Unit,
    onStudy: (row: Int, column: Int) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val vm = appViewModel { c, h -> RowViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val row = state.row

    state.blocked?.let { blocked ->
        UnfinishedSessionDialog(
            sessionLabel = vm.blockedLabel(),
            onResume = {
                vm.dismissBlocked()
                onOpenSession(blocked.activeSessionId)
            },
            onEndAndStart = { vm.endBlockingAndStart(onOpenSession) },
            onDismiss = vm::dismissBlocked,
        )
    }

    Scaffold(
        topBar = { AppTopBar("Row ×$row", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        Facts.rowTitle(row),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Atlas.Navy,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onStudy(row, 1) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) { Text("Study") }
                        Button(
                            onClick = { vm.startCheck(onOpenSession) },
                            colors = ButtonDefaults.buttonColors(containerColor = Atlas.LavenderDeep),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) { Text("Check (10 questions)") }
                    }
                    RowSummary(state)
                    Text(
                        "Facts",
                        style = MaterialTheme.typography.titleMedium,
                        color = Atlas.Navy,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            items(state.facts, key = { it.fact.key }) { fs ->
                FactTile(fs, viewed = fs.fact in state.studied, onClick = { onStudy(row, fs.fact.column) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                NeedsPracticeSection(state, onStudy = { c -> onStudy(row, c) })
            }
        }
    }
}

@Composable
private fun RowSummary(state: RowState) {
    val s = state.stats
    PaperCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✓ ${s.correctAtLeastOnce} of 12 answered correctly at least once", style = MaterialTheme.typography.bodyLarge)
            Text("★ ${s.confident} of 12 confident", style = MaterialTheme.typography.bodyLarge)
            Text("Accuracy: ${formatPercent(s.accuracyPercent)}", style = MaterialTheme.typography.bodyLarge)
            Text("Needs Practice: ${s.needsPractice}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Viewed in Study: ${state.studied.size} of 12 (viewing is not scored)",
                style = MaterialTheme.typography.bodySmall,
                color = Atlas.TextMuted,
            )
        }
    }
}

@Composable
private fun FactTile(fs: FactStats, viewed: Boolean, onClick: () -> Unit) {
    val f = fs.fact
    val label = buildString {
        append("${f.spoken} equals ${f.product}. ${fs.status.label}")
        if (fs.reviewNeeded) append(", needs practice")
        if (viewed) append(", viewed in Study")
        append(". Opens the explanation.")
    }
    Surface(
        onClick = onClick,
        color = Atlas.rowFill(f.row),
        border = BorderStroke(if (fs.reviewNeeded) 2.dp else 1.dp, if (fs.reviewNeeded) Atlas.Incorrect else Atlas.GridLine),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${f.expression} = ${f.product}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Atlas.Navy)
            StatusLabel(fs.status)
            if (fs.reviewNeeded) Text("! Needs practice", style = MaterialTheme.typography.labelMedium, color = Atlas.Incorrect)
            if (viewed) Text("◉ Viewed", style = MaterialTheme.typography.labelMedium, color = Atlas.TextMuted)
        }
    }
}

@Composable
private fun NeedsPracticeSection(state: RowState, onStudy: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "Needs Practice in this row",
            style = MaterialTheme.typography.titleMedium,
            color = Atlas.Navy,
            modifier = Modifier.semantics { heading() },
        )
        if (state.needsPractice.isEmpty()) {
            Text("Nothing to review in this row right now.", style = MaterialTheme.typography.bodyMedium, color = Atlas.TextMuted)
        } else {
            state.needsPractice.forEach { f ->
                OutlinedButton(
                    onClick = { onStudy(f.column) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text("${f.expression} — look again") }
            }
        }
    }
}
