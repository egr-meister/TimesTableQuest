package com.timestablequest.app.domain.generation

import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnswerChoicesTest {

    @Test
    fun fourDistinctOptionsWithExactlyOneCorrectForEveryFact() {
        for (seed in 0 until 25) {
            val random = Random(seed)
            Facts.all.forEach { f ->
                val options = AnswerChoices.generate(f, random)
                assertTrue("$f -> $options", AnswerChoices.isValid(options, f.product))
                assertEquals(4, options.size)
                assertEquals(4, options.toSet().size)
                assertEquals(1, options.count { it == f.product })
                assertTrue(options.all { it in 1..180 })
            }
        }
    }

    @Test
    fun distractorsAreMostlyPlausibleMistakes() {
        val random = Random(7)
        Facts.all.forEach { f ->
            val plausible = AnswerChoices.plausibleDistractors(f).filter { it in 1..180 && it != f.product }.toSet()
            val distractors = AnswerChoices.generate(f, random).filter { it != f.product }
            if (plausible.size >= 3) assertTrue("$f -> $distractors", distractors.all { it in plausible })
        }
    }

    @Test
    fun smallestFactUsesBoundedFallbackWithoutInvalidValues() {
        val options = AnswerChoices.generate(Fact(1, 1), Random(1))
        assertTrue(AnswerChoices.isValid(options, 1))
        assertTrue(options.none { it <= 0 })
    }

    @Test
    fun seededGenerationIsDeterministicAndPositionsVary() {
        val f = Fact(7, 8)
        assertEquals(AnswerChoices.generate(f, Random(42)), AnswerChoices.generate(f, Random(42)))
        val positions = (0 until 200).map { seed -> AnswerChoices.generate(f, Random(seed)).indexOf(56) }.toSet()
        assertEquals(setOf(0, 1, 2, 3), positions)
    }

    @Test
    fun encodingRoundTrips() {
        val options = listOf(28, 24, 35, 32)
        assertEquals(options, AnswerChoices.decode(AnswerChoices.encode(options)))
    }
}
