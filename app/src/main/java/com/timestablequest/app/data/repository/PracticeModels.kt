package com.timestablequest.app.data.repository

import com.timestablequest.app.data.local.FactProgressEntity
import com.timestablequest.app.data.local.PracticeSessionEntity
import com.timestablequest.app.data.local.QuestionEntity
import com.timestablequest.app.data.local.SessionSummaryRow
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.generation.AnswerChoices
import com.timestablequest.app.domain.progress.FactStats
import com.timestablequest.app.domain.progress.OutcomeWindow
import com.timestablequest.app.domain.progress.Percent

enum class SessionType(val label: String) {
    ROW_CHECK("Row check"),
    MIXED("Mixed practice"),
    REVIEW("Needs Practice"),
    DAILY("Daily 10");

    /** Review retries never count as original answers. */
    val scoresOriginal: Boolean get() = this != REVIEW

    companion object {
        fun parse(name: String): SessionType = entries.firstOrNull { it.name == name } ?: MIXED
    }
}

enum class SessionStatus(val label: String) {
    ACTIVE("In progress"),
    COMPLETED("Completed"),
    ENDED_EARLY("Ended early"),

    /** A daily set from an earlier date that was not finished. Unanswered questions are not errors. */
    INCOMPLETE("Incomplete");

    companion object {
        fun parse(name: String): SessionStatus = entries.firstOrNull { it.name == name } ?: COMPLETED
    }
}

sealed interface StartResult {
    data class Started(val sessionId: Long) : StartResult

    /** Another non-daily session is unfinished; the user must resume or end it first. */
    data class Blocked(val activeSessionId: Long, val activeType: SessionType) : StartResult

    /** Nothing to practice (e.g. the Needs Practice queue is empty). */
    data object Empty : StartResult
}

data class AnswerResult(
    val questionId: Long,
    val correct: Boolean,
    val correctAnswer: Int,
    /** True when the question had already been answered; nothing was scored again. */
    val alreadyAnswered: Boolean,
    val sessionFinished: Boolean,
)

data class QuestionView(
    val id: Long,
    val position: Int,
    val fact: Fact,
    val options: List<Int>,
    val correctAnswer: Int,
    val selectedAnswer: Int?,
    val isCorrect: Boolean?,
    val hintUsed: Boolean,
    val answeredAt: Long?,
    val answeredLocalDate: String?,
) {
    val answered: Boolean get() = selectedAnswer != null
}

data class SessionDetail(
    val id: Long,
    val type: SessionType,
    val rows: Set<Int>,
    val dailyDate: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: SessionStatus,
    val currentPosition: Int,
    val questions: List<QuestionView>,
) {
    val answered: Int get() = questions.count { it.answered }
    val correct: Int get() = questions.count { it.isCorrect == true }
    val incorrect: Int get() = questions.count { it.isCorrect == false }

    /** Accuracy over answered questions only; unanswered questions are excluded. */
    val accuracyPercent: Int? get() = Percent.of(correct, answered)
    val allAnswered: Boolean get() = questions.isNotEmpty() && questions.all { it.answered }
    val rowsLabel: String get() = Facts.rowsLabel(rows)
}

data class SessionSummary(
    val id: Long,
    val type: SessionType,
    val rows: Set<Int>,
    val dailyDate: String?,
    val startedAt: Long,
    val status: SessionStatus,
    val total: Int,
    val answered: Int,
    val correct: Int,
) {
    val accuracyPercent: Int? get() = Percent.of(correct, answered)
}

// ---- mappers ----

fun FactProgressEntity.toStats(): FactStats = FactStats(
    fact = Fact(rowFactor, columnFactor),
    originalAttempts = originalAttempts,
    originalCorrect = originalCorrect,
    latestCorrect = latestCorrect,
    window = OutcomeWindow.decode(latestOutcomeWindow),
    lastPracticedAt = lastPracticedAt,
    lastIncorrectAt = lastIncorrectAt,
    lastReviewedAt = lastReviewedAt,
    reviewNeeded = reviewNeeded,
    timesAsked = timesAsked,
    lastAskedAt = lastAskedAt,
)

fun FactStats.toEntity(): FactProgressEntity = FactProgressEntity(
    rowFactor = fact.row,
    columnFactor = fact.column,
    originalAttempts = originalAttempts,
    originalCorrect = originalCorrect,
    latestCorrect = latestCorrect,
    latestOutcomeWindow = OutcomeWindow.encode(window),
    lastPracticedAt = lastPracticedAt,
    lastIncorrectAt = lastIncorrectAt,
    lastReviewedAt = lastReviewedAt,
    reviewNeeded = reviewNeeded,
    timesAsked = timesAsked,
    lastAskedAt = lastAskedAt,
)

fun QuestionEntity.toView(): QuestionView = QuestionView(
    id = id,
    position = position,
    fact = Fact(rowFactor, columnFactor),
    options = AnswerChoices.decode(answerOptions),
    correctAnswer = correctAnswer,
    selectedAnswer = selectedAnswer,
    isCorrect = isCorrect,
    hintUsed = hintUsed,
    answeredAt = answeredAt,
    answeredLocalDate = answeredLocalDate,
)

fun PracticeSessionEntity.toDetail(questions: List<QuestionEntity>): SessionDetail = SessionDetail(
    id = id,
    type = SessionType.parse(type),
    rows = Facts.decodeRows(selectedRows),
    dailyDate = dailyDate,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = SessionStatus.parse(status),
    currentPosition = currentPosition.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
    questions = questions.sortedBy { it.position }.map { it.toView() },
)

fun SessionSummaryRow.toSummary(): SessionSummary = SessionSummary(
    id = id,
    type = SessionType.parse(type),
    rows = Facts.decodeRows(selectedRows),
    dailyDate = dailyDate,
    startedAt = startedAt,
    status = SessionStatus.parse(status),
    total = total,
    answered = answered,
    correct = correct,
)
