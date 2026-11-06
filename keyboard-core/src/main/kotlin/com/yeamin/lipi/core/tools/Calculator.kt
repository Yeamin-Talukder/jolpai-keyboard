package com.yeamin.lipi.core.tools

import java.math.BigDecimal
import java.math.MathContext

/**
 * Typing a sum offers its result in the suggestion strip: `250*4` → 1000 (tap to replace the
 * sum with the answer), or `12*3+4=` → 40 (tap to add the answer after "="). Works with Bangla
 * digits too (`১২+৩` → ১৫), and with × ÷ − as well as * / -.
 *
 * Without "=", phone numbers and dates must not look like sums: a lone "-" or "/" only counts
 * with spaces around it ("10 - 3"), unless + * × or ÷ is also used, and numbers with a leading
 * zero (01711…) rule it out.
 */
object Calculator {

    /** [replace]: the characters before the cursor to replace with [value] (0 = insert after "="). */
    data class Result(val expression: String, val value: String, val replace: Int = 0)

    private const val ALLOWED = "0123456789০১২৩৪৫৬৭৮৯.+-−*/×÷() "
    private const val OPERATORS = "+-−*/×÷"
    private val SPACED_MINUS_OR_SLASH = Regex("""\s[-−/]\s""")
    private val LEADING_ZERO = Regex("""(^|[^\d.০-৯])[0০][\d০-৯]""")

    /** The result for a sum right before the cursor (with or without a final "="), or null. */
    fun resultFor(before: CharSequence): Result? {
        if (before.isEmpty()) return null
        if (before.last() != '=') return implicit(before)
        var i = before.length - 1
        while (i > 0 && ALLOWED.indexOf(before[i - 1]) >= 0) i--
        val expression = before.subSequence(i, before.length - 1).toString().trim()
        if (expression.length < 3) return null
        if (expression.none { OPERATORS.indexOf(it) >= 0 }) return null
        if (expression.none { it.isDigit() }) return null
        val bangla = expression.any { it in '০'..'৯' }
        val value = evaluate(normalise(expression)) ?: return null
        val text = format(value) ?: return null
        return Result(expression, if (bangla) toBanglaDigits(text) else text)
    }

    /** A sum typed without "=": stricter, so ordinary text with numbers isn't mistaken for one. */
    private fun implicit(before: CharSequence): Result? {
        val last = before.last()
        if (!(last.isDigit() || last == ')')) return null
        var i = before.length
        while (i > 0 && ALLOWED.indexOf(before[i - 1]) >= 0) i--
        // The sum must start a word: "abc12+3" or "x=2*3" are not sums.
        val raw = before.subSequence(i, before.length).toString()
        if (i > 0 && !raw.first().isWhitespace()) return null
        val expression = raw.trim()
        if (expression.length < 3) return null
        val strong = expression.any { it in "+*×÷" }
        val spacedWeak = SPACED_MINUS_OR_SLASH.containsMatchIn(expression)
        if (!strong && !spacedWeak) return null
        if (LEADING_ZERO.containsMatchIn(expression)) return null // 017…, 05/…
        if (expression.count { it == '/' } >= 2 && !strong) return null // a date
        if (expression.last().let { it in OPERATORS }) return null
        val bangla = expression.any { it in '০'..'৯' }
        val value = evaluate(normalise(expression)) ?: return null
        val text = format(value) ?: return null
        return Result(expression, if (bangla) toBanglaDigits(text) else text, replace = raw.trimStart().length)
    }

    /** Evaluates + − × ÷ with brackets and unary minus; null for anything malformed. */
    fun evaluate(expression: String): Double? = runCatching { Parser(expression.filter { it != ' ' }).parseAll() }.getOrNull()

    private fun normalise(s: String): String = buildString {
        for (c in s) {
            append(
                when (c) {
                    in '০'..'৯' -> '0' + (c - '০')
                    '×' -> '*'
                    '÷' -> '/'
                    '−' -> '-'
                    else -> c
                },
            )
        }
    }

    private fun format(v: Double): String? {
        if (v.isNaN() || v.isInfinite()) return null
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    private fun toBanglaDigits(s: String) = buildString { for (c in s) append(if (c in '0'..'9') '০' + (c - '0') else c) }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseAll(): Double {
            val v = expression()
            require(pos == s.length) { "trailing input" }
            return v
        }

        private fun expression(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val r = factor()
                require(!(op == '/' && r == 0.0)) { "division by zero" }
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun factor(): Double {
            require(pos < s.length) { "unexpected end" }
            return when (s[pos]) {
                '-' -> { pos++; -factor() }
                '+' -> { pos++; factor() }
                '(' -> {
                    pos++
                    val v = expression()
                    require(pos < s.length && s[pos] == ')') { "missing )" }
                    pos++
                    v
                }
                else -> number()
            }
        }

        private fun number(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start) { "number expected" }
            return s.substring(start, pos).toDouble()
        }
    }
}
