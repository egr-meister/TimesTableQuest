package com.timestablequest.app.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.QuestionView
import com.timestablequest.app.data.repository.SessionDetail
import com.timestablequest.app.data.repository.SessionStatus
import com.timestablequest.app.data.repository.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeUiState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val detail: SessionDetail? = null,
    val position: Int = 0,
    /** The session is not active (completed, ended or incomplete): show its results. */
    val finished: Boolean = false,
    /** The local date changed during a daily set. */
    val newDay: Boolean = false,
    val busy: Boolean = false,
) {
    val question: QuestionView? get() = detail?.questions?.getOrNull(position)
    val isLast: Boolean get() = detail != null && position >= detail.questions.size - 1
}

/**
 * Drives one persisted session. Every visible state (question position, option order, the chosen
 * answer, feedback and hint use) is stored, so the screen recovers after rotation, process death or
 * relaunch. The first answer is final; there is no timer and no automatic advance.
 */
class PracticeViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }

    private val practice = container.practice
    private val prefs = container.preferences
    val sessionId: Long = handle.get<Long>(ARG_SESSION_ID) ?: -1L

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load(initial = true) }
    }

    private suspend fun load(initial: Boolean = false) {
        val detail = practice.session(sessionId)
        if (detail == null) {
            _state.value = PracticeUiState(loaded = true, missing = true)
            return
        }
        val position = if (initial) detail.currentPosition else _state.value.position
        _state.value = _state.value.copy(
            loaded = true,
            detail = detail,
            position = position.coerceIn(0, (detail.questions.size - 1).coerceAtLeast(0)),
            finished = initial && detail.status != SessionStatus.ACTIVE,
        )
    }

    fun answer(option: Int) {
        val q = _state.value.question ?: return
        if (q.answered || _state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            practice.answer(q.id, option)
            load()
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun showHint() {
        val q = _state.value.question ?: return
        if (q.answered || q.hintUsed) return
        viewModelScope.launch {
            practice.useHint(q.id)
            load()
        }
    }

    fun next() {
        val s = _state.value
        val detail = s.detail ?: return
        if (s.question?.answered != true) return
        viewModelScope.launch {
            if (s.isLast) {
                load()
                _state.value = _state.value.copy(finished = true)
                return@launch
            }
            if (detail.type == SessionType.DAILY && detail.dailyDate != null && practice.todayKey() != detail.dailyDate) {
                // Midnight passed: the answered question stayed in its original set, which is now kept
                // as incomplete; offer the new date's set.
                practice.closeStaleDailies()
                _state.value = _state.value.copy(newDay = true)
                return@launch
            }
            val nextPos = s.position + 1
            practice.setPosition(sessionId, nextPos)
            _state.value = _state.value.copy(position = nextPos)
        }
    }

    fun previous() {
        val s = _state.value
        if (s.position <= 0) return
        viewModelScope.launch {
            practice.setPosition(sessionId, s.position - 1)
            _state.value = _state.value.copy(position = s.position - 1)
        }
    }

    /** Ends early. Returns through [onDone] whether the session was kept (it had answers). */
    fun endSession(onDone: (kept: Boolean) -> Unit) {
        viewModelScope.launch { onDone(practice.endSession(sessionId)) }
    }

    fun openTodaysDaily(onOpened: (Long) -> Unit) {
        viewModelScope.launch {
            practice.closeStaleDailies()
            onOpened(practice.openDaily(prefs.current().dailyRows))
        }
    }
}
