package com.timestablequest.app.data.repository

import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.generation.AnswerChoices
import com.timestablequest.app.domain.progress.FactStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class PracticeServiceTest {
    private val store = FakePracticeStore()
    private var now = 1_000_000L
    private var today = LocalDate.of(2026, 10, 1)
    private val service = PracticeService(store, Random(1234), { now }, { today })

    private fun started(result: StartResult): Long = (result as StartResult.Started).sessionId

    private suspend fun answerAll(sessionId: Long, correct: (QuestionView) -> Boolean = { true }) {
        service.session(sessionId)!!.questions.forEach { q ->
            now += 1_000
            val pick = if (correct(q)) q.correctAnswer else q.options.first { it != q.correctAnswer }
            service.answer(q.id, pick)
        }
    }

    private suspend fun answerFact(sessionId: Long, fact: Fact, correct: Boolean): AnswerResult? {
        val q = service.session(sessionId)!!.questions.first { it.fact == fact }
        now += 1_000
        return service.answer(q.id, if (correct) q.correctAnswer else q.options.first { it != q.correctAnswer })
    }

    @Test
    fun sessionAndAllQuestionsWithOptionsArePersistedBeforeDisplay() = runBlocking {
        val id = started(service.startRowCheck(7))
        val detail = service.session(id)!!
        assertEquals(SessionType.ROW_CHECK, detail.type)
        assertEquals(SessionStatus.ACTIVE, detail.status)
        assertEquals(10, detail.questions.size)
        detail.questions.forEach { q ->
            assertEquals(7, q.fact.row)
            assertTrue(AnswerChoices.isValid(q.options, q.fact.product))
            assertNull(q.selectedAnswer)
        }
        assertEquals(10, store.progress.values.count { it.timesAsked == 1 })
    }

    @Test
    fun progressIsAttributedToTheOrderedFactOnly() = runBlocking {
        val id = started(service.startMixed(setOf(3, 7)))
        val asked = service.session(id)!!.questions.map { it.fact }.toSet()
        answerAll(id)
        val stats = service.factStats()
        stats.values.filter { it.originalAttempts > 0 }.forEach { assertTrue(it.fact in asked) }
        asked.forEach { f ->
            assertEquals(1, stats[f]!!.originalCorrect)
            if (f.reversed !in asked) assertEquals(0, stats[f.reversed]?.originalAttempts ?: 0)
        }
    }

    @Test
    fun reversedFactNeverReceivesProgress() = runBlocking {
        val id = started(service.startRowCheck(3))
        answerAll(id)
        val stats = service.factStats()
        stats.values.filter { it.originalAttempts > 0 }.forEach { assertEquals(3, it.fact.row) }
        (1..12).filter { it != 3 }.forEach { r -> assertNull(stats[Fact(r, 3)]) }
    }

    @Test
    fun firstAnswerIsPersistedOnceAndDuplicatesAreIgnored() = runBlocking {
        val id = started(service.startRowCheck(6))
        val q = service.session(id)!!.questions.first()
        val wrong = q.options.first { it != q.correctAnswer }
        val first = service.answer(q.id, wrong)!!
        assertFalse(first.correct)
        assertFalse(first.alreadyAnswered)
        val second = service.answer(q.id, q.correctAnswer)!!
        assertTrue(second.alreadyAnswered)
        assertFalse(second.correct)
        val stats = service.factStats()[q.fact]!!
        assertEquals(1, stats.originalAttempts)
        assertEquals(0, stats.originalCorrect)
        assertEquals(wrong, service.session(id)!!.questions.first().selectedAnswer)
    }

    @Test
    fun oneUnfinishedNonDailySessionAtATime() = runBlocking {
        val id = started(service.startRowCheck(2))
        val blocked = service.startMixed(setOf(4, 5))
        assertEquals(StartResult.Blocked(id, SessionType.ROW_CHECK), blocked)
        // The daily set is separate and is not blocked.
        val daily = service.openDaily(setOf(1, 2, 3))
        assertNotEquals(id, daily)
        assertTrue(service.endSession(id).not()) // no answers -> discarded
        assertNull(service.session(id))
        assertTrue(service.startMixed(setOf(4, 5)) is StartResult.Started)
    }

    @Test
    fun endingEarlyKeepsAnsweredQuestionsAndExcludesUnansweredFromAccuracy() = runBlocking {
        val id = started(service.startRowCheck(8))
        val qs = service.session(id)!!.questions
        service.answer(qs[0].id, qs[0].correctAnswer)
        service.answer(qs[1].id, qs[1].options.first { it != qs[1].correctAnswer })
        assertTrue(service.endSession(id))
        val d = service.session(id)!!
        assertEquals(SessionStatus.ENDED_EARLY, d.status)
        assertEquals(2, d.answered)
        assertEquals(50, d.accuracyPercent)
        assertNull(service.answer(qs[2].id, qs[2].correctAnswer)) // ended sessions accept no answers
    }

    @Test
    fun completingAllQuestionsCompletesTheSession() = runBlocking {
        val id = started(service.startRowCheck(9))
        answerAll(id)
        val d = service.session(id)!!
        assertEquals(SessionStatus.COMPLETED, d.status)
        assertEquals(100, d.accuracyPercent)
        assertNotNull(d.finishedAt)
    }

    @Test
    fun reviewEntryResolutionAndReopening() = runBlocking {
        val id = started(service.startRowCheck(4))
        val target = service.session(id)!!.questions.first().fact
        answerAll(id) { it.fact != target }
        assertTrue(service.factStats()[target]!!.reviewNeeded)

        val review = started(service.startReview())
        val reviewDetail = service.session(review)!!
        assertEquals(SessionType.REVIEW, reviewDetail.type)
        assertEquals(listOf(target), reviewDetail.questions.map { it.fact })

        // Incorrect retry keeps it queued.
        val rq = reviewDetail.questions.first()
        service.answer(rq.id, rq.options.first { it != rq.correctAnswer })
        assertTrue(service.factStats()[target]!!.reviewNeeded)
        assertEquals(SessionStatus.COMPLETED, service.session(review)!!.status)

        // Correct retry removes it but preserves the original incorrect answer and accuracy.
        val review2 = started(service.startReview())
        val rq2 = service.session(review2)!!.questions.first()
        service.answer(rq2.id, rq2.correctAnswer)
        val resolved = service.factStats()[target]!!
        assertFalse(resolved.reviewNeeded)
        assertEquals(1, resolved.originalAttempts)
        assertEquals(0, resolved.originalCorrect)
        assertEquals(false, service.session(id)!!.questions.first { it.fact == target }.isCorrect)
        assertEquals(2, store.reviewAttempts.size)
        assertEquals(StartResult.Empty, service.startReview())

        // A later incorrect original answer reopens it.
        val again = started(service.startMixed(setOf(4)))
        val q = service.session(again)!!.questions.firstOrNull { it.fact == target }
        if (q != null) {
            service.answer(q.id, q.options.first { it != q.correctAnswer })
            assertTrue(service.factStats()[target]!!.reviewNeeded)
        }
    }

    @Test
    fun reviewRetriesDoNotCountTowardConfidence() = runBlocking {
        // Make 5 × 5 confident is impossible through retries alone.
        val id = started(service.startRowCheck(5))
        val target = service.session(id)!!.questions.first().fact
        answerAll(id) { it.fact != target }
        repeat(3) {
            val r = started(service.startReview())
            val q = service.session(r)!!.questions.first()
            service.answer(q.id, q.options.first { it != q.correctAnswer })
        }
        val r = started(service.startReview())
        val q = service.session(r)!!.questions.first()
        service.answer(q.id, q.correctAnswer)
        val s = service.factStats()[target]!!
        assertEquals(1, s.originalAttempts)
        assertEquals(FactStatus.PRACTICING, s.status)
    }

    @Test
    fun confidenceRequiresDistinctSessionsAndNoHints() = runBlocking {
        val fact = Fact(11, 3)
        // Three correct answers across three mixed sessions on row 11, with a hint in the second.
        var hinted = false
        var answeredCount = 0
        var guard = 0
        while (answeredCount < 3 && guard++ < 50) {
            val id = started(service.startMixed(setOf(11)))
            service.session(id)!!.questions.forEach { q ->
                if (q.fact == fact && answeredCount == 1) {
                    service.useHint(q.id)
                    hinted = true
                }
                service.answer(q.id, q.correctAnswer)
                if (q.fact == fact) answeredCount++
            }
        }
        assertTrue(hinted)
        val s = service.factStats()[fact]!!
        assertEquals(3, s.originalCorrect)
        assertEquals(FactStatus.PRACTICING, s.status)
        assertTrue(s.window.toString(), s.window.any { it.outcome == com.timestablequest.app.domain.progress.Outcome.CORRECT_WITH_HINT })
    }

    @Test
    fun dailySetIsPersistentPerDateAndResumable() = runBlocking {
        val first = service.openDaily((1..12).toSet())
        val again = service.openDaily(setOf(2)) // changed selection applies only to the next date
        assertEquals(first, again)
        val d = service.session(first)!!
        assertEquals("2026-10-01", d.dailyDate)
        assertEquals(10, d.questions.size)
        assertEquals(10, d.questions.map { it.fact }.toSet().size)
        service.setPosition(first, 3)
        assertEquals(3, service.session(first)!!.currentPosition)
        assertEquals(d.questions.map { it.options }, service.session(first)!!.questions.map { it.options })
    }

    @Test
    fun dailyIncludesUnresolvedReviewFacts() = runBlocking {
        val id = started(service.startRowCheck(6))
        answerAll(id) { false }
        val daily = service.session(service.openDaily(setOf(6, 7)))!!
        val reviewFacts = daily.questions.count { it.fact.row == 6 && service.factStats()[it.fact]!!.reviewNeeded }
        assertTrue(reviewFacts >= 4)
        assertTrue(daily.questions.all { it.fact.row in setOf(6, 7) })
    }

    @Test
    fun dateChangeCreatesNewSetAndOldSetStaysIncomplete() = runBlocking {
        val day1 = service.openDaily((1..12).toSet())
        val q = service.session(day1)!!.questions
        service.answer(q[0].id, q[0].correctAnswer)

        // Midnight passes while question 2 is visible: it still finishes in its original set,
        // attributed to the date on which it was answered.
        today = today.plusDays(1)
        service.answer(q[1].id, q[1].correctAnswer)
        val afterMidnight = service.session(day1)!!
        assertEquals("2026-10-01", afterMidnight.questions[0].answeredLocalDate)
        assertEquals("2026-10-02", afterMidnight.questions[1].answeredLocalDate)
        assertEquals("2026-10-01", afterMidnight.dailyDate)

        val day2 = service.openDaily((1..12).toSet())
        assertNotEquals(day1, day2)
        val old = service.session(day1)!!
        assertEquals(SessionStatus.INCOMPLETE, old.status)
        assertEquals(2, old.answered)
        assertEquals(100, old.accuracyPercent) // unanswered questions are not errors

        // Returning to a date with an existing set reuses it.
        today = today.minusDays(1)
        assertEquals(day1, service.openDaily(setOf(5)))
    }

    @Test
    fun retentionPrunesHistoryWithoutLosingFactProgressOrReviewQueue() = runBlocking {
        val first = started(service.startRowCheck(12))
        val wrongFact = service.session(first)!!.questions.first().fact
        answerAll(first) { it.fact != wrongFact }
        val before = service.factStats()

        repeat(PracticeService.RETAIN_NON_DAILY + 5) {
            now += 10_000
            val id = started(service.startRowCheck(1))
            val q = service.session(id)!!.questions.first()
            service.answer(q.id, q.correctAnswer)
            service.endSession(id)
        }
        val nonDaily = store.sessions.values.count { it.type != "DAILY" }
        assertEquals(PracticeService.RETAIN_NON_DAILY, nonDaily)
        assertNull(service.session(first))

        val after = service.factStats()
        assertEquals(before[wrongFact], after[wrongFact])
        assertTrue(after[wrongFact]!!.reviewNeeded)
        assertEquals(PracticeService.RETAIN_NON_DAILY + 5, after.values.filter { it.fact.row == 1 }.sumOf { it.originalAttempts })

        repeat(PracticeService.RETAIN_DAILY + 3) {
            today = today.plusDays(1)
            service.openDaily(setOf(2))
        }
        today = today.plusDays(1)
        service.openDaily(setOf(2))
        assertTrue(store.sessions.values.count { it.type == "DAILY" } <= PracticeService.RETAIN_DAILY + 1)
    }

    @Test
    fun resetRowClearsStatsAndQueueAndInvalidatesActiveSessions() = runBlocking {
        val done = started(service.startRowCheck(3))
        answerAll(done) { false }
        val active = started(service.startMixed(setOf(3, 4)))
        val aq = service.session(active)!!.questions.first()
        service.answer(aq.id, aq.correctAnswer)

        service.resetRow(3, clearHistory = false)
        val stats = service.factStats()
        assertTrue(stats.keys.none { it.row == 3 })
        assertEquals(SessionStatus.ENDED_EARLY, service.session(active)!!.status)
        assertNotNull(service.session(done)) // history is kept read-only
        assertTrue(stats.values.none { it.reviewNeeded && it.fact.row == 3 })

        service.resetRow(3, clearHistory = true)
        assertNull(service.session(done))
    }

    @Test
    fun resetAllClearsProgressButCanKeepHistory() = runBlocking {
        val id = started(service.startRowCheck(10))
        answerAll(id) { false }
        service.resetAll(clearHistory = false)
        assertTrue(service.factStats().isEmpty())
        assertNotNull(service.session(id))
        assertEquals(StartResult.Empty, service.startReview())
        service.resetAll(clearHistory = true)
        assertNull(service.session(id))
    }
}
