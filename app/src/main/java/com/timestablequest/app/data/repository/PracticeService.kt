package com.timestablequest.app.data.repository

import com.timestablequest.app.data.local.PracticeSessionEntity
import com.timestablequest.app.data.local.QuestionEntity
import com.timestablequest.app.data.local.ReviewAttemptEntity
import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.generation.AnswerChoices
import com.timestablequest.app.domain.generation.FactSelector
import com.timestablequest.app.domain.progress.Clock
import com.timestablequest.app.domain.progress.DateProvider
import com.timestablequest.app.domain.progress.FactStats
import com.timestablequest.app.domain.progress.Scoring
import kotlin.random.Random

/**
 * Session lifecycle, scoring, review and retention rules. All writes that must stay consistent
 * (answer + fact statistics + session status, resets) run inside one store transaction.
 * Clock, date and random sources are injected so tests are deterministic.
 */
class PracticeService(
    private val store: PracticeStore,
    private val random: Random,
    private val clock: Clock,
    private val dates: DateProvider,
) {
    companion object {
        const val RETAIN_NON_DAILY = 100
        const val RETAIN_DAILY = 90
    }

    // ---------------------------------------------------------------- reads

    suspend fun factStats(): Map<Fact, FactStats> =
        store.allProgress().associate { e -> e.toStats().let { it.fact to it } }

    suspend fun session(id: Long): SessionDetail? {
        val s = store.session(id) ?: return null
        return s.toDetail(store.questions(id))
    }

    suspend fun activeNonDaily(): SessionDetail? = store.activeNonDailySession()?.let { session(it.id) }

    suspend fun dailyFor(date: String): SessionDetail? = store.dailySession(date)?.let { session(it.id) }

    suspend fun todayKey(): String = dates.today().toString()

    suspend fun summaries(): List<SessionSummary> = store.sessionSummaries().map { it.toSummary() }

    // ---------------------------------------------------------------- start sessions

    suspend fun startRowCheck(row: Int): StartResult {
        require(row in FACTORS)
        return startNonDaily(SessionType.ROW_CHECK, setOf(row)) { selector, _ -> selector.rowCheck(row, random) }
    }

    suspend fun startMixed(rows: Set<Int>): StartResult {
        val valid = rows.filter { it in FACTORS }.toSet()
        require(valid.isNotEmpty()) { "Select at least one row" }
        return startNonDaily(SessionType.MIXED, valid) { selector, _ -> selector.mixed(valid, random) }
    }

    suspend fun startReview(): StartResult =
        startNonDaily(SessionType.REVIEW, emptySet()) { selector, all -> selector.review(all) }

    private suspend fun startNonDaily(
        type: SessionType,
        rows: Set<Int>,
        pick: (FactSelector, Collection<FactStats>) -> List<Fact>,
    ): StartResult = store.transaction {
        val active = store.activeNonDailySession()
        if (active != null) {
            return@transaction StartResult.Blocked(active.id, SessionType.parse(active.type))
        }
        val stats = factStats()
        val facts = pick(FactSelector.from(stats), stats.values)
        if (facts.isEmpty()) return@transaction StartResult.Empty
        val sessionRows = if (type == SessionType.REVIEW) facts.map { it.row }.toSet() else rows
        StartResult.Started(createSession(type, sessionRows, null, facts, stats))
    }

    /**
     * Returns today's daily set, creating and persisting all ten questions (with option order)
     * before anything is displayed. Reopening on the same date returns the same set. Earlier
     * unfinished daily sets are marked INCOMPLETE; their unanswered questions are not errors.
     */
    suspend fun openDaily(rows: Set<Int>): Long = store.transaction {
        val today = dates.today().toString()
        store.dailySession(today)?.let { return@transaction it.id }
        store.staleActiveDailies(today).forEach { store.updateStatus(it.id, SessionStatus.INCOMPLETE.name, null) }
        val valid = rows.filter { it in FACTORS }.toSet().ifEmpty { FACTORS.toSet() }
        val stats = factStats()
        val facts = FactSelector.from(stats).daily(valid, stats.values, random)
        val id = createSession(SessionType.DAILY, valid, today, facts, stats)
        prune()
        id
    }

    private suspend fun createSession(
        type: SessionType,
        rows: Set<Int>,
        dailyDate: String?,
        facts: List<Fact>,
        stats: Map<Fact, FactStats>,
    ): Long {
        val now = clock.now()
        val id = store.insertSession(
            PracticeSessionEntity(
                type = type.name,
                selectedRows = Facts.encodeRows(rows),
                dailyDate = dailyDate,
                startedAt = now,
                status = SessionStatus.ACTIVE.name,
            ),
        )
        store.insertQuestions(
            facts.mapIndexed { index, fact ->
                QuestionEntity(
                    sessionId = id,
                    position = index,
                    rowFactor = fact.row,
                    columnFactor = fact.column,
                    correctAnswer = fact.product,
                    answerOptions = AnswerChoices.encode(AnswerChoices.generate(fact, random)),
                )
            },
        )
        if (type.scoresOriginal) {
            facts.forEach { f ->
                store.upsertProgress(Scoring.markAsked(stats[f] ?: FactStats.empty(f), now).toEntity())
            }
        }
        return id
    }

    // ---------------------------------------------------------------- answering

    /**
     * Persists the first answer to a question exactly once and updates statistics in the same
     * transaction. Original answers (row, mixed, daily) update accuracy and the confidence window;
     * Needs Practice retries are stored as review attempts only.
     */
    suspend fun answer(questionId: Long, selected: Int): AnswerResult? = store.transaction {
        val q = store.question(questionId) ?: return@transaction null
        val session = store.session(q.sessionId) ?: return@transaction null
        val total = store.questions(session.id).size
        if (q.selectedAnswer != null) {
            return@transaction AnswerResult(q.id, q.isCorrect == true, q.correctAnswer, alreadyAnswered = true,
                sessionFinished = session.status != SessionStatus.ACTIVE.name)
        }
        if (session.status != SessionStatus.ACTIVE.name) return@transaction null

        val now = clock.now()
        val correct = selected == q.correctAnswer
        val updated = store.recordAnswer(q.id, selected, now, dates.today().toString(), correct)
        if (updated == 0) {
            return@transaction AnswerResult(q.id, correct, q.correctAnswer, alreadyAnswered = true, sessionFinished = false)
        }

        val fact = Fact(q.rowFactor, q.columnFactor)
        val prev = store.progress(fact.row, fact.column)?.toStats() ?: FactStats.empty(fact)
        val type = SessionType.parse(session.type)
        val next = if (type.scoresOriginal) {
            Scoring.applyOriginal(prev, session.id, correct, q.hintUsed, now)
        } else {
            store.insertReviewAttempt(ReviewAttemptEntity(rowFactor = fact.row, columnFactor = fact.column, attemptedAt = now, isCorrect = correct))
            Scoring.applyReviewRetry(prev, correct, now)
        }
        store.upsertProgress(next.toEntity())

        val finished = store.answeredCount(session.id) >= total
        if (finished) {
            store.updateStatus(session.id, SessionStatus.COMPLETED.name, now)
            prune()
        }
        AnswerResult(q.id, correct, q.correctAnswer, alreadyAnswered = false, sessionFinished = finished)
    }

    /** Records hint use before answering. Hints never reduce progress. */
    suspend fun useHint(questionId: Long) {
        store.markHintUsed(questionId)
    }

    suspend fun setPosition(sessionId: Long, position: Int) {
        store.updatePosition(sessionId, position.coerceAtLeast(0))
    }

    /**
     * Ends a session early. Answered questions are kept (unanswered ones are excluded from
     * accuracy); a session with no answers is discarded. Returns true if the session was kept.
     */
    suspend fun endSession(sessionId: Long): Boolean = store.transaction {
        val s = store.session(sessionId) ?: return@transaction false
        if (s.status != SessionStatus.ACTIVE.name) return@transaction true
        if (store.answeredCount(sessionId) == 0) {
            store.deleteSessions(listOf(sessionId))
            false
        } else {
            val status = if (s.type == SessionType.DAILY.name) SessionStatus.INCOMPLETE else SessionStatus.ENDED_EARLY
            store.updateStatus(sessionId, status.name, clock.now())
            prune()
            true
        }
    }

    /** Called when the local date changed during a daily set: the earlier set stays incomplete. */
    suspend fun closeStaleDailies() = store.transaction {
        val today = dates.today().toString()
        store.staleActiveDailies(today).forEach { store.updateStatus(it.id, SessionStatus.INCOMPLETE.name, null) }
    }

    // ---------------------------------------------------------------- resets & retention

    /**
     * Clears one row's statistics and review queue and invalidates active sessions containing that
     * row. Retained history stays read-only unless [clearHistory] is true.
     */
    suspend fun resetRow(row: Int, clearHistory: Boolean) = store.transaction {
        require(row in FACTORS)
        store.deleteProgressForRow(row)
        store.deleteReviewAttemptsForRow(row)
        val ids = store.sessionIdsWithRow(row).toSet()
        invalidateActive { it.id in ids }
        if (clearHistory) store.deleteSessions(ids.toList())
    }

    /** Clears all multiplication progress and the review queue; optionally all session history. */
    suspend fun resetAll(clearHistory: Boolean) = store.transaction {
        store.deleteAllProgress()
        store.deleteAllReviewAttempts()
        invalidateActive { true }
        if (clearHistory) store.deleteAllSessions()
    }

    private suspend fun invalidateActive(predicate: (PracticeSessionEntity) -> Boolean) {
        val now = clock.now()
        store.activeSessions().filter(predicate).forEach { s ->
            if (store.answeredCount(s.id) == 0) {
                store.deleteSessions(listOf(s.id))
            } else {
                store.updateStatus(s.id, SessionStatus.ENDED_EARLY.name, now)
            }
        }
    }

    /**
     * Keeps the latest 100 finished non-daily sessions and the latest 90 daily sets. Only
     * session/question records are removed; fact statistics and the review queue are untouched.
     */
    suspend fun prune() {
        store.deleteSessions(store.prunableNonDailyIds(RETAIN_NON_DAILY))
        store.deleteSessions(store.prunableDailyIds(RETAIN_DAILY))
    }
}
