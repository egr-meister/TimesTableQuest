package com.timestablequest.app.data.repository

import com.timestablequest.app.data.local.FactProgressEntity
import com.timestablequest.app.data.local.PracticeSessionEntity
import com.timestablequest.app.data.local.QuestionEntity
import com.timestablequest.app.data.local.ReviewAttemptEntity
import com.timestablequest.app.data.local.SessionSummaryRow

/**
 * Persistence boundary for practice data. Implemented by [RoomPracticeStore] in the app and by an
 * in-memory fake in unit tests, so scoring and session rules are tested without a device.
 */
interface PracticeStore {
    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun session(id: Long): PracticeSessionEntity?
    suspend fun dailySession(date: String): PracticeSessionEntity?
    suspend fun insertSession(session: PracticeSessionEntity): Long
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long?)
    suspend fun updatePosition(id: Long, position: Int)
    suspend fun deleteSessions(ids: List<Long>)
    suspend fun deleteAllSessions()
    suspend fun activeNonDailySession(): PracticeSessionEntity?
    suspend fun activeSessions(): List<PracticeSessionEntity>
    suspend fun staleActiveDailies(today: String): List<PracticeSessionEntity>
    suspend fun prunableNonDailyIds(keep: Int): List<Long>
    suspend fun prunableDailyIds(keep: Int): List<Long>
    suspend fun sessionSummaries(): List<SessionSummaryRow>
    suspend fun sessionIdsWithRow(row: Int): List<Long>

    suspend fun insertQuestions(questions: List<QuestionEntity>)
    suspend fun questions(sessionId: Long): List<QuestionEntity>
    suspend fun question(id: Long): QuestionEntity?
    suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean): Int
    suspend fun markHintUsed(id: Long): Int
    suspend fun answeredCount(sessionId: Long): Int

    suspend fun allProgress(): List<FactProgressEntity>
    suspend fun progress(row: Int, column: Int): FactProgressEntity?
    suspend fun upsertProgress(entity: FactProgressEntity)
    suspend fun deleteProgressForRow(row: Int)
    suspend fun deleteAllProgress()

    suspend fun insertReviewAttempt(attempt: ReviewAttemptEntity): Long
    suspend fun deleteReviewAttemptsForRow(row: Int)
    suspend fun deleteAllReviewAttempts()
}
