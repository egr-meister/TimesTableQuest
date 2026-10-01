package com.timestablequest.app.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timestablequest.app.AppContainer
import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Study mode: unscored and untimed. Browsing or revealing an answer is recorded only as a study
 * visit and never as a correct scored answer.
 */
class StudyViewModel(container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    companion object {
        const val ARG_ROW = "row"
        const val ARG_COLUMN = "column"
        private const val KEY_HIDE = "hide_answer"
        private const val KEY_ARRAY = "show_array"
    }

    private val prefs = container.preferences
    val row: Int = (handle.get<Int>(ARG_ROW) ?: 1).coerceIn(FACTORS.first, FACTORS.last)

    val column: StateFlow<Int> = handle.getStateFlow(ARG_COLUMN, 1)
    val hideAnswer: StateFlow<Boolean> = handle.getStateFlow(KEY_HIDE, false)
    val showArray: StateFlow<Boolean> = handle.getStateFlow(KEY_ARRAY, true)

    init {
        markVisited()
    }

    private fun current(): Fact = Fact(row, column.value.coerceIn(FACTORS.first, FACTORS.last))

    private fun markVisited() {
        val f = current()
        viewModelScope.launch { prefs.markStudied(f) }
    }

    fun go(delta: Int) {
        val next = (column.value + delta).coerceIn(FACTORS.first, FACTORS.last)
        if (next != column.value) {
            handle[ARG_COLUMN] = next
            markVisited()
        }
    }

    fun setHideAnswer(hide: Boolean) {
        handle[KEY_HIDE] = hide
    }

    fun setShowArray(show: Boolean) {
        handle[KEY_ARRAY] = show
    }
}
