package com.timestablequest.app.domain.generation

import com.timestablequest.app.domain.facts.Fact
import kotlin.random.Random

/**
 * Four distinct positive integer options with exactly one correct answer, in shuffled positions.
 *
 * Distractors model plausible mistakes:
 *  - adjacent column product: r × (c ± 1)
 *  - adjacent row product: (r ± 1) × c
 *  - adding instead of multiplying: r + c
 *  - nearby product / counting error: p ± 1, p ± 2, p ± 10
 * Values outside 1..180, duplicates and accidental correct answers are filtered. A bounded fallback
 * (p ± 1, p ± 2, … within range) fills any remaining slots.
 */
object AnswerChoices {
    const val COUNT = 4
    const val MIN_VALUE = 1
    const val MAX_VALUE = 180

    fun plausibleDistractors(fact: Fact): List<Int> {
        val r = fact.row
        val c = fact.column
        val p = fact.product
        return listOf(
            r * (c + 1), r * (c - 1),
            (r + 1) * c, (r - 1) * c,
            r + c,
            p + 1, p - 1, p + 2, p - 2, p + 10, p - 10,
        )
    }

    fun generate(fact: Fact, random: Random): List<Int> {
        val p = fact.product
        val chosen = LinkedHashSet<Int>()
        plausibleDistractors(fact)
            .filter { it in MIN_VALUE..MAX_VALUE && it != p }
            .distinct()
            .shuffled(random)
            .forEach { if (chosen.size < COUNT - 1) chosen += it }
        // Bounded fallback: nearest unused values. The range always holds enough candidates.
        var d = 1
        while (chosen.size < COUNT - 1 && d <= MAX_VALUE) {
            for (candidate in intArrayOf(p + d, p - d)) {
                if (chosen.size < COUNT - 1 && candidate in MIN_VALUE..MAX_VALUE && candidate != p) chosen += candidate
            }
            d++
        }
        return (chosen.toList() + p).shuffled(random)
    }

    fun isValid(options: List<Int>, correct: Int): Boolean =
        options.size == COUNT &&
            options.toSet().size == COUNT &&
            options.count { it == correct } == 1 &&
            options.all { it in MIN_VALUE..MAX_VALUE }

    fun encode(options: List<Int>): String = options.joinToString(",")

    fun decode(text: String): List<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull() }
}
