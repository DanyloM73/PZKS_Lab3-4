package com.danylom73.parallel

import com.danylom73.expression.Expression
import java.math.BigDecimal

internal class AlgebraicSimplifier(
    private val trace: OptimizationTrace? = null,
) {
    private data class Rewrite(val expression: Expression, val rule: OptimizationRule? = null)

    private val zero = Expression.Value("0")
    private val one = Expression.Value("1")

    fun simplify(expression: Expression, path: List<Int> = emptyList()): Expression {
        val rewrite = when (expression) {
            is Expression.Value -> Rewrite(
                ConstantFolding.number(expression)?.let(ConstantFolding::value) ?: expression,
                OptimizationRule.CONSTANT_FORMAT,
            )
            is Expression.Unary -> unary(expression.operator, simplify(expression.operand, path + 0))
            is Expression.Binary -> binary(
                expression.operator, simplify(expression.left, path + 0), simplify(expression.right, path + 1),
            )
            is Expression.Call -> call(expression.name, expression.arguments.mapIndexed { index, argument ->
                simplify(argument, path + index)
            })
        }
        rewrite.rule?.let { trace?.record(it, path, rewrite.expression) }
        return rewrite.expression
    }

    private fun unary(operator: String, operand: Expression): Rewrite {
        val number = ConstantFolding.number(operand)
        val result = when {
            operator == "+" -> operand
            operator != "-" -> Expression.Unary(operator, operand)
            number != null -> ConstantFolding.value(number.negate())
            operand is Expression.Unary && operand.operator == "-" -> operand.operand
            else -> Expression.Unary("-", operand)
        }
        return Rewrite(result, OptimizationRule.UNARY_SIGN)
    }

    private fun binary(operator: String, left: Expression, right: Expression): Rewrite {
        ConstantFolding.binary(operator, left, right)?.let { return Rewrite(it, OptimizationRule.CONSTANT_FOLDING) }
        when (operator) {
            "+" -> {
                if (isNumber(left, 0)) return Rewrite(right, OptimizationRule.NEUTRAL_ELEMENT)
                if (isNumber(right, 0)) return Rewrite(left, OptimizationRule.NEUTRAL_ELEMENT)
                negated(right)?.let { return Rewrite(binary("-", left, it).expression, OptimizationRule.UNARY_SIGN) }
                negated(left)?.let { return Rewrite(binary("-", right, it).expression, OptimizationRule.UNARY_SIGN) }
            }
            "-" -> {
                if (isNumber(right, 0)) return Rewrite(left, OptimizationRule.NEUTRAL_ELEMENT)
                if (isNumber(left, 0)) return unary("-", right)
                if (left == right) return Rewrite(zero, OptimizationRule.EQUAL_OPERANDS)
                negated(right)?.let { return Rewrite(binary("+", left, it).expression, OptimizationRule.UNARY_SIGN) }
            }
            "*" -> {
                if (isNumber(left, 1)) return Rewrite(right, OptimizationRule.NEUTRAL_ELEMENT)
                if (isNumber(right, 1)) return Rewrite(left, OptimizationRule.NEUTRAL_ELEMENT)
                if (isNumber(left, -1)) return unary("-", right)
                if (isNumber(right, -1)) return unary("-", left)
                if (isNumber(left, 0) || isNumber(right, 0)) return Rewrite(zero, OptimizationRule.ZERO_PRODUCT)
            }
            "/" -> {
                if (isNumber(right, 1)) return Rewrite(left, OptimizationRule.NEUTRAL_ELEMENT)
                if (isNumber(right, -1)) return unary("-", left)
                if (!isNumber(right, 0)) {
                    if (isNumber(left, 0)) return Rewrite(zero, OptimizationRule.ZERO_DIVIDEND)
                    if (left == right) return Rewrite(one, OptimizationRule.EQUAL_OPERANDS)
                }
            }
        }
        return Rewrite(Expression.Binary(operator, left, right))
    }

    private fun call(name: String, arguments: List<Expression>): Rewrite {
        ConstantFolding.call(name, arguments)?.let { return Rewrite(it, OptimizationRule.CONSTANT_FOLDING) }
        if (name in listOf("min", "max") && arguments[0] == arguments[1]) return Rewrite(arguments[0], OptimizationRule.FUNCTION_SIMPLIFICATION)
        if (name == "pow" && isNumber(arguments[1], 1)) return Rewrite(arguments[0], OptimizationRule.FUNCTION_SIMPLIFICATION)
        val operand = arguments[0]
        if (name == "abs" && operand is Expression.Call && operand.name == "abs") return Rewrite(operand, OptimizationRule.FUNCTION_SIMPLIFICATION)
        return Rewrite(Expression.Call(name, arguments))
    }

    private fun negated(expression: Expression): Expression? {
        ConstantFolding.number(expression)?.let { return if (it.signum() < 0) ConstantFolding.value(it.negate()) else null }
        return (expression as? Expression.Unary)?.takeIf { it.operator == "-" }?.operand
    }

    private fun isNumber(expression: Expression, value: Int): Boolean =
        ConstantFolding.number(expression)?.compareTo(BigDecimal.valueOf(value.toLong())) == 0
}
