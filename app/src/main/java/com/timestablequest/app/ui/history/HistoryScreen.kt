package com.timestablequest.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.PracticeService
import com.timestablequest.app.data.repository.SessionSummary
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.formatPercent
import com.timestablequest.app.ui.theme.Atlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val practice = container.practice
    private val _items = MutableStateFlow<List<SessionSummary>?>(null)
    val items: StateFlow<List<SessionSummary>?> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            practice.changes.collect { _items.value = practice.summaries() }
        }
    }
}

@Composable
fun HistoryScreen(onOpen: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> HistoryViewModel(c) }
    val items by vm.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar("History", onBack = null) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val list = items
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No practice yet. Sessions you play appear here.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "Keeps the latest ${PracticeService.RETAIN_NON_DAILY} practice sessions and ${PracticeService.RETAIN_DAILY} Daily 10 sets. " +
                        "Older records are removed, but progress and Needs Practice are never reduced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Atlas.TextMuted,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
            items(list.orEmpty(), key = { it.id }) { s -> HistoryRow(s, onClick = { onOpen(s.id) }) }
        }
    }
}

@Composable
private fun HistoryRow(s: SessionSummary, onClick: () -> Unit) {
    val date = s.dailyDate ?: DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.startedAt))
    val accuracy = if (s.answered == 0) "No answers" else formatPercent(s.accuracyPercent)
    val rows = if (s.type.name == "REVIEW") "Review" else Facts.rowsLabel(s.rows)
    val label = "${s.type.label}, $date, $rows. ${s.answered} of ${s.total} answered, accuracy $accuracy. ${s.status.label}."
    Surface(
        onClick = onClick,
        color = Atlas.PaperCard,
        border = BorderStroke(1.dp, Atlas.GridLine),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = label },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${s.type.label} · $date", style = MaterialTheme.typography.titleSmall, color = Atlas.Navy)
            Text(rows, style = MaterialTheme.typography.bodyMedium, color = Atlas.TextMuted)
            Text(
                "${s.answered} of ${s.total} answered · accuracy $accuracy · ${s.status.label}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
