package com.timestablequest.app.domain.generation

import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts

/** Splitting the column factor into two smaller parts: c = first + second. */
data class SplitParts(val first: Int, val second: Int) {
    val total: Int get() = first + second
}

/**
 * Explanations and hints generated locally from the actual fact. Every number in the text is
 * computed with exact integer arithmetic. Commutativity is mentioned as an idea only; it never
 * awards progress to the reversed fact.
 *
 * Convention: r × c is read as "c groups of r" (7 × 4 = four groups of 7).
 */
object Explanations {

    /** "7 × 4 = 28. Four groups of 7 make 28." */
    fun equalGroups(f: Fact): String {
        val groups = Facts.word(f.column).replaceFirstChar { it.uppercase() }
        val noun = if (f.column == 1) "group" else "groups"
        val verb = if (f.column == 1) "makes" else "make"
        return "${f.expression} = ${f.product}. $groups $noun of ${f.row} $verb ${f.product}."
    }

    /** Repeated addition for small column factors (2..5): "7 + 7 + 7 + 7 = 28". */
    fun repeatedAddition(f: Fact): String? {
        if (f.column !in 2..5) return null
        return List(f.column) { f.row }.joinToString(" + ") + " = ${f.product}"
    }

    /** Split used for larger column factors: 6..10 → 5 + rest, 11..12 → 10 + rest. */
    fun split(f: Fact): SplitParts? = when (f.column) {
        in 6..10 -> SplitParts(5, f.column - 5)
        11, 12 -> SplitParts(10, f.column - 10)
        else -> null
    }

    /** "8 × 6 = 48. Split 6 into 5 and 1: 8 × 5 = 40, then add 8." */
    fun splitSentence(f: Fact): String? {
        val s = split(f) ?: return null
        val a = f.row * s.first
        val b = f.row * s.second
        val head = "${f.expression} = ${f.product}. Split ${f.column} into ${s.first} and ${s.second}: ${f.row} × ${s.first} = $a"
        return if (s.second == 1) {
            "$head, then add ${f.row}."
        } else {
            "$head and ${f.row} × ${s.second} = $b, so $a + $b = ${f.product}."
        }
    }

    /** "7 × 5 is 7 more than 7 × 4 (28 + 7 = 35)." */
    fun comparePrevious(f: Fact): String {
        if (f.column == 1) return "${f.expression} is one group of ${f.row}, so it is just ${f.row}."
        val prev = Fact(f.row, f.column - 1)
        return "${f.expression} is ${f.row} more than ${prev.expression} (${prev.product} + ${f.row} = ${f.product})."
    }

    /** "7 × 4 has the same product as 4 × 7, but each is practiced in its own row." */
    fun commutative(f: Fact): String? {
        if (f.row == f.column) return null
        return "${f.expression} has the same product as ${f.reversed.expression}. " +
            "They are practiced separately: one in row ${f.row}, the other in row ${f.column}."
    }

    /** Ordered explanation lines shown after answering and in Study mode. */
    fun explanation(f: Fact): List<String> = buildList {
        add(equalGroups(f))
        repeatedAddition(f)?.let { add(it) }
        splitSentence(f)?.let { add(it) }
        add(comparePrevious(f))
        commutative(f)?.let { add(it) }
    }

    /** One short line used as feedback right after an answer. */
    fun shortExplanation(f: Fact): String = splitSentence(f) ?: equalGroups(f)

    /** Hint that guides toward the answer without stating the product. */
    fun hint(f: Fact): String {
        val r = f.row
        val c = f.column
        return when {
            c == 1 -> "One group of $r is just $r."
            c == 2 -> "Double $r: $r + $r."
            c in 3..5 -> "Add $c groups of $r: " + List(c) { r }.joinToString(" + ") + "."
            c == 10 -> "Ten groups of $r: think of $r tens."
            else -> {
                val s = split(f)!!
                "Split $c into ${s.first} and ${s.second}. Work out $r × ${s.first} and $r × ${s.second}, then add them."
            }
        }
    }

    /** Text alternative for an array or area diagram. */
    fun arrayDescription(f: Fact): String =
        "An array of ${f.column} rows with ${f.row} dots in each row, ${f.product} dots in total."

    fun areaDescription(f: Fact): String {
        val s = split(f)
        return if (s == null) {
            "A rectangle ${f.row} wide and ${f.column} tall, with area ${f.product}."
        } else {
            "A rectangle ${f.row} wide and ${f.column} tall, split into ${f.row} × ${s.first} = ${f.row * s.first} " +
                "and ${f.row} × ${s.second} = ${f.row * s.second}, total ${f.product}."
        }
    }

    /** Dot arrays are drawn only for manageable sizes; larger facts use a compact area diagram. */
    fun usesDotArray(f: Fact): Boolean = f.product <= 60 && f.row <= 10 && f.column <= 10
}
