package com.timestablequest.app.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private var state = CalculatorState()
    private val records = mutableListOf<CalculationRecord>()

    private fun press(vararg keys: CalcKey) {
        keys.forEach { k ->
            val out = CalculatorEngine.press(state, k)
            state = out.state
            out.record?.let { records += it }
        }
    }

    private fun type(text: String) {
        text.forEach { c ->
            when (c) {
                in '0'..'9' -> press(CalcKey.Digit(c - '0'))
                '.' -> press(CalcKey.Decimal)
                '+' -> press(CalcKey.Operator(CalcOperator.ADD))
                '-' -> press(CalcKey.Operator(CalcOperator.SUBTRACT))
                '*' -> press(CalcKey.Operator(CalcOperator.MULTIPLY))
                '/' -> press(CalcKey.Operator(CalcOperator.DIVIDE))
                '=' -> press(CalcKey.Equals)
                '~' -> press(CalcKey.ToggleSign)
                '<' -> press(CalcKey.Backspace)
            }
        }
    }

    @Test
    fun basicOperations() {
        type("12+30="); assertEquals("42", state.display)
        press(CalcKey.Clear); type("7-10="); assertEquals("-3", state.display)
        press(CalcKey.Clear); type("2.5*4="); assertEquals("10", state.display)
        press(CalcKey.Clear); type("9/4="); assertEquals("2.25", state.display)
        assertFalse(state.showsApproximate)
    }

    @Test
    fun decimalAdditionIsExact() {
        type("0.1+0.2=")
        assertEquals("0.3", state.display)
    }

    @Test
    fun divisionRoundsHalfUpToSixDigitsAndMarksApproximate() {
        type("2/3=")
        assertEquals("0.666667", state.display)
        assertTrue(state.showsApproximate)
        assertTrue(records.last().rounded)
        press(CalcKey.Clear); type("1/8=")
        assertEquals("0.125", state.display)
        assertFalse(state.showsApproximate)
    }

    @Test
    fun multiplicationBeyondSixDecimalsIsRounded() {
        type("0.001*0.0005=")
        assertEquals("0.000001", state.display) // 0.0000005 → HALF_UP
        assertTrue(state.showsApproximate)
    }

    @Test
    fun divisionByZeroShowsErrorAndNoHistory() {
        type("5/0=")
        assertEquals(CalculatorEngine.ERROR_DIVIDE_BY_ZERO, state.error)
        assertTrue(records.isEmpty())
        type("3")
        assertNull(state.error)
        assertEquals("3", state.display)
    }

    @Test
    fun outOfRangeResultShowsErrorAndNoHistory() {
        type("1000000+1=")
        assertEquals(CalculatorEngine.ERROR_OUT_OF_RANGE, state.error)
        assertTrue(records.isEmpty())
        press(CalcKey.Clear)
        type("1000000*-")
        // operator replaced; still no record
        assertTrue(records.isEmpty())
    }

    @Test
    fun operandLimits() {
        type("1000000"); assertEquals("1000000", state.display)
        type("0"); assertEquals("1000000", state.display) // would exceed limit
        press(CalcKey.Clear)
        type("1.1234567"); assertEquals("1.123456", state.display) // max six fractional digits
        press(CalcKey.Clear)
        type("1000001"); assertEquals("100000", state.display)
    }

    @Test
    fun maximumValuesAreAllowed() {
        type("999999+1=")
        assertEquals("1000000", state.display)
        press(CalcKey.Clear)
        type("~1000000-0=")
        assertEquals("-1000000", state.display)
    }

    @Test
    fun preventsMultipleDecimalPointsAndNormalizesLeadingZeros() {
        type("0.5.5"); assertEquals("0.55", state.display)
        press(CalcKey.Clear)
        type("0007"); assertEquals("7", state.display)
        press(CalcKey.Clear)
        type("."); assertEquals("0.", state.display)
    }

    @Test
    fun trailingZerosAreRemoved() {
        type("1.50*2=")
        assertEquals("3", state.display)
        press(CalcKey.Clear)
        type("2.50+0=")
        assertEquals("2.5", state.display)
    }

    @Test
    fun negativeOperandsAndResults() {
        type("~5*~3=")
        assertEquals("15", state.display)
        press(CalcKey.Clear)
        type("~2.5+1=")
        assertEquals("-1.5", state.display)
    }

    @Test
    fun repeatedEqualsDoesNotRepeat() {
        type("2+3===")
        assertEquals("5", state.display)
        assertEquals(1, records.size)
    }

    @Test
    fun digitAfterResultStartsNew() {
        type("2+3=4")
        assertEquals("4", state.display)
        assertNull(state.left)
        type("+1=")
        assertEquals("5", state.display)
    }

    @Test
    fun operatorAfterResultContinues() {
        type("2+3=*4=")
        assertEquals("20", state.display)
        assertEquals(2, records.size)
    }

    @Test
    fun backspace() {
        type("123<")
        assertEquals("12", state.display)
        type("<<<")
        assertEquals("0", state.display)
    }

    @Test
    fun useValueFromHistory() {
        type("2/3=")
        val r = records.last()
        press(CalcKey.Clear)
        type("1+")
        press(CalcKey.UseValue(r.result))
        type("=")
        assertEquals("1.666667", state.display)
    }

    @Test
    fun draftRoundTrip() {
        type("12.5+3")
        val decoded = CalculatorEngine.decode(CalculatorEngine.encode(state))
        assertEquals(state, decoded)
        assertNotNull(decoded.operator)
        assertEquals(CalculatorState(), CalculatorEngine.decode("garbage"))
    }
}
