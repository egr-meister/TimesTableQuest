package com.timestablequest.app.ui.row

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.SessionType
import com.timestablequest.app.data.repository.StartResult
import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.progress.FactStats
import com.timestablequest.app.domain.progress.RowProgress
import com.timestablequest.app.domain.progress.RowStats
import com.timestablequest.app.domain.review.ReviewQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class RowState(
    val row: Int,
    val loaded: Boolean = false,
    val facts: List<FactStats> = Facts.row(row).map { FactStats.empty(it) },
    val stats: RowStats = RowStats(row, 0, 0, 0, 0, 0),
    val studied: Set<Fact> = emptySet(),
    val needsPractice: List<Fact> = emptyList(),
    /** Set when a check could not start because another session is unfinished. */
    val blocked: StartResult.Blocked? = null,
)

class RowViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    companion object {
        const val ARG_ROW = "row"
    }

    private val practice = container.practice
    private val prefs = container.preferences
    val row: Int = (handle.get<Int>(ARG_ROW) ?: 1).coerceIn(FACTORS.first, FACTORS.last)

    private val _state = MutableStateFlow(RowState(row))
    val state: StateFlow<RowState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(practice.changes, prefs.studyVisited) { _, visited -> visited }.collect { visited ->
                val all = practice.factStats()
                val facts = Facts.row(row).map { all[it] ?: FactStats.empty(it) }
                _state.value = _state.value.copy(
                    loaded = true,
                    facts = facts,
                    stats = RowProgress.compute(row) { f -> all[f] ?: FactStats.empty(f) },
                    studied = visited.filter { it.row == row }.toSet(),
                    needsPractice = ReviewQueue.queue(facts).map { it.fact },
                )
            }
        }
    }

    fun startCheck(onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            when (val r = practice.startRowCheck(row)) {
                is StartResult.Started -> onStarted(r.sessionId)
                is StartResult.Blocked -> _state.value = _state.value.copy(blocked = r)
                StartResult.Empty -> Unit
            }
        }
    }

    fun endBlockingAndStart(onStarted: (Long) -> Unit) {
        val blocked = _state.value.blocked ?: return
        _state.value = _state.value.copy(blocked = null)
        viewModelScope.launch {
            practice.endSession(blocked.activeSessionId)
            startCheck(onStarted)
        }
    }

    fun dismissBlocked() {
        _state.value = _state.value.copy(blocked = null)
    }

    fun blockedLabel(): String = _state.value.blocked?.activeType?.label?.lowercase() ?: SessionType.MIXED.label
}
