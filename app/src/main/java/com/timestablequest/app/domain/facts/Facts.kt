package com.timestablequest.app.domain.facts

/** Factors of the curriculum: 1 through 12. */
val FACTORS: IntRange = 1..12

/**
 * One ordered multiplication fact, `row × column`.
 *
 * The row factor identifies the table being studied. 3 × 7 belongs to row 3 and 7 × 3 belongs to
 * row 7: they share a product but are separate study facts, and progress on one is never awarded
 * to the other.
 */
data class Fact(val row: Int, val column: Int) : Comparable<Fact> {
    init {
        require(row in FACTORS) { "row $row out of range" }
        require(column in FACTORS) { "column $column out of range" }
    }

    /** Exact integer product, always in 1..144. */
    val product: Int get() = row * column

    /** The other orientation of the same multiplication pair (may equal this fact for squares). */
    val reversed: Fact get() = Fact(column, row)

    /** Display form, e.g. "7 × 4". */
    val expression: String get() = "$row × $column"

    /** Screen-reader form, e.g. "7 times 4". */
    val spoken: String get() = "$row times $column"

    /** Stable string key, e.g. "7x4". */
    val key: String get() = "${row}x$column"

    override fun compareTo(other: Fact): Int = compareValuesBy(this, other, { it.row }, { it.column })

    companion object {
        fun parseKey(key: String): Fact? {
            val parts = key.split('x')
            if (parts.size != 2) return null
            val r = parts[0].toIntOrNull() ?: return null
            val c = parts[1].toIntOrNull() ?: return null
            return if (r in FACTORS && c in FACTORS) Fact(r, c) else null
        }
    }
}

object Facts {
    /** All 144 ordered facts, row-major: 1 × 1, 1 × 2, … 12 × 12. */
    val all: List<Fact> = FACTORS.flatMap { r -> FACTORS.map { c -> Fact(r, c) } }

    /** The twelve facts of one row, in column order. */
    fun row(row: Int): List<Fact> {
        require(row in FACTORS)
        return FACTORS.map { Fact(row, it) }
    }

    /** "The 7 times table". */
    fun rowTitle(row: Int): String = "The $row times table"

    /** Encodes a row selection as "1,3,7". */
    fun encodeRows(rows: Collection<Int>): String = rows.filter { it in FACTORS }.distinct().sorted().joinToString(",")

    /** Decodes "1,3,7"; ignores invalid entries. */
    fun decodeRows(text: String?): Set<Int> =
        text.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in FACTORS }.toSortedSet()

    /** Human label for a row selection, e.g. "All rows", "×7", "×3, ×4, ×9". */
    fun rowsLabel(rows: Collection<Int>): String {
        val sorted = rows.distinct().sorted()
        return when {
            sorted.isEmpty() -> "No rows"
            sorted.size == FACTORS.count() -> "All rows"
            else -> sorted.joinToString(", ") { "×$it" }
        }
    }

    private val words = listOf(
        "zero", "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve",
    )

    /** Number word for 0..12 ("four"); digits otherwise. */
    fun word(n: Int): String = words.getOrNull(n) ?: n.toString()
}
