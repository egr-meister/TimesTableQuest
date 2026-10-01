package com.timestablequest.app.data.repository

import androidx.room.withTransaction
import com.timestablequest.app.data.local.AppDatabase
import com.timestablequest.app.data.local.FactProgressEntity
import com.timestablequest.app.data.local.PracticeDao
import com.timestablequest.app.data.local.PracticeSessionEntity
import com.timestablequest.app.data.local.QuestionEntity
import com.timestablequest.app.data.local.ReviewAttemptEntity

class RoomPracticeStore(
    private val db: AppDatabase,
    private val dao: PracticeDao = db.practiceDao(),
) : PracticeStore {
    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun session(id: Long) = dao.session(id)
    override suspend fun dailySession(date: String) = dao.dailySession(date)
    override suspend fun insertSession(session: PracticeSessionEntity) = dao.insertSession(session)
    override suspend fun updateStatus(id: Long, status: String, finishedAt: Long?) = dao.updateStatus(id, status, finishedAt)
    override suspend fun updatePosition(id: Long, position: Int) = dao.updatePosition(id, position)
    override suspend fun deleteSessions(ids: List<Long>) {
        // SQLite limits bound variables; delete in chunks.
        ids.chunked(500).forEach { dao.deleteSessions(it) }
    }
    override suspend fun deleteAllSessions() = dao.deleteAllSessions()
    override suspend fun activeNonDailySession() = dao.activeNonDailySession()
    override suspend fun activeSessions() = dao.activeSessions()
    override suspend fun staleActiveDailies(today: String) = dao.staleActiveDailies(today)
    override suspend fun prunableNonDailyIds(keep: Int) = dao.prunableNonDailyIds(keep)
    override suspend fun prunableDailyIds(keep: Int) = dao.prunableDailyIds(keep)
    override suspend fun sessionSummaries() = dao.sessionSummaries()
    override suspend fun sessionIdsWithRow(row: Int) = dao.sessionIdsWithRow(row)

    override suspend fun insertQuestions(questions: List<QuestionEntity>) = dao.insertQuestions(questions)
    override suspend fun questions(sessionId: Long) = dao.questions(sessionId)
    override suspend fun question(id: Long) = dao.question(id)
    override suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean) =
        dao.recordAnswer(id, answer, answeredAt, answeredLocalDate, isCorrect)
    override suspend fun markHintUsed(id: Long) = dao.markHintUsed(id)
    override suspend fun answeredCount(sessionId: Long) = dao.answeredCount(sessionId)

    override suspend fun allProgress() = dao.allProgress()
    override suspend fun progress(row: Int, column: Int) = dao.progress(row, column)
    override suspend fun upsertProgress(entity: FactProgressEntity) = dao.upsertProgress(entity)
    override suspend fun deleteProgressForRow(row: Int) = dao.deleteProgressForRow(row)
    override suspend fun deleteAllProgress() = dao.deleteAllProgress()

    override suspend fun insertReviewAttempt(attempt: ReviewAttemptEntity) = dao.insertReviewAttempt(attempt)
    override suspend fun deleteReviewAttemptsForRow(row: Int) = dao.deleteReviewAttemptsForRow(row)
    override suspend fun deleteAllReviewAttempts() = dao.deleteAllReviewAttempts()
}
