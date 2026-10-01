package com.timestablequest.app.domain.progress

import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import com.timestablequest.app.domain.review.ReviewQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private val f = Fact(6, 7)

    private fun answers(vararg a: Triple<Long, Boolean, Boolean>): FactStats =
        a.foldIndexed(FactStats.empty(f)) { i, s, (session, correct, hint) -> Scoring.applyOriginal(s, session, correct, hint, 1_000L + i) }

    private fun ok(session: Long) = Triple(session, true, false)
    private fun hinted(session: Long) = Triple(session, true, true)
    private fun wrong(session: Long) = Triple(session, false, false)

    @Test
    fun notPracticedUntilFirstScoredAnswer() {
        assertEquals(FactStatus.NOT_PRACTICED, FactStats.empty(f).status)
        assertEquals(FactStatus.PRACTICING, answers(ok(1)).status)
    }

    @Test
    fun confidentNeedsThreeCorrectAcrossAtLeastTwoSessions() {
        assertEquals(FactStatus.PRACTICING, answers(ok(1), ok(1), ok(1)).status)
        assertEquals(FactStatus.CONFIDENT, answers(ok(1), ok(1), ok(2)).status)
        assertEquals(FactStatus.CONFIDENT, answers(ok(1), ok(2), ok(3)).status)
        assertEquals(FactStatus.PRACTICING, answers(ok(1), ok(2)).status)
    }

    @Test
    fun hintAssistedAnswerIsCorrectButNotIndependent() {
        val s = answers(ok(1), hinted(2), ok(3))
        assertEquals(3, s.originalCorrect)
        assertTrue(s.correctAtLeastOnce)
        assertEquals(FactStatus.PRACTICING, s.status)
        // Three further independent answers restore confidence.
        val later = listOf(ok(4), ok(4), ok(5)).fold(s) { acc, (id, c, h) -> Scoring.applyOriginal(acc, id, c, h, 9_000) }
        assertEquals(FactStatus.CONFIDENT, later.status)
    }

    @Test
    fun incorrectAnswerReturnsToPracticingAndQueuesReview() {
        val confident = answers(ok(1), ok(2), ok(3))
        assertEquals(FactStatus.CONFIDENT, confident.status)
        val after = Scoring.applyOriginal(confident, 4, false, false, 50_000)
        assertEquals(FactStatus.PRACTICING, after.status)
        assertTrue(after.reviewNeeded)
        assertEquals(50_000L, after.lastIncorrectAt)
        assertEquals(false, after.latestCorrect)
        assertEquals(4, after.originalAttempts)
        assertEquals(3, after.originalCorrect)
    }

    @Test
    fun reviewRetryNeverChangesOriginalStatsOrWindow() {
        val s = answers(wrong(1))
        val retried = Scoring.applyReviewRetry(s, correct = true, now = 99)
        assertEquals(s.originalAttempts, retried.originalAttempts)
        assertEquals(s.originalCorrect, retried.originalCorrect)
        assertEquals(s.window, retried.window)
        assertFalse(retried.reviewNeeded)
        assertEquals(99L, retried.lastReviewedAt)
        val failed = Scoring.applyReviewRetry(s, correct = false, now = 99)
        assertTrue(failed.reviewNeeded)
    }

    @Test
    fun windowKeepsLatestThreeAndRoundTrips() {
        val s = answers(wrong(1), ok(2), hinted(3), ok(4))
        assertEquals(3, s.window.size)
        assertEquals(listOf(2L, 3L, 4L), s.window.map { it.sessionId })
        assertEquals(s.window, OutcomeWindow.decode(OutcomeWindow.encode(s.window)))
        assertEquals("2:C,3:H,4:C", OutcomeWindow.encode(s.window))
        assertEquals(emptyList<OutcomeEntry>(), OutcomeWindow.decode("bad,1:Z"))
    }

    @Test
    fun rowAccuracyAndCounts() {
        val stats = mutableMapOf<Fact, FactStats>()
        stats[Fact(4, 1)] = answers(ok(1)).copy(fact = Fact(4, 1))
        stats[Fact(4, 2)] = answers(ok(1), wrong(2)).copy(fact = Fact(4, 2))
        stats[Fact(4, 3)] = answers(ok(1), ok(2), ok(3)).copy(fact = Fact(4, 3))
        stats[Fact(2, 4)] = answers(wrong(1)).copy(fact = Fact(2, 4)) // reversed: must not count for row 4
        val rows = RowProgress.all(stats)
        val row4 = rows[3]
        assertEquals(4, row4.row)
        assertEquals(3, row4.correctAtLeastOnce)
        assertEquals(1, row4.confident)
        assertEquals(6, row4.originalAttempts)
        assertEquals(5, row4.originalCorrect)
        assertEquals(83, row4.accuracyPercent)
        assertEquals(1, row4.needsPractice)
        assertEquals(0, rows[1].correctAtLeastOnce)
        assertEquals(1, rows[1].needsPractice)
        assertNull(rows[6].accuracyPercent) // "Not practiced yet"
        assertEquals(12, Facts.row(4).size)
    }

    @Test
    fun reviewQueueOrdering() {
        val unresolvedOld = FactStats(Fact(2, 2), latestCorrect = false, reviewNeeded = true, lastIncorrectAt = 100)
        val unresolvedNew = FactStats(Fact(3, 3), latestCorrect = false, reviewNeeded = true, lastIncorrectAt = 200)
        // Answered correctly in a later original session but still queued (not yet retried correctly).
        val recoveredA = FactStats(Fact(4, 4), latestCorrect = true, reviewNeeded = true, lastIncorrectAt = 300, lastReviewedAt = 50)
        val recoveredB = FactStats(Fact(5, 5), latestCorrect = true, reviewNeeded = true, lastIncorrectAt = 300, lastReviewedAt = null)
        val resolved = FactStats(Fact(6, 6), latestCorrect = false, reviewNeeded = false, lastIncorrectAt = 999)
        val order = ReviewQueue.queue(listOf(recoveredA, resolved, unresolvedOld, recoveredB, unresolvedNew)).map { it.fact }
        assertEquals(listOf(Fact(3, 3), Fact(2, 2), Fact(5, 5), Fact(4, 4)), order)
        val many = (1..12).map { c -> FactStats(Fact(7, c), latestCorrect = false, reviewNeeded = true, lastIncorrectAt = c.toLong()) }
        assertEquals(10, ReviewQueue.sessionFacts(many).size)
    }
}
