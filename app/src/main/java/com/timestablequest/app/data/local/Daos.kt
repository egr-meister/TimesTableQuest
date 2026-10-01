package com.timestablequest.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationDao {
    @Query("SELECT * FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun latest(limit: Int): Flow<List<CalculationEntity>>

    @Insert
    suspend fun insert(entity: CalculationEntity): Long

    /** Keeps only the newest [keep] calculations. */
    @Query(
        "DELETE FROM calculations WHERE id NOT IN " +
            "(SELECT id FROM calculations ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)

    @Query("DELETE FROM calculations")
    suspend fun clear()
}

@Dao
interface PracticeDao {
    // ---- sessions ----
    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun session(id: Long): PracticeSessionEntity?

    @Query("SELECT * FROM practice_sessions WHERE dailyDate = :date LIMIT 1")
    suspend fun dailySession(date: String): PracticeSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: PracticeSessionEntity): Long

    @Query("UPDATE practice_sessions SET status = :status, finishedAt = :finishedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long?)

    @Query("UPDATE practice_sessions SET currentPosition = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM practice_sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<Long>)

    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM practice_sessions WHERE type != 'DAILY' AND status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeNonDailySession(): PracticeSessionEntity?

    @Query("SELECT * FROM practice_sessions WHERE status = 'ACTIVE'")
    suspend fun activeSessions(): List<PracticeSessionEntity>

    @Query("SELECT * FROM practice_sessions WHERE type = 'DAILY' AND status = 'ACTIVE' AND dailyDate < :today")
    suspend fun staleActiveDailies(today: String): List<PracticeSessionEntity>

    /** Finished non-daily sessions beyond the newest [keep]. */
    @Query(
        "SELECT id FROM practice_sessions WHERE type != 'DAILY' AND status != 'ACTIVE' " +
            "ORDER BY startedAt DESC, id DESC LIMIT -1 OFFSET :keep",
    )
    suspend fun prunableNonDailyIds(keep: Int): List<Long>

    /** Daily sets beyond the newest [keep] dates (an active set for today is never among them). */
    @Query(
        "SELECT id FROM practice_sessions WHERE type = 'DAILY' AND status != 'ACTIVE' " +
            "ORDER BY dailyDate DESC LIMIT -1 OFFSET :keep",
    )
    suspend fun prunableDailyIds(keep: Int): List<Long>

    @Query(
        "SELECT s.id, s.type, s.selectedRows, s.dailyDate, s.startedAt, s.finishedAt, s.status, " +
            "COUNT(q.id) AS total, " +
            "SUM(CASE WHEN q.selectedAnswer IS NOT NULL THEN 1 ELSE 0 END) AS answered, " +
            "SUM(CASE WHEN q.isCorrect = 1 THEN 1 ELSE 0 END) AS correct " +
            "FROM practice_sessions s LEFT JOIN questions q ON q.sessionId = s.id " +
            "GROUP BY s.id ORDER BY s.startedAt DESC, s.id DESC",
    )
    suspend fun sessionSummaries(): List<SessionSummaryRow>

    @Query("SELECT DISTINCT sessionId FROM questions WHERE rowFactor = :row")
    suspend fun sessionIdsWithRow(row: Int): List<Long>

    // ---- questions ----
    @Insert
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("SELECT * FROM questions WHERE sessionId = :sessionId ORDER BY position")
    suspend fun questions(sessionId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun question(id: Long): QuestionEntity?

    /** Persists the first answer only; returns 0 when the question was already answered. */
    @Query(
        "UPDATE questions SET selectedAnswer = :answer, answeredAt = :answeredAt, " +
            "answeredLocalDate = :answeredLocalDate, isCorrect = :isCorrect " +
            "WHERE id = :id AND selectedAnswer IS NULL",
    )
    suspend fun recordAnswer(id: Long, answer: Int, answeredAt: Long, answeredLocalDate: String, isCorrect: Boolean): Int

    @Query("UPDATE questions SET hintUsed = 1 WHERE id = :id AND selectedAnswer IS NULL")
    suspend fun markHintUsed(id: Long): Int

    @Query("SELECT COUNT(*) FROM questions WHERE sessionId = :sessionId AND selectedAnswer IS NOT NULL")
    suspend fun answeredCount(sessionId: Long): Int

    // ---- fact progress ----
    @Query("SELECT * FROM fact_progress")
    suspend fun allProgress(): List<FactProgressEntity>

    @Query("SELECT * FROM fact_progress WHERE rowFactor = :row AND columnFactor = :column")
    suspend fun progress(row: Int, column: Int): FactProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: FactProgressEntity)

    @Query("DELETE FROM fact_progress WHERE rowFactor = :row")
    suspend fun deleteProgressForRow(row: Int)

    @Query("DELETE FROM fact_progress")
    suspend fun deleteAllProgress()

    // ---- review attempts ----
    @Insert
    suspend fun insertReviewAttempt(attempt: ReviewAttemptEntity): Long

    @Query("DELETE FROM review_attempts WHERE rowFactor = :row")
    suspend fun deleteReviewAttemptsForRow(row: Int)

    @Query("DELETE FROM review_attempts")
    suspend fun deleteAllReviewAttempts()

    @Query("SELECT COUNT(*) FROM review_attempts")
    suspend fun reviewAttemptCount(): Int
}
