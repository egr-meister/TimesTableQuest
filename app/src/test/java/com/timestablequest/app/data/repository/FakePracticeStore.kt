package com.timestablequest.app.data.repository

import com.timestablequest.app.data.local.FactProgressEntity
import com.timestablequest.app.data.local.PracticeSessionEntity
import com.timestablequest.app.data.local.QuestionEntity
import com.timestablequest.app.data.local.ReviewAttemptEntity
import com.timestablequest.app.data.local.SessionSummaryRow

/** In-memory [PracticeStore] mirroring the Room DAO queries, including the unique daily-date index. */
class FakePracticeStore : PracticeStore {
    val sessions = linkedMapOf<Long, PracticeSessionEntity>()
    val questions = linkedMapOf<Long, QuestionEntity>()
    val progress = linkedMapOf<Pair<Int, Int>, FactProgressEntity>()
    val reviewAttempts = mutableListOf<ReviewAttemptEntity>()
    private var nextSessionId = 1L
    private var nextQuestionId = 1L
    private var nextAttemptId = 1L
    var transactions = 0

    override suspend fun <T> transaction(block: suspend () -> T): T {
        transactions++
        return block()
    }

    override suspend fun session(id: Long) = sessions[id]
    override suspend fun dailySession(date: String) = sessions.values.firstOrNull { it.dailyDate == date }

    override suspend fun insertSession(session: PracticeSessionEntity): Long {
        if (session.dailyDate != null) {
            check(sessions.values.none { it.dailyDate == session.dailyDate }) { "UNIQUE constraint failed: dailyDate" }
        }
        val id = nextSessionId++
        sessions[id] = session.copy(id = id)
        return id
    }

    override suspend fun updateStatus(id: Long, status: String, finishedAt: Long?) {
        sessions[id]?.let { sessions[id] = it.copy(status = status, finishedAt = finishedAt) }
    }

    override suspend fun updatePosition(id: Long, position: Int) {
        sessions[id]?.let { sessions[id] = it.copy(currentPosition = position) }
    }

    override suspend fun deleteSessions(ids: List<Long>) {
        ids.forEach { id ->
            sessions.remove(id)
            questions.values.removeAll { it.sessionId == id } // ON DELETE CASCADE
        }
    }

    override suspend fun deleteAllSessions() {
        sessions.clear()
        questions.clear()
    }

    override suspend fun activeNonDailySession() =
        sessions.values.filter { it.type != "DAILY" && it.status == "ACTIVE" }.maxByOrNull { it.startedAt }

    override suspend fun activeSessions() = sessions.values.filter { it.status == "ACTIVE" }

    override suspend fun staleActiveDailies(today: String) =
        sessions.values.filter { it.type == "DAILY" && it.status == "ACTIVE" && it.dailyDate!! < today }

    override suspend fun prunableNonDailyIds(keep: Int) =
        sessions.values.filter { it.type != "DAILY" && it.status != "ACTIVE" }
            .sortedWith(compareByDescending<PracticeSessionEntity> { it.startedAt }.thenByDescending { it.id })
            .drop(keep).map { it.id }

    override suspend fun prunableDailyIds(keep: Int) =
        sessions.values.filter { it.type == "DAILY" && it.status != "ACTIVE" }
            .sortedByDescending { it.dailyDate }
            .drop(keep).map { it.id }

    override suspend fun sessionSummaries() = sessions.values
        .sortedWith(compareByDescending<PracticeSessionEntity> { it.startedAt }.thenByDescending { it.id })
        .map { s ->
            val qs = questions.values.filter { it.sessionId == s.id }
            SessionSummaryRow(
                s.id, s.type, s.selectedRows, s.dailyDate, s.startedAt, s.finishedAt, s.status,
                total = qs.size, answered = qs.count { it.selectedAnswer != null }, correct = qs.count { it.isCorrect == true },
            )
        }

    override suspend fun sessionIdsWithRow(row: Int) =
        questions.values.filter { it.rowFactor == row }.map { it.sessionId }.distinct()

    override suspend fun insertQuestions(questions: List<QuestionEntity>) {
        questions.forEach { q ->
            check(this.questions.values.none { it.sessionId == q.sessionId && it.position == q.position })
            val id = nextQuestionId++
            this.questions[id] = q.copy(id = id)
        }
    }

    override suspend fun questions(sessionId: Long) = questions.values.filter { it.sessionId == sessionId }.sortedBy { it.position }
    override suspend fun question(id: Long) = questions[id]

    override suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean): Int {
        val q = questions[id] ?: return 0
        if (q.selectedAnswer != null) return 0
        questions[id] = q.copy(selectedAnswer = answer, answeredAt = answeredAt, answeredLocalDate = answeredLocalDate, isCorrect = isCorrect)
        return 1
    }

    override suspend fun markHintUsed(id: Long): Int {
        val q = questions[id] ?: return 0
        if (q.selectedAnswer != null) return 0
        questions[id] = q.copy(hintUsed = true)
        return 1
    }

    override suspend fun answeredCount(sessionId: Long) = questions.values.count { it.sessionId == sessionId && it.selectedAnswer != null }

    override suspend fun allProgress() = progress.values.toList()
    override suspend fun progress(row: Int, column: Int) = progress[row to column]
    override suspend fun upsertProgress(entity: FactProgressEntity) {
        progress[entity.rowFactor to entity.columnFactor] = entity
    }
    override suspend fun deleteProgressForRow(row: Int) {
        progress.keys.removeAll { it.first == row }
    }
    override suspend fun deleteAllProgress() = progress.clear()

    override suspend fun insertReviewAttempt(attempt: ReviewAttemptEntity): Long {
        val id = nextAttemptId++
        reviewAttempts += attempt.copy(id = id)
        return id
    }
    override suspend fun deleteReviewAttemptsForRow(row: Int) {
        reviewAttempts.removeAll { it.rowFactor == row }
    }
    override suspend fun deleteAllReviewAttempts() = reviewAttempts.clear()
}
