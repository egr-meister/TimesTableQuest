package com.timestablequest.app.data.repository

import com.timestablequest.app.data.local.AppDatabase
import com.timestablequest.app.data.local.CalculationDao
import com.timestablequest.app.data.local.CalculationEntity
import com.timestablequest.app.data.local.PreferencesStore
import com.timestablequest.app.domain.calculator.CalcOperator
import com.timestablequest.app.domain.calculator.CalculationRecord
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.progress.Clock
import com.timestablequest.app.domain.progress.FactStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Main-safe facade over [PracticeService]. Every call runs on the IO dispatcher; [changes] ticks
 * after each write so screens reload their state.
 */
class PracticeRepository(
    private val service: PracticeService,
    private val io: CoroutineDispatcher,
) {
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun bump() {
        _changes.value = _changes.value + 1
    }

    private suspend fun <T> read(block: suspend PracticeService.() -> T): T = withContext(io) { service.block() }
    private suspend fun <T> write(block: suspend PracticeService.() -> T): T =
        withContext(io) { service.block() }.also { bump() }

    suspend fun factStats(): Map<Fact, FactStats> = read { factStats() }
    suspend fun session(id: Long): SessionDetail? = read { session(id) }
    suspend fun activeNonDaily(): SessionDetail? = read { activeNonDaily() }
    suspend fun dailyFor(date: String): SessionDetail? = read { dailyFor(date) }
    suspend fun todayKey(): String = read { todayKey() }
    suspend fun summaries(): List<SessionSummary> = read { summaries() }

    suspend fun startRowCheck(row: Int): StartResult = write { startRowCheck(row) }
    suspend fun startMixed(rows: Set<Int>): StartResult = write { startMixed(rows) }
    suspend fun startReview(): StartResult = write { startReview() }
    suspend fun openDaily(rows: Set<Int>): Long = write { openDaily(rows) }
    suspend fun answer(questionId: Long, selected: Int): AnswerResult? = write { answer(questionId, selected) }
    suspend fun useHint(questionId: Long) = write { useHint(questionId) }
    suspend fun setPosition(sessionId: Long, position: Int) = withContext(io) { service.setPosition(sessionId, position) }
    suspend fun endSession(sessionId: Long): Boolean = write { endSession(sessionId) }
    suspend fun closeStaleDailies() = write { closeStaleDailies() }
    suspend fun resetRow(row: Int, clearHistory: Boolean) = write { resetRow(row, clearHistory) }
    suspend fun resetAll(clearHistory: Boolean) = write { resetAll(clearHistory) }

    /** Signals external changes (e.g. clearing all data). */
    fun notifyChanged() = bump()
}

data class CalculationItem(val id: Long, val record: CalculationRecord, val createdAt: Long)

class CalculatorRepository(
    private val dao: CalculationDao,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    companion object {
        const val HISTORY_LIMIT = 50
    }

    val history: Flow<List<CalculationItem>> = dao.latest(HISTORY_LIMIT).map { list ->
        list.mapNotNull { e ->
            val op = CalcOperator.entries.firstOrNull { it.name == e.operator } ?: return@mapNotNull null
            CalculationItem(e.id, CalculationRecord(e.leftOperand, op, e.rightOperand, e.result, e.rounded), e.createdAt)
        }
    }

    /** Stores a successful calculation and keeps only the latest 50. Errors are never stored. */
    suspend fun add(record: CalculationRecord) = withContext(io) {
        dao.insert(
            CalculationEntity(
                leftOperand = record.left,
                operator = record.operator.name,
                rightOperand = record.right,
                result = record.result,
                rounded = record.rounded,
                createdAt = clock.now(),
            ),
        )
        dao.prune(HISTORY_LIMIT)
    }

    suspend fun clear() = withContext(io) { dao.clear() }
}

/** Grown-up data actions from Settings. */
class DataResetter(
    private val database: AppDatabase,
    private val preferences: PreferencesStore,
    private val practice: PracticeRepository,
    private val calculator: CalculatorRepository,
    private val io: CoroutineDispatcher,
) {
    suspend fun resetRow(row: Int, clearHistory: Boolean) {
        practice.resetRow(row, clearHistory)
        preferences.clearStudyVisitsForRow(row)
    }

    suspend fun resetAllProgress(clearHistory: Boolean) {
        practice.resetAll(clearHistory)
        preferences.clearStudyVisits()
    }

    suspend fun clearCalculatorHistory() {
        calculator.clear()
        preferences.clearCalculatorDraft()
    }

    /** Deletes every table and every preference. */
    suspend fun clearAllLocalData() {
        withContext(io) { database.clearAllTables() }
        preferences.clearAll()
        practice.notifyChanged()
    }
}
