package com.timestablequest.app.domain.facts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FactsTest {

    @Test
    fun all144OrderedFactsWithExactProducts() {
        assertEquals(144, Facts.all.size)
        assertEquals(144, Facts.all.toSet().size)
        var i = 0
        for (r in 1..12) for (c in 1..12) {
            val f = Facts.all[i++]
            assertEquals(r, f.row)
            assertEquals(c, f.column)
            assertEquals(r * c, f.product)
            assertTrue(f.product in 1..144)
        }
        assertEquals(Fact(1, 1), Facts.all.first())
        assertEquals(Fact(12, 12), Facts.all.last())
        assertEquals(144, Fact(12, 12).product)
    }

    @Test
    fun rowAttributionKeepsOrientationsSeparate() {
        val a = Fact(3, 7)
        val b = Fact(7, 3)
        assertEquals(a.product, b.product)
        assertNotEquals(a, b)
        assertEquals(3, a.row)
        assertEquals(7, b.row)
        assertEquals(b, a.reversed)
        assertTrue(a in Facts.row(3))
        assertTrue(b !in Facts.row(3))
        assertTrue(b in Facts.row(7))
    }

    @Test
    fun eachRowHasTwelveFactsInOrder() {
        for (r in 1..12) {
            val row = Facts.row(r)
            assertEquals(12, row.size)
            assertEquals((1..12).toList(), row.map { it.column })
            assertTrue(row.all { it.row == r })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeFactors() {
        Fact(0, 5)
    }

    @Test
    fun keysAndRowSelectionsRoundTrip() {
        Facts.all.forEach { assertEquals(it, Fact.parseKey(it.key)) }
        assertNull(Fact.parseKey("13x1"))
        assertNull(Fact.parseKey("garbage"))
        assertEquals(setOf(3, 7, 12), Facts.decodeRows(Facts.encodeRows(listOf(12, 3, 7, 3, 99))))
        assertEquals("All rows", Facts.rowsLabel((1..12).toList()))
        assertEquals("×3, ×7", Facts.rowsLabel(listOf(7, 3)))
        assertEquals("7 × 4", Fact(7, 4).expression)
        assertEquals("7 times 4", Fact(7, 4).spoken)
    }
}
