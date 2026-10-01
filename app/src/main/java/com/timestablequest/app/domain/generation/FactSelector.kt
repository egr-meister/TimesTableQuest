package com.timestablequest.app.domain.generation

import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.progress.FactStats
import com.timestablequest.app.domain.review.ReviewQueue
import kotlin.random.Random

/**
 * Chooses which ordered facts go into a session. All randomness comes from the injected [Random],
 * so a seeded generator gives reproducible sessions in tests.
 */
class FactSelector(private val stats: (Fact) -> FactStats) {

    companion object {
        const val SESSION_SIZE = 10
        const val DAILY_REVIEW_MAX = 4

        fun from(map: Map<Fact, FactStats>) = FactSelector { f -> map[f] ?: FactStats.empty(f) }
    }

    /**
     * Ten distinct column factors from one row. Facts that were not asked recently come first
     * (never-asked before least-recently-asked); random tie-breaks keep variety. The two most
     * recently asked facts are therefore the ones left out. Question order is shuffled.
     */
    fun rowCheck(row: Int, random: Random, count: Int = SESSION_SIZE): List<Fact> {
        require(row in FACTORS)
        val keyed = Facts.row(row).map { it to random.nextDouble() }
        return keyed
            .sortedWith(compareBy<Pair<Fact, Double>> { stats(it.first).lastAskedAt ?: Long.MIN_VALUE }.thenBy { it.second })
            .take(count.coerceAtMost(FACTORS.count()))
            .map { it.first }
            .shuffled(random)
    }

    /**
     * Ten questions spread as evenly as possible across [rows], without duplicate ordered facts and
     * avoiding both orientations of a pair (a × b and b × a) whenever an alternative exists.
     * Within a row, low-exposure facts are preferred.
     */
    fun mixed(rows: Set<Int>, random: Random, count: Int = SESSION_SIZE): List<Fact> =
        balanced(rows, random, count, preselected = emptyList()).shuffled(random)

    /**
     * Daily set: up to four unresolved Needs Practice facts from the selected rows, then balanced
     * low-exposure facts across the selected rows. With no review facts this is a balanced set.
     */
    fun daily(rows: Set<Int>, allStats: Collection<FactStats>, random: Random, count: Int = SESSION_SIZE): List<Fact> {
        val review = ReviewQueue.queue(allStats)
            .filter { it.fact.row in rows }
            .take(DAILY_REVIEW_MAX)
            .map { it.fact }
        return balanced(rows, random, count, preselected = review).shuffled(random)
    }

    /** Needs Practice session: up to ten queued facts in queue order (no padding with duplicates). */
    fun review(allStats: Collection<FactStats>): List<Fact> = ReviewQueue.sessionFacts(allStats)

    private fun exposureOrder(random: Random): Comparator<Fact> {
        val tie = HashMap<Fact, Double>()
        return compareBy<Fact> { stats(it).originalAttempts }
            .thenBy { stats(it).timesAsked }
            .thenBy { stats(it).lastAskedAt ?: Long.MIN_VALUE }
            .thenBy { tie.getOrPut(it) { random.nextDouble() } }
    }

    private fun balanced(rows: Set<Int>, random: Random, count: Int, preselected: List<Fact>): List<Fact> {
        val selectedRows = rows.filter { it in FACTORS }.distinct().sorted()
        require(selectedRows.isNotEmpty()) { "At least one row must be selected" }
        val capacity = selectedRows.size * FACTORS.count()
        val target = count.coerceAtMost(capacity)

        val chosen = LinkedHashSet<Fact>()
        preselected.filter { it.row in selectedRows }.forEach { if (chosen.size < target) chosen += it }

        val order = exposureOrder(random)
        val perRow = selectedRows.associateWith { r -> chosen.count { it.row == r } }.toMutableMap()
        val rowTie = selectedRows.associateWith { random.nextDouble() }

        while (chosen.size < target) {
            // Rows with the fewest questions so far; ties broken randomly (fixed per session).
            val available = selectedRows.filter { r -> Facts.row(r).any { it !in chosen } }
            if (available.isEmpty()) break
            val row = available.minWith(compareBy<Int> { perRow.getValue(it) }.thenBy { rowTie.getValue(it) })
            val candidates = Facts.row(row).filter { it !in chosen }
            val clean = candidates.filter { it.reversed !in chosen || it.reversed == it }
            val pick = (clean.ifEmpty { candidates }).minWith(order)
            chosen += pick
            perRow[row] = perRow.getValue(row) + 1
        }
        return chosen.toList()
    }
}
