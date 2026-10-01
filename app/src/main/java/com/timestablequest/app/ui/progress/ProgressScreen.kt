package com.timestablequest.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.progress.RowProgress
import com.timestablequest.app.domain.progress.RowStats
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.common.SectionTitle
import com.timestablequest.app.ui.common.formatPercent
import com.timestablequest.app.ui.theme.Atlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProgressViewModel(container: AppContainer) : ViewModel() {
    private val practice = container.practice
    private val _rows = MutableStateFlow<List<RowStats>?>(null)
    val rows: StateFlow<List<RowStats>?> = _rows.asStateFlow()

    init {
        viewModelScope.launch {
            practice.changes.collect { _rows.value = RowProgress.all(practice.factStats()) }
        }
    }
}

@Composable
fun ProgressScreen(onOpenRow: (Int) -> Unit) {
    val vm = appViewModel { c, _ -> ProgressViewModel(c) }
    val rows by vm.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar("Progress", onBack = null) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Definitions(Modifier.widthIn(max = 720.dp)) }
            item { SectionTitle("By row", Modifier.widthIn(max = 720.dp).fillMaxWidth()) }
            items(rows.orEmpty(), key = { it.row }) { r ->
                RowProgressCard(r, onClick = { onOpenRow(r.row) }, modifier = Modifier.widthIn(max = 720.dp))
            }
        }
    }
}

@Composable
private fun Definitions(modifier: Modifier) {
    PaperCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What the numbers mean", style = MaterialTheme.typography.titleMedium, color = Atlas.Navy)
            Text("✓ Correct: facts answered correctly at least once in a check, mixed practice or Daily 10 (out of 12).", style = MaterialTheme.typography.bodyMedium)
            Text("★ Confident: the last three answers to a fact were correct without a hint, in at least two different sessions. An app status, not a promise of permanent mastery.", style = MaterialTheme.typography.bodyMedium)
            Text("Accuracy: correct first answers ÷ all first answers in the row × 100. Study browsing and Needs Practice retries are not counted.", style = MaterialTheme.typography.bodyMedium)
            Text("Needs Practice: facts missed in practice and not yet answered correctly in a review.", style = MaterialTheme.typography.bodyMedium)
            Text("3 × 7 and 7 × 3 are tracked separately: each belongs to its own row.", style = MaterialTheme.typography.bodyMedium, color = Atlas.TextMuted)
        }
    }
}

@Composable
private fun RowProgressCard(r: RowStats, onClick: () -> Unit, modifier: Modifier) {
    val acc = formatPercent(r.accuracyPercent)
    val label = "${Facts.rowTitle(r.row)}. ${r.correctAtLeastOnce} of 12 correct at least once, " +
        "${r.confident} of 12 confident, accuracy $acc, ${r.needsPractice} need practice. Opens the row."
    Surface(
        onClick = onClick,
        color = Atlas.rowFill(r.row),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = label },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("×${r.row}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Atlas.Navy)
            Column(Modifier.weight(1f)) {
                Text("✓ ${r.correctAtLeastOnce}/12 correct · ★ ${r.confident}/12 confident", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Accuracy: $acc" + if (r.practiced) " (${r.originalCorrect} of ${r.originalAttempts})" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Needs Practice: ${r.needsPractice}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
