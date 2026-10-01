package com.timestablequest.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.StartResult
import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.progress.FactStats
import com.timestablequest.app.domain.review.ReviewQueue
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.common.RowSelector
import com.timestablequest.app.ui.common.UnfinishedSessionDialog
import com.timestablequest.app.ui.theme.Atlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Mixed practice

data class MixedState(
    val rows: Set<Int> = FACTORS.toSet(),
    val loaded: Boolean = false,
    val blocked: StartResult.Blocked? = null,
)

class MixedSetupViewModel(container: AppContainer) : ViewModel() {
    private val practice = container.practice
    private val prefs = container.preferences
    private val _state = MutableStateFlow(MixedState())
    val state: StateFlow<MixedState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(rows = prefs.current().mixedRows, loaded = true)
        }
    }

    fun setRows(rows: Set<Int>) {
        if (rows.isNotEmpty()) _state.value = _state.value.copy(rows = rows)
    }

    fun start(onStarted: (Long) -> Unit) {
        val rows = _state.value.rows
        viewModelScope.launch {
            when (val r = practice.startMixed(rows)) {
                is StartResult.Started -> onStarted(r.sessionId)
                is StartResult.Blocked -> _state.value = _state.value.copy(blocked = r)
                StartResult.Empty -> Unit
            }
        }
    }

    fun endBlockingAndStart(onStarted: (Long) -> Unit) {
        val b = _state.value.blocked ?: return
        _state.value = _state.value.copy(blocked = null)
        viewModelScope.launch {
            practice.endSession(b.activeSessionId)
            start(onStarted)
        }
    }

    fun dismissBlocked() {
        _state.value = _state.value.copy(blocked = null)
    }
}

@Composable
fun MixedSetupScreen(onBack: () -> Unit, onStarted: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> MixedSetupViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    state.blocked?.let { b ->
        UnfinishedSessionDialog(
            sessionLabel = b.activeType.label.lowercase(),
            onResume = {
                vm.dismissBlocked()
                onStarted(b.activeSessionId)
            },
            onEndAndStart = { vm.endBlockingAndStart(onStarted) },
            onDismiss = vm::dismissBlocked,
        )
    }

    Scaffold(
        topBar = { AppTopBar("Mixed Practice", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Choose the rows to mix. Ten questions are shared out as evenly as possible across them.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                RowSelector(selected = state.rows, onChange = vm::setRows)
                Button(
                    onClick = { vm.start(onStarted) },
                    enabled = state.loaded && state.rows.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Atlas.LavenderDeep),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Start 10 questions") }
            }
        }
    }
}

// ------------------------------------------------------------------ Needs Practice

data class NeedsPracticeState(
    val loaded: Boolean = false,
    val queue: List<FactStats> = emptyList(),
    val blocked: StartResult.Blocked? = null,
)

class NeedsPracticeViewModel(container: AppContainer) : ViewModel() {
    private val practice = container.practice
    private val _state = MutableStateFlow(NeedsPracticeState())
    val state: StateFlow<NeedsPracticeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            practice.changes.collect {
                _state.value = _state.value.copy(loaded = true, queue = ReviewQueue.queue(practice.factStats().values))
            }
        }
    }

    fun start(onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            when (val r = practice.startReview()) {
                is StartResult.Started -> onStarted(r.sessionId)
                is StartResult.Blocked -> _state.value = _state.value.copy(blocked = r)
                StartResult.Empty -> Unit
            }
        }
    }

    fun endBlockingAndStart(onStarted: (Long) -> Unit) {
        val b = _state.value.blocked ?: return
        _state.value = _state.value.copy(blocked = null)
        viewModelScope.launch {
            practice.endSession(b.activeSessionId)
            start(onStarted)
        }
    }

    fun dismissBlocked() {
        _state.value = _state.value.copy(blocked = null)
    }
}

@Composable
fun NeedsPracticeScreen(onBack: () -> Unit, onStarted: (Long) -> Unit, onOpenRow: (Int) -> Unit) {
    val vm = appViewModel { c, _ -> NeedsPracticeViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    state.blocked?.let { b ->
        UnfinishedSessionDialog(
            sessionLabel = b.activeType.label.lowercase(),
            onResume = {
                vm.dismissBlocked()
                onStarted(b.activeSessionId)
            },
            onEndAndStart = { vm.endBlockingAndStart(onStarted) },
            onDismiss = vm::dismissBlocked,
        )
    }

    Scaffold(
        topBar = { AppTopBar("Needs Practice", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.loaded) return@Column
                if (state.queue.isEmpty()) {
                    PaperCard(Modifier.fillMaxWidth()) {
                        Text(
                            "No review questions right now. Try a row or mixed practice.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    return@Column
                }
                val sessionSize = state.queue.size.coerceAtMost(ReviewQueue.MAX_SESSION_SIZE)
                Text(
                    "Facts you missed come back here. Answer one correctly in a review and it leaves the list. " +
                        "Your original answers and accuracy are never changed.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { vm.start(onStarted) },
                    colors = ButtonDefaults.buttonColors(containerColor = Atlas.LavenderDeep),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Review $sessionSize ${if (sessionSize == 1) "fact" else "facts"}") }
                Text("Waiting for review (${state.queue.size})", style = MaterialTheme.typography.titleMedium, color = Atlas.Navy)
                state.queue.forEach { fs ->
                    QueueRow(fs.fact, onOpenRow)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(f: Fact, onOpenRow: (Int) -> Unit) {
    OutlinedButton(
        onClick = { onOpenRow(f.row) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text("${f.expression} · row ×${f.row}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("Open row ›", style = MaterialTheme.typography.labelLarge)
    }
}
