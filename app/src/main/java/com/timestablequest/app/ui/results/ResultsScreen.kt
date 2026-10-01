package com.timestablequest.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.QuestionView
import com.timestablequest.app.data.repository.SessionDetail
import com.timestablequest.app.data.repository.SessionStatus
import com.timestablequest.app.data.repository.SessionType
import com.timestablequest.app.data.repository.StartResult
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.progress.Percent
import com.timestablequest.app.ui.appViewModel
import com.timestablequest.app.ui.common.AppTopBar
import com.timestablequest.app.ui.common.PaperCard
import com.timestablequest.app.ui.common.SectionTitle
import com.timestablequest.app.ui.common.UnfinishedSessionDialog
import com.timestablequest.app.ui.common.formatPercent
import com.timestablequest.app.ui.theme.Atlas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class ResultsState(
    val loaded: Boolean = false,
    val detail: SessionDetail? = null,
    /** Facts from this session that are currently in the Needs Practice queue. */
    val reviewNeeded: List<Fact> = emptyList(),
    val totalQueued: Int = 0,
    val blocked: StartResult.Blocked? = null,
    val emptyReview: Boolean = false,
)

class ResultsViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }

    private val practice = container.practice
    private val sessionId: Long = handle.get<Long>(ARG_SESSION_ID) ?: -1L
    private val _state = MutableStateFlow(ResultsState())
    val state: StateFlow<ResultsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            practice.changes.collect {
                val detail = practice.session(sessionId)
                val stats = practice.factStats()
                val sessionFacts = detail?.questions?.map { it.fact }?.distinct().orEmpty()
                _state.value = _state.value.copy(
                    loaded = true,
                    detail = detail,
                    reviewNeeded = sessionFacts.filter { stats[it]?.reviewNeeded == true },
                    totalQueued = stats.values.count { it.reviewNeeded },
                )
            }
        }
    }

    fun reviewMistakes(onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            when (val r = practice.startReview()) {
                is StartResult.Started -> onStarted(r.sessionId)
                is StartResult.Blocked -> _state.value = _state.value.copy(blocked = r)
                StartResult.Empty -> _state.value = _state.value.copy(emptyReview = true)
            }
        }
    }

    fun endBlockingAndReview(onStarted: (Long) -> Unit) {
        val b = _state.value.blocked ?: return
        _state.value = _state.value.copy(blocked = null)
        viewModelScope.launch {
            practice.endSession(b.activeSessionId)
            reviewMistakes(onStarted)
        }
    }

    fun dismissBlocked() {
        _state.value = _state.value.copy(blocked = null)
    }
}

@Composable
fun ResultsScreen(onBack: () -> Unit, onBackToMap: () -> Unit, onOpenSession: (Long) -> Unit) {
    val vm = appViewModel { c, h -> ResultsViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()

    state.blocked?.let { b ->
        UnfinishedSessionDialog(
            sessionLabel = b.activeType.label.lowercase(),
            onResume = {
                vm.dismissBlocked()
                onOpenSession(b.activeSessionId)
            },
            onEndAndStart = { vm.endBlockingAndReview(onOpenSession) },
            onDismiss = vm::dismissBlocked,
        )
    }

    Scaffold(
        topBar = { AppTopBar("Results", onBack) },
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
                val d = state.detail
                if (!state.loaded) return@Column
                if (d == null) {
                    Text("This session is no longer in the saved history.", style = MaterialTheme.typography.bodyLarge)
                    return@Column
                }
                Summary(d)
                if (d.type == SessionType.DAILY || d.type == SessionType.MIXED) RowBreakdown(d)

                if (state.reviewNeeded.isNotEmpty()) {
                    SectionTitle("Needs Practice from this session")
                    Text(
                        state.reviewNeeded.joinToString(", ") { it.expression },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (state.emptyReview) {
                    Text("No review questions right now. Try a row or mixed practice.", style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.totalQueued > 0) {
                        Button(
                            onClick = { vm.reviewMistakes(onOpenSession) },
                            colors = ButtonDefaults.buttonColors(containerColor = Atlas.LavenderDeep),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) { Text("Review mistakes") }
                    }
                    OutlinedButton(
                        onClick = onBackToMap,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                    ) { Text("Back to map") }
                }

                SectionTitle("Question review")
                d.questions.forEach { q -> QuestionRow(q) }
                if (d.type == SessionType.REVIEW) {
                    Text(
                        "Review answers are retries: they never change original accuracy or Confident status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Atlas.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(d: SessionDetail) {
    val dateText = d.dailyDate ?: DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(d.startedAt))
    PaperCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.type.label, style = MaterialTheme.typography.headlineSmall, color = Atlas.Navy)
            Text("$dateText · ${d.rowsLabel}", style = MaterialTheme.typography.bodyMedium, color = Atlas.TextMuted)
            Text(
                when (d.status) {
                    SessionStatus.COMPLETED -> "Completed"
                    SessionStatus.ENDED_EARLY -> "Ended early"
                    SessionStatus.INCOMPLETE -> "Incomplete"
                    SessionStatus.ACTIVE -> "In progress"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${d.correct} correct out of ${d.answered} answered" +
                    if (d.answered < d.questions.size) " (${d.questions.size - d.answered} not answered, not counted)" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Incorrect: ${d.incorrect}", style = MaterialTheme.typography.bodyLarge)
            Text("Accuracy: ${formatPercent(d.accuracyPercent).let { if (d.answered == 0) "No answers" else it }}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RowBreakdown(d: SessionDetail) {
    val byRow = d.questions.filter { it.answered }.groupBy { it.fact.row }.toSortedMap()
    if (byRow.isEmpty()) return
    SectionTitle("By row")
    PaperCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            byRow.forEach { (row, qs) ->
                val correct = qs.count { it.isCorrect == true }
                Text(
                    "×$row: $correct of ${qs.size} correct (${Percent.of(correct, qs.size)}%)",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun QuestionRow(q: QuestionView) {
    val f = q.fact
    val (mark, text) = when (q.isCorrect) {
        true -> "✓" to "${f.expression} = ${f.product}. Answered ${q.selectedAnswer}${if (q.hintUsed) " (hint used)" else ""}."
        false -> "✗" to "${f.expression} = ${f.product}. Answered ${q.selectedAnswer}."
        null -> "–" to "${f.expression}: not answered."
    }
    val spoken = when (q.isCorrect) {
        true -> "Correct. ${f.spoken} equals ${f.product}."
        false -> "Incorrect. ${f.spoken} equals ${f.product}. You answered ${q.selectedAnswer}."
        null -> "${f.spoken}. Not answered."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            mark,
            style = MaterialTheme.typography.titleMedium,
            color = when (q.isCorrect) {
                true -> Atlas.Correct
                false -> Atlas.Incorrect
                null -> Atlas.TextMuted
            },
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
