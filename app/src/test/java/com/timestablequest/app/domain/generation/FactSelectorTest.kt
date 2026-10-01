package com.timestablequest.app.domain.generation

import com.timestablequest.app.domain.facts.FACTORS
import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.progress.FactStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FactSelectorTest {

    private fun hasBothOrientations(facts: List<Fact>): Boolean =
        facts.any { it.row != it.column && it.reversed in facts }

    @Test
    fun rowCheckHasTenDistinctColumnsFromTheRow() {
        val selector = FactSelector.from(emptyMap())
        for (row in FACTORS) for (seed in 0 until 20) {
            val facts = selector.rowCheck(row, Random(seed))
            assertEquals(10, facts.size)
            assertEquals(10, facts.toSet().size)
            assertTrue(facts.all { it.row == row })
        }
    }

    @Test
    fun rowCheckPrioritisesFactsNotRecentlyAsked() {
        // Columns 11 and 12 were asked most recently; everything else earlier or never.
        val stats = Facts.row(5).associateWith { f ->
            when (f.column) {
                12 -> FactStats(f, lastAskedAt = 5_000)
                11 -> FactStats(f, lastAskedAt = 4_000)
                1 -> FactStats(f, lastAskedAt = 1_000)
                else -> FactStats(f)
            }
        }
        val selector = FactSelector.from(stats)
        for (seed in 0 until 30) {
            val cols = selector.rowCheck(5, Random(seed)).map { it.column }.toSet()
            assertFalse(11 in cols)
            assertFalse(12 in cols)
            assertTrue(1 in cols)
        }
    }

    @Test
    fun rowCheckIsDeterministicForASeedAndVariesAcrossSeeds() {
        val selector = FactSelector.from(emptyMap())
        assertEquals(selector.rowCheck(7, Random(3)), selector.rowCheck(7, Random(3)))
        val variants = (0 until 20).map { selector.rowCheck(7, Random(it)).toSet() }.toSet()
        assertTrue(variants.size > 1)
    }

    @Test
    fun mixedIsBalancedWithoutDuplicates() {
        val selector = FactSelector.from(emptyMap())
        val cases = listOf(setOf(3), setOf(3, 4), setOf(2, 5, 9), setOf(1, 2, 3, 4), (1..12).toSet(), setOf(6, 7, 8, 9, 10, 11, 12))
        for (rows in cases) for (seed in 0 until 25) {
            val facts = selector.mixed(rows, Random(seed))
            assertEquals(10, facts.size)
            assertEquals(10, facts.toSet().size)
            assertTrue(facts.all { it.row in rows })
            val counts = rows.map { r -> facts.count { it.row == r } }
            assertTrue("$rows -> $counts", counts.max() - counts.min() <= 1)
        }
    }

    @Test
    fun mixedAvoidsBothOrientationsWhenAlternativesExist() {
        val selector = FactSelector.from(emptyMap())
        for (seed in 0 until 50) {
            assertFalse(hasBothOrientations(selector.mixed((1..12).toSet(), Random(seed))))
            assertFalse(hasBothOrientations(selector.mixed(setOf(3, 4), Random(seed))))
            assertFalse(hasBothOrientations(selector.mixed(setOf(2, 3, 4, 5, 6), Random(seed))))
        }
    }

    @Test
    fun allRowsMixedUsesTenDifferentRows() {
        val facts = FactSelector.from(emptyMap()).mixed((1..12).toSet(), Random(11))
        assertEquals(10, facts.map { it.row }.toSet().size)
    }

    @Test
    fun mixedPrefersLowExposureFacts() {
        val stats = Facts.row(4).associateWith { f -> if (f.column <= 6) FactStats(f, originalAttempts = 9, timesAsked = 9) else FactStats(f) }
        val facts = FactSelector.from(stats).mixed(setOf(4), Random(1))
        // Six fresh facts exist; all must be used before any high-exposure fact.
        assertTrue(facts.map { it.column }.containsAll(listOf(7, 8, 9, 10, 11, 12)))
    }

    @Test
    fun dailyIncludesAtMostFourReviewFactsFromSelectedRows() {
        val review = listOf(Fact(3, 4), Fact(3, 8), Fact(5, 6), Fact(5, 7), Fact(6, 9), Fact(9, 9))
        val stats = review.mapIndexed { i, f ->
            f to FactStats(f, originalAttempts = 1, latestCorrect = false, reviewNeeded = true, lastIncorrectAt = 1_000L + i)
        }.toMap()
        val selector = FactSelector.from(stats)
        for (seed in 0 until 20) {
            val daily = selector.daily((1..12).toSet(), stats.values, Random(seed))
            assertEquals(10, daily.size)
            assertEquals(10, daily.toSet().size)
            val included = daily.filter { it in review }
            assertEquals(4, included.size)
            // Most recent incorrect answers come first.
            assertEquals(setOf(Fact(9, 9), Fact(6, 9), Fact(5, 7), Fact(5, 6)), included.toSet())
        }
        val restricted = selector.daily(setOf(3, 4), stats.values, Random(1))
        assertTrue(restricted.all { it.row in setOf(3, 4) })
        assertTrue(restricted.containsAll(listOf(Fact(3, 4), Fact(3, 8))))
    }

    @Test
    fun dailyWithoutReviewFactsIsBalanced() {
        val daily = FactSelector.from(emptyMap()).daily(setOf(2, 7), emptyList(), Random(5))
        assertEquals(5, daily.count { it.row == 2 })
        assertEquals(5, daily.count { it.row == 7 })
    }

    @Test
    fun reviewSessionIsShorterWhenFewFactsQueuedAndNeverDuplicated() {
        val stats = listOf(Fact(2, 3), Fact(4, 4)).map { FactStats(it, reviewNeeded = true, latestCorrect = false, lastIncorrectAt = 1) }
        val facts = FactSelector.from(stats.associateBy { it.fact }).review(stats)
        assertEquals(2, facts.size)
        assertEquals(2, facts.toSet().size)
    }
}
