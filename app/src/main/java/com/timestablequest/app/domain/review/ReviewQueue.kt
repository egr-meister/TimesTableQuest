package com.timestablequest.app.domain.review

import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.progress.FactStats

/**
 * Needs Practice queue.
 *
 * A fact enters the queue after an incorrect original answer and leaves it after a correct review
 * retry. Ordering:
 *  1. unresolved incorrect facts — queued facts whose latest original answer is still incorrect;
 *  2. more recent incorrect answers first;
 *  3. least recently reviewed first (never reviewed before reviewed).
 * Ties fall back to row/column order so the queue is deterministic.
 */
object ReviewQueue {
    const val MAX_SESSION_SIZE = 10

    val ORDER: Comparator<FactStats> =
        compareBy<FactStats> { if (it.latestCorrect == false) 0 else 1 }
            .thenByDescending { it.lastIncorrectAt ?: Long.MIN_VALUE }
            .thenBy { it.lastReviewedAt ?: Long.MIN_VALUE }
            .thenBy { it.fact }

    fun queue(stats: Collection<FactStats>): List<FactStats> = stats.filter { it.reviewNeeded }.sortedWith(ORDER)

    /** Up to ten queued facts. A shorter session is created when fewer exist; no duplicates. */
    fun sessionFacts(stats: Collection<FactStats>, max: Int = MAX_SESSION_SIZE): List<Fact> =
        queue(stats).take(max).map { it.fact }
}
