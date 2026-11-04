package com.danylom73.parallel

import com.danylom73.expression.Expression
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal object ConstantFolding {
    private const val MAX_DIGITS = 1000
    private const val MAX_POWER = 100

    fun number(expression: Expression): BigDecimal? = (expression as? Expression.Value)
        ?.text?.toBigDecimalOrNull()?.takeIf(::manageable)

    fun value(number: BigDecimal): Expression.Value = Expression.Value(number.stripTrailingZeros().toPlainString())

    fun binary(operator: String, left: Expression, right: Expression): Expression.Value? {
        val a = number(left) ?: return null
        val b = number(right) ?: return null
        return exact {
            when (operator) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> a.divide(b)
                else -> return null
            }
        }
    }

    fun call(name: String, arguments: List<Expression>): Expression.Value? {
        val numbers = arguments.map { number(it) ?: return null }
        return exact {
            val first = numbers.first()
            when (name) {
                "abs" -> first.abs()
                "min" -> first.min(numbers[1])
                "max" -> first.max(numbers[1])
                "sqrt" -> first.sqrt(MathContext.UNLIMITED)
                "round" -> first.setScale(0, RoundingMode.HALF_UP)
                "sin" -> if (first.signum() == 0) BigDecimal.ZERO else return null
                "cos" -> if (first.signum() == 0) BigDecimal.ONE else return null
                "pow" -> {
                    val exponent = numbers[1].intValueExact()
                    if (exponent !in -MAX_POWER..MAX_POWER || (first.signum() == 0 && exponent <= 0)) {
                        return null
                    }
                    if (exponent < 0) BigDecimal.ONE.divide(first.pow(-exponent)) else first.pow(exponent)
                }
                else -> return null
            }
        }
    }

    private fun manageable(number: BigDecimal): Boolean =
        number.precision() <= MAX_DIGITS && number.scale() in -MAX_DIGITS..MAX_DIGITS

    private inline fun exact(compute: () -> BigDecimal): Expression.Value? = try {
        compute().takeIf(::manageable)?.let(::value)
    } catch (_: ArithmeticException) {
        null
    }
}
