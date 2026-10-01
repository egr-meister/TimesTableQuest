package com.timestablequest.app.domain.generation

import com.timestablequest.app.domain.facts.Fact
import com.timestablequest.app.domain.facts.Facts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationsTest {

    @Test
    fun specExamples() {
        assertEquals("7 × 4 = 28. Four groups of 7 make 28.", Explanations.equalGroups(Fact(7, 4)))
        assertEquals("7 × 5 is 7 more than 7 × 4 (28 + 7 = 35).", Explanations.comparePrevious(Fact(7, 5)))
        assertEquals(
            "8 × 6 = 48. Split 6 into 5 and 1: 8 × 5 = 40, then add 8.",
            Explanations.splitSentence(Fact(8, 6)),
        )
        assertEquals("7 + 7 + 7 + 7 = 28", Explanations.repeatedAddition(Fact(7, 4)))
        assertEquals("One group of 9 makes 9.", Explanations.equalGroups(Fact(9, 1)).substringAfter(". "))
    }

    @Test
    fun everyExplanationStartsWithTheExactEquation() {
        Facts.all.forEach { f ->
            val lines = Explanations.explanation(f)
            assertTrue(lines.first().startsWith("${f.row} × ${f.column} = ${f.product}."))
            assertTrue(lines.first().endsWith(" ${f.product}."))
        }
    }

    @Test
    fun splitPartsAreArithmeticallyCorrect() {
        Facts.all.forEach { f ->
            val s = Explanations.split(f)
            if (f.column >= 6) {
                assertNotNull(s)
                assertEquals(f.column, s!!.total)
                assertTrue(s.first > 0 && s.second > 0)
                assertEquals(f.product, f.row * s.first + f.row * s.second)
                val sentence = Explanations.splitSentence(f)!!
                assertTrue(sentence.contains("${f.row} × ${s.first} = ${f.row * s.first}"))
                if (s.second > 1) {
                    assertTrue(sentence.contains("${f.row} × ${s.second} = ${f.row * s.second}"))
                    assertTrue(sentence.contains("${f.row * s.first} + ${f.row * s.second} = ${f.product}"))
                }
            } else {
                assertNull(s)
            }
        }
    }

    @Test
    fun repeatedAdditionSumsToProduct() {
        Facts.all.forEach { f ->
            val text = Explanations.repeatedAddition(f) ?: return@forEach
            val (lhs, rhs) = text.split(" = ")
            assertEquals(f.product, rhs.toInt())
            val terms = lhs.split(" + ").map { it.toInt() }
            assertEquals(f.column, terms.size)
            assertEquals(f.product, terms.sum())
        }
    }

    @Test
    fun comparisonWithPreviousFactIsCorrect() {
        Facts.all.filter { it.column > 1 }.forEach { f ->
            val text = Explanations.comparePrevious(f)
            assertTrue(text, text.contains("(${f.row * (f.column - 1)} + ${f.row} = ${f.product})"))
        }
    }

    @Test
    fun commutativityIsExplainedButOnlyForNonSquares() {
        assertNull(Explanations.commutative(Fact(6, 6)))
        val text = Explanations.commutative(Fact(3, 7))!!
        assertTrue(text.contains("7 × 3"))
        assertTrue(text.contains("row 3") && text.contains("row 7"))
    }

    @Test
    fun hintsNeverRevealTheProductAsAnEquation() {
        Facts.all.forEach { f ->
            val hint = Explanations.hint(f)
            assertFalse("$f: $hint", hint.contains("= ${f.product}"))
            assertTrue(hint.isNotBlank())
        }
    }

    @Test
    fun diagramsHaveTextAlternatives() {
        assertTrue(Explanations.usesDotArray(Fact(7, 4)))
        assertFalse(Explanations.usesDotArray(Fact(12, 12)))
        assertTrue(Explanations.arrayDescription(Fact(7, 4)).contains("28 dots"))
        assertTrue(Explanations.areaDescription(Fact(12, 8)).contains("total 96"))
    }
}
