package com.danylom73.parallel

import com.danylom73.expression.Expression
import com.danylom73.expression.ExpressionFormatter

enum class OptimizationRule(val description: String) {
    CONSTANT_FORMAT("Уніфікація запису константи"),
    CONSTANT_FOLDING("Точне обчислення константного підвиразу"),
    NEUTRAL_ELEMENT("Прибирання нейтрального елемента"),
    UNARY_SIGN("Спрощення унарних знаків"),
    ZERO_PRODUCT("Множення на нуль"),
    ZERO_DIVIDEND("Ділення нуля на вираз"),
    EQUAL_OPERANDS("Спрощення однакових операндів"),
    FUNCTION_SIMPLIFICATION("Спрощення функції"),
    CONSTANT_GROUP("Об'єднання констант і балансування групи"),
    BALANCING("Перегрупування та балансування операцій"),
}

data class OptimizationStep(
    val rule: OptimizationRule,
    val before: Expression,
    val after: Expression,
    val expression: Expression,
)

data class OptimizationResult(
    val original: Expression,
    val optimized: Expression,
    val steps: List<OptimizationStep>,
) {
    fun renderSteps(): String = buildString {
        append("Початковий вираз: ").append(ExpressionFormatter.format(original)).append('\n')
        if (steps.isEmpty()) append("Перетворень не застосовано.\n")
        steps.forEachIndexed { index, step ->
            append(index + 1).append(". ").append(step.rule.description).append('\n')
            append("   Підвираз: ").append(ExpressionFormatter.format(step.before))
                .append(" → ").append(ExpressionFormatter.format(step.after)).append('\n')
            append("   Вираз: ").append(ExpressionFormatter.format(step.expression)).append('\n')
        }
    }.trimEnd()
}

internal class OptimizationTrace(original: Expression) {
    private var current = original
    val steps = mutableListOf<OptimizationStep>()

    fun record(rule: OptimizationRule, path: List<Int>, after: Expression) {
        fun replace(node: Expression, depth: Int): Expression {
            if (depth == path.size) return after
            val child = path[depth]
            return when (node) {
                is Expression.Value -> error("A value has no children")
                is Expression.Unary -> node.copy(operand = replace(node.operand, depth + 1))
                is Expression.Binary -> if (child == 0) node.copy(left = replace(node.left, depth + 1))
                    else node.copy(right = replace(node.right, depth + 1))
                is Expression.Call -> node.copy(arguments = node.arguments.mapIndexed { index, argument ->
                    if (index == child) replace(argument, depth + 1) else argument
                })
            }
        }

        var before = current
        path.forEach { child ->
            before = when (val node = before) {
                is Expression.Value -> error("A value has no children")
                is Expression.Unary -> node.operand
                is Expression.Binary -> if (child == 0) node.left else node.right
                is Expression.Call -> node.arguments[child]
            }
        }
        if (before == after) return
        current = replace(current, 0)
        steps += OptimizationStep(rule, before, after, current)
    }
}
