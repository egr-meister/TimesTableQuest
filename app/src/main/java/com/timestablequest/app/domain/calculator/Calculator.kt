package com.timestablequest.app.domain.calculator

import java.math.BigDecimal
import java.math.RoundingMode

enum class CalcOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷"),
}

sealed interface CalcKey {
    data class Digit(val digit: Int) : CalcKey
    data object Decimal : CalcKey
    data class Operator(val operator: CalcOperator) : CalcKey
    data object Equals : CalcKey
    data object Clear : CalcKey
    data object Backspace : CalcKey
    data object ToggleSign : CalcKey
    /** Inserts a value (e.g. from history, "Use result") as the current entry. */
    data class UseValue(val value: String) : CalcKey
}

data class CalculationRecord(
    val left: String,
    val operator: CalcOperator,
    val right: String,
    val result: String,
    val rounded: Boolean,
)

/**
 * Immutable calculator state.
 * - [entry]: number currently being typed ("" = nothing typed; "-" = a negative number has been started).
 * - [left] + [operator]: pending binary operation.
 * - [result]: last successful result shown after "=".
 */
data class CalculatorState(
    val entry: String = "",
    val left: String? = null,
    val operator: CalcOperator? = null,
    val result: String? = null,
    val resultRounded: Boolean = false,
    val lastExpression: String? = null,
    val error: String? = null,
) {
    val display: String
        get() = error ?: entry.ifEmpty { result ?: left ?: "0" }

    val expression: String
        get() = when {
            error != null -> ""
            left != null && operator != null -> "$left ${operator.symbol} ${entry}".trimEnd()
            result != null && lastExpression != null -> lastExpression
            else -> ""
        }

    val showsApproximate: Boolean get() = error == null && entry.isEmpty() && result != null && resultRounded
}

data class CalcOutput(val state: CalculatorState, val record: CalculationRecord? = null)

object CalculatorEngine {
    val LIMIT: BigDecimal = BigDecimal(1_000_000)
    const val MAX_FRACTION_DIGITS = 6
    private const val MAX_INTEGER_DIGITS = 7

    const val ERROR_DIVIDE_BY_ZERO = "You can't divide by zero. Press C to start again."
    const val ERROR_OUT_OF_RANGE = "That number is too big. Results must stay between −1,000,000 and 1,000,000."

    fun press(state: CalculatorState, key: CalcKey): CalcOutput = when (key) {
        is CalcKey.Digit -> CalcOutput(digit(state, key.digit))
        CalcKey.Decimal -> CalcOutput(decimal(state))
        is CalcKey.Operator -> operator(state, key.operator)
        CalcKey.Equals -> equals(state)
        CalcKey.Clear -> CalcOutput(CalculatorState())
        CalcKey.Backspace -> CalcOutput(backspace(state))
        CalcKey.ToggleSign -> CalcOutput(toggleSign(state))
        is CalcKey.UseValue -> CalcOutput(useValue(state, key.value))
    }

    private fun digit(state: CalculatorState, d: Int): CalculatorState {
        require(d in 0..9)
        val base = when {
            state.error != null -> CalculatorState()
            state.result != null -> state.copy(result = null, resultRounded = false, lastExpression = null)
            else -> state
        }
        val newEntry = appendDigit(base.entry, d) ?: return base
        return base.copy(entry = newEntry)
    }

    /** Returns null when the digit is rejected (limits). */
    internal fun appendDigit(entry: String, d: Int): String? {
        val negative = entry.startsWith("-")
        val body = entry.removePrefix("-")
        val newBody = when {
            body.isEmpty() -> d.toString()
            body == "0" -> d.toString()
            body.contains('.') -> {
                if (body.substringAfter('.').length >= MAX_FRACTION_DIGITS) return null
                body + d
            }
            else -> {
                if (body.length >= MAX_INTEGER_DIGITS) return null
                body + d
            }
        }
        val candidate = (if (negative) "-" else "") + newBody
        val value = parse(candidate) ?: return null
        if (value.abs() > LIMIT) return null
        return candidate
    }

    private fun decimal(state: CalculatorState): CalculatorState {
        val base = when {
            state.error != null -> CalculatorState()
            state.result != null -> state.copy(result = null, resultRounded = false, lastExpression = null)
            else -> state
        }
        val e = base.entry
        val newEntry = when {
            e.contains('.') -> e
            e.isEmpty() -> "0."
            e == "-" -> "-0."
            else -> "$e."
        }
        return base.copy(entry = newEntry)
    }

    private fun operator(state: CalculatorState, op: CalcOperator): CalcOutput {
        if (state.error != null) return CalcOutput(state)
        val entryValue = parse(state.entry)
        return when {
            state.left != null && state.operator != null && entryValue != null -> {
                when (val r = compute(BigDecimal(state.left), state.operator, entryValue)) {
                    is ComputeResult.Ok -> CalcOutput(
                        CalculatorState(left = r.text, operator = op),
                        CalculationRecord(state.left, state.operator, canonical(entryValue), r.text, r.rounded),
                    )
                    is ComputeResult.Error -> CalcOutput(CalculatorState(error = r.message))
                }
            }
            entryValue != null -> CalcOutput(CalculatorState(left = canonical(entryValue), operator = op))
            state.result != null -> CalcOutput(CalculatorState(left = state.result, operator = op))
            state.left != null -> CalcOutput(state.copy(operator = op, entry = ""))
            else -> CalcOutput(CalculatorState(left = "0", operator = op))
        }
    }

    private fun equals(state: CalculatorState): CalcOutput {
        if (state.error != null) return CalcOutput(state)
        val left = state.left ?: return CalcOutput(state)
        val op = state.operator ?: return CalcOutput(state)
        val right = parse(state.entry) ?: return CalcOutput(state)
        return when (val r = compute(BigDecimal(left), op, right)) {
            is ComputeResult.Ok -> {
                val rightText = canonical(right)
                CalcOutput(
                    CalculatorState(
                        result = r.text,
                        resultRounded = r.rounded,
                        lastExpression = "$left ${op.symbol} $rightText =",
                    ),
                    CalculationRecord(left, op, rightText, r.text, r.rounded),
                )
            }
            is ComputeResult.Error -> CalcOutput(CalculatorState(error = r.message))
        }
    }

    private fun backspace(state: CalculatorState): CalculatorState = when {
        state.error != null -> CalculatorState()
        state.entry.isNotEmpty() -> {
            val e = state.entry.dropLast(1)
            state.copy(entry = if (e == "-") "" else e)
        }
        else -> state
    }

    private fun toggleSign(state: CalculatorState): CalculatorState = when {
        state.error != null -> state
        state.entry.isNotEmpty() -> {
            val e = state.entry
            state.copy(entry = if (e.startsWith("-")) e.removePrefix("-") else "-$e")
        }
        state.result != null -> {
            val negated = canonical(BigDecimal(state.result).negate())
            state.copy(entry = negated, result = null, resultRounded = false, lastExpression = null)
        }
        else -> state.copy(entry = "-")
    }

    private fun useValue(state: CalculatorState, value: String): CalculatorState {
        val v = parse(value) ?: return state
        if (v.abs() > LIMIT || v.stripTrailingZeros().scale() > MAX_FRACTION_DIGITS) return state
        val base = if (state.error != null) CalculatorState() else state
        return base.copy(entry = canonical(v), result = null, resultRounded = false, lastExpression = null)
    }

    sealed interface ComputeResult {
        data class Ok(val value: BigDecimal, val rounded: Boolean) : ComputeResult {
            val text: String get() = canonical(value)
        }
        data class Error(val message: String) : ComputeResult
    }

    fun compute(a: BigDecimal, op: CalcOperator, b: BigDecimal): ComputeResult {
        if (a.abs() > LIMIT || b.abs() > LIMIT) return ComputeResult.Error(ERROR_OUT_OF_RANGE)
        val exact: BigDecimal? = when (op) {
            CalcOperator.ADD -> a.add(b)
            CalcOperator.SUBTRACT -> a.subtract(b)
            CalcOperator.MULTIPLY -> a.multiply(b)
            CalcOperator.DIVIDE -> {
                if (b.signum() == 0) return ComputeResult.Error(ERROR_DIVIDE_BY_ZERO)
                null
            }
        }
        val value: BigDecimal
        val rounded: Boolean
        if (exact != null) {
            val stripped = exact.stripTrailingZeros()
            if (stripped.scale() > MAX_FRACTION_DIGITS) {
                value = exact.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
                rounded = true
            } else {
                value = exact
                rounded = false
            }
        } else {
            value = a.divide(b, MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
            rounded = value.multiply(b).compareTo(a) != 0
        }
        if (value.abs() > LIMIT) return ComputeResult.Error(ERROR_OUT_OF_RANGE)
        return ComputeResult.Ok(value, rounded)
    }

    fun parse(text: String): BigDecimal? {
        if (text.isEmpty() || text == "-") return null
        val t = if (text.endsWith(".")) text.dropLast(1) else text
        return try {
            BigDecimal(t)
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun canonical(v: BigDecimal): String {
        if (v.signum() == 0) return "0"
        return v.stripTrailingZeros().toPlainString()
    }

    // ---- draft persistence (DataStore string) ----

    fun encode(state: CalculatorState): String = listOf(
        state.entry,
        state.left.orEmpty(),
        state.operator?.name.orEmpty(),
        state.result.orEmpty(),
        if (state.resultRounded) "1" else "0",
        state.lastExpression.orEmpty(),
    ).joinToString("\u001F")

    fun decode(text: String?): CalculatorState {
        if (text.isNullOrEmpty()) return CalculatorState()
        val p = text.split("\u001F")
        if (p.size != 6) return CalculatorState()
        return try {
            CalculatorState(
                entry = p[0].takeIf { it.isEmpty() || it == "-" || parse(it) != null } ?: "",
                left = p[1].ifEmpty { null }?.also { BigDecimal(it) },
                operator = p[2].ifEmpty { null }?.let { CalcOperator.valueOf(it) },
                result = p[3].ifEmpty { null }?.also { BigDecimal(it) },
                resultRounded = p[4] == "1",
                lastExpression = p[5].ifEmpty { null },
            )
        } catch (_: IllegalArgumentException) {
            CalculatorState()
        }
    }
}
