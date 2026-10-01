package com.timestablequest.app.domain.progress

import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import java.time.LocalDate
import kotlin.math.roundToInt

/** Injected wall clock (epoch milliseconds) so scoring and generation are deterministic in tests. */
fun interface Clock {
    fun now(): Long
}

/** Injected local calendar date provider (device time zone). */
fun interface DateProvider {
    fun today(): LocalDate
}

/** Result of one scored original answer. */
enum class Outcome(val code: Char) {
    /** Correct on the first answer, no hint shown. */
    CORRECT('C'),

    /** Correct on the first answer, but a hint was shown first. */
    CORRECT_WITH_HINT('H'),

    INCORRECT('X');

    val isCorrect: Boolean get() = this != INCORRECT

    companion object {
        fun of(correct: Boolean, hintUsed: Boolean): Outcome = when {
            !correct -> INCORRECT
            hintUsed -> CORRECT_WITH_HINT
            else -> CORRECT
        }

        fun fromCode(c: Char): Outcome? = entries.firstOrNull { it.code == c }
    }
}

data class OutcomeEntry(val sessionId: Long, val outcome: Outcome)

/** The latest three original outcomes of a fact, stored as "12:C,12:H,15:X" (oldest first). */
object OutcomeWindow {
    const val SIZE = 3

    fun encode(entries: List<OutcomeEntry>): String =
        entries.takeLast(SIZE).joinToString(",") { "${it.sessionId}:${it.outcome.code}" }

    fun decode(text: String?): List<OutcomeEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(',').mapNotNull { part ->
            val idx = part.lastIndexOf(':')
            if (idx <= 0 || idx != part.length - 2) return@mapNotNull null
            val id = part.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
            val outcome = Outcome.fromCode(part.last()) ?: return@mapNotNull null
            OutcomeEntry(id, outcome)
        }.takeLast(SIZE)
    }

    fun push(entries: List<OutcomeEntry>, entry: OutcomeEntry): List<OutcomeEntry> = (entries + entry).takeLast(SIZE)
}

enum class FactStatus(val label: String, val symbol: String) {
    NOT_PRACTICED("Not practiced", "○"),
    PRACTICING("Practicing", "◐"),
    CONFIDENT("Confident", "★"),
}

/**
 * Confidence rule: the latest three original answers are correct without hints and come from at
 * least two distinct sessions. "Confident" is an application status, not a claim of permanent mastery.
 * Study browsing and review retries never enter the window.
 */
object ConfidenceRules {
    fun isConfident(window: List<OutcomeEntry>): Boolean =
        window.size == OutcomeWindow.SIZE &&
            window.all { it.outcome == Outcome.CORRECT } &&
            window.map { it.sessionId }.distinct().size >= 2

    fun status(originalAttempts: Int, window: List<OutcomeEntry>): FactStatus = when {
        originalAttempts <= 0 -> FactStatus.NOT_PRACTICED
        isConfident(window) -> FactStatus.CONFIDENT
        else -> FactStatus.PRACTICING
    }
}

/** Lifetime statistics of one ordered fact. Independent of how much session history is retained. */
data class FactStats(
    val fact: Fact,
    val originalAttempts: Int = 0,
    val originalCorrect: Int = 0,
    /** Correctness of the latest original answer; null when never answered. */
    val latestCorrect: Boolean? = null,
    val window: List<OutcomeEntry> = emptyList(),
    val lastPracticedAt: Long? = null,
    val lastIncorrectAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val reviewNeeded: Boolean = false,
    /** Number of times the fact was placed into a scored (non-review) session. */
    val timesAsked: Int = 0,
    val lastAskedAt: Long? = null,
) {
    val status: FactStatus get() = ConfidenceRules.status(originalAttempts, window)
    val correctAtLeastOnce: Boolean get() = originalCorrect > 0

    companion object {
        fun empty(fact: Fact) = FactStats(fact)
    }
}

/** Pure state transitions for fact statistics. Persisted inside one database transaction. */
object Scoring {
    /**
     * Applies the first (original) answer to a row-check, mixed or daily question.
     * Correct-with-hint counts as correct for accuracy but is not an independent demonstration.
     * An incorrect answer (re)opens the fact in the Needs Practice queue.
     */
    fun applyOriginal(prev: FactStats, sessionId: Long, correct: Boolean, hintUsed: Boolean, now: Long): FactStats {
        val outcome = Outcome.of(correct, hintUsed)
        return prev.copy(
            originalAttempts = prev.originalAttempts + 1,
            originalCorrect = prev.originalCorrect + if (correct) 1 else 0,
            latestCorrect = correct,
            window = OutcomeWindow.push(prev.window, OutcomeEntry(sessionId, outcome)),
            lastPracticedAt = now,
            lastIncorrectAt = if (correct) prev.lastIncorrectAt else now,
            reviewNeeded = if (correct) prev.reviewNeeded else true,
        )
    }

    /**
     * Applies a Needs Practice retry. Never changes original accuracy or the confidence window.
     * A correct retry resolves the fact; an incorrect retry keeps it queued.
     */
    fun applyReviewRetry(prev: FactStats, correct: Boolean, now: Long): FactStats = prev.copy(
        lastReviewedAt = now,
        reviewNeeded = if (correct) false else prev.reviewNeeded,
    )

    /** Records that a fact was placed into a scored session (used for low-exposure selection). */
    fun markAsked(prev: FactStats, now: Long): FactStats = prev.copy(timesAsked = prev.timesAsked + 1, lastAskedAt = now)
}

/** Row-level metrics. */
data class RowStats(
    val row: Int,
    /** Facts answered correctly at least once (original answers), out of 12. */
    val correctAtLeastOnce: Int,
    /** Facts with Confident status, out of 12. */
    val confident: Int,
    val originalAttempts: Int,
    val originalCorrect: Int,
    /** Facts currently in the Needs Practice queue. */
    val needsPractice: Int,
) {
    /** correct original answers / all original answers × 100, rounded; null when not practiced yet. */
    val accuracyPercent: Int? get() = Percent.of(originalCorrect, originalAttempts)
    val practiced: Boolean get() = originalAttempts > 0

    companion object {
        const val FACTS_PER_ROW = 12
    }
}

object Percent {
    fun of(part: Int, whole: Int): Int? = if (whole <= 0) null else (part * 100.0 / whole).roundToInt()
}

object RowProgress {
    fun compute(row: Int, stats: (Fact) -> FactStats): RowStats {
        val facts = Facts.row(row).map(stats)
        return RowStats(
            row = row,
            correctAtLeastOnce = facts.count { it.correctAtLeastOnce },
            confident = facts.count { it.status == FactStatus.CONFIDENT },
            originalAttempts = facts.sumOf { it.originalAttempts },
            originalCorrect = facts.sumOf { it.originalCorrect },
            needsPractice = facts.count { it.reviewNeeded },
        )
    }

    fun all(stats: Map<Fact, FactStats>): List<RowStats> =
        FACTORS.map { r -> compute(r) { f -> stats[f] ?: FactStats.empty(f) } }
}
