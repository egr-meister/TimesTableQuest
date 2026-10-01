package com.timestablequest.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.data.repository.SessionDetail
import com.timestablequest.app.data.repository.SessionStatus
import com.timestablequest.app.domain.progress.RowProgress
import com.timestablequest.app.domain.progress.RowStats
import com.timestablequest.app.domain.review.ReviewQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DailyStripState(
    val date: String = "",
    /** Null until today's set has been generated. */
    val answered: Int? = null,
    val total: Int = 10,
    val correct: Int = 0,
    val status: SessionStatus? = null,
    val rowsLabel: String = "All rows",
)

data class MapState(
    val loaded: Boolean = false,
    val rows: List<RowStats> = emptyList(),
    val daily: DailyStripState = DailyStripState(),
    val needsPractice: Int = 0,
    val active: SessionDetail? = null,
)

class MapViewModel(private val container: AppContainer) : ViewModel() {
    private val practice = container.practice
    private val prefs = container.preferences
    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(practice.changes, prefs.settings) { _, s -> s }.collect { refresh() }
        }
    }

    /** Also called on resume so a date change (midnight) is reflected in the Daily 10 strip. */
    fun refresh() {
        viewModelScope.launch {
            val settings = prefs.current()
            val stats = practice.factStats()
            val today = practice.todayKey()
            val daily = practice.dailyFor(today)
            _state.value = MapState(
                loaded = true,
                rows = RowProgress.all(stats),
                daily = DailyStripState(
                    date = today,
                    answered = daily?.answered,
                    total = daily?.questions?.size ?: 10,
                    correct = daily?.correct ?: 0,
                    status = daily?.status,
                    rowsLabel = daily?.rowsLabel ?: com.timestablequest.app.domain.facts.Facts.rowsLabel(settings.dailyRows),
                ),
                needsPractice = ReviewQueue.queue(stats.values).size,
                active = practice.activeNonDaily(),
            )
        }
    }

    /** Opens (creating and persisting if needed) today's daily set. */
    fun openDaily(onOpened: (Long) -> Unit) {
        viewModelScope.launch {
            practice.closeStaleDailies()
            val id = practice.openDaily(prefs.current().dailyRows)
            onOpened(id)
        }
    }
}
