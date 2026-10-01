package com.timestablequest.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One successful calculator calculation. Decimal values are canonical strings (BigDecimal plain form). */
@Entity(tableName = "calculations", indices = [Index("createdAt")])
data class CalculationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leftOperand: String,
    /** CalcOperator name: ADD, SUBTRACT, MULTIPLY, DIVIDE. */
    val operator: String,
    val rightOperand: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
)

/**
 * A practice session. Types: ROW_CHECK, MIXED, REVIEW, DAILY.
 * Statuses: ACTIVE, COMPLETED, ENDED_EARLY, INCOMPLETE (an earlier day's unfinished daily set).
 * A non-null [dailyDate] (ISO local date) is unique: one daily set per calendar date.
 */
@Entity(
    tableName = "practice_sessions",
    indices = [Index(value = ["dailyDate"], unique = true), Index("status"), Index("startedAt")],
)
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    /** Selected rows, e.g. "3,4,9". */
    val selectedRows: String,
    val dailyDate: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String,
    /** Index of the visible question, persisted for recovery. */
    @ColumnInfo(defaultValue = "0") val currentPosition: Int = 0,
)

/** An immutable question record. Its first answer is persisted exactly once. */
@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId", "position"], unique = true), Index(value = ["rowFactor", "columnFactor"])],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int,
    val rowFactor: Int,
    val columnFactor: Int,
    val correctAnswer: Int,
    /** Option order as shown, e.g. "28,24,35,32". */
    val answerOptions: String,
    val selectedAnswer: Int? = null,
    val answeredAt: Long? = null,
    /** Local date at answer time; fixed so later time-zone changes never rewrite daily totals. */
    val answeredLocalDate: String? = null,
    val isCorrect: Boolean? = null,
    @ColumnInfo(defaultValue = "0") val hintUsed: Boolean = false,
)

/** Lifetime statistics of one ordered fact. Never pruned with session history. */
@Entity(tableName = "fact_progress", primaryKeys = ["rowFactor", "columnFactor"])
data class FactProgressEntity(
    val rowFactor: Int,
    val columnFactor: Int,
    val originalAttempts: Int = 0,
    val originalCorrect: Int = 0,
    val latestCorrect: Boolean? = null,
    /** Latest three original outcomes, "sessionId:C|H|X" comma-separated, oldest first. */
    val latestOutcomeWindow: String = "",
    val lastPracticedAt: Long? = null,
    val lastIncorrectAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val reviewNeeded: Boolean = false,
    val timesAsked: Int = 0,
    val lastAskedAt: Long? = null,
)

/** One Needs Practice retry. Kept separate so retries never change original accuracy. */
@Entity(tableName = "review_attempts", indices = [Index(value = ["rowFactor", "columnFactor"]), Index("attemptedAt")])
data class ReviewAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rowFactor: Int,
    val columnFactor: Int,
    val attemptedAt: Long,
    val isCorrect: Boolean,
)

/** Summary row used by the history list. */
data class SessionSummaryRow(
    val id: Long,
    val type: String,
    val selectedRows: String,
    val dailyDate: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val total: Int,
    val answered: Int,
    val correct: Int,
)
