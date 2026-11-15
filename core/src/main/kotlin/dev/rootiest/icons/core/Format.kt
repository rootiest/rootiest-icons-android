package dev.rootiest.icons.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Numbers written as render-icon.py (Python) and the homepage (JavaScript) write them, so the SVG text built here is
// the same, byte for byte, as theirs.

// Python's "%.{d}f": the exact binary value, rounded half to even.
fun pyFixed(x: Double, d: Int): String {
    val out = BigDecimal(x).setScale(d, RoundingMode.HALF_EVEN)
    val text = out.toPlainString()
    return if (out.signum() == 0 && (x < 0 || 1 / x < 0)) "-$text" else text
}

// Python's "%.{p}g".
fun fmtG(x: Double, p: Int = 6): String {
    if (x == 0.0) return "0"
    val bd = BigDecimal(x).round(MathContext(p, RoundingMode.HALF_EVEN))
    val exp = bd.precision() - bd.scale() - 1
    if (exp < -4 || exp >= p) {
        val digits = bd.unscaledValue().abs().toString().trimEnd('0').ifEmpty { "0" }
        val mantissa = digits.take(1) + if (digits.length > 1) "." + digits.drop(1) else ""
        val sign = if (bd.signum() < 0) "-" else ""
        return "$sign${mantissa}e${if (exp < 0) "-" else "+"}${kotlin.math.abs(exp).toString().padStart(2, '0')}"
    }
    return bd.stripTrailingZeros().toPlainString()
}

// A number as JavaScript writes it: 704, not 704.0.
fun jsNum(v: Double): String {
    if (v == 0.0) return "0"
    if (v == Math.rint(v) && kotlin.math.abs(v) < 1e21) return BigDecimal(v).toBigInteger().toString()
    val text = BigDecimal(v.toString()).stripTrailingZeros()
    val exp = text.precision() - text.scale() - 1
    if (exp in -6..20) return text.toPlainString()
    val digits = text.unscaledValue().abs().toString()
    return (if (v < 0) "-" else "") + digits.take(1) + (if (digits.length > 1) "." + digits.drop(1) else "") +
        "e" + (if (exp < 0) "-" else "+") + kotlin.math.abs(exp)
}

// Python's round(): halves go to the even neighbour.
fun pyRound(x: Double): Long = Math.rint(x).toLong()

// Python's float %: the result takes the divisor's sign.
fun pyMod(x: Double, m: Double): Double {
    val r = x % m
    return if (r != 0.0 && (r < 0) != (m < 0)) r + m else r
}
