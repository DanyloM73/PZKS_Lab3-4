package com.danylom73.expression

sealed interface Expression {
    data class Value(val text: String) : Expression
    data class Unary(val operator: String, val operand: Expression) : Expression
    data class Binary(val operator: String, val left: Expression, val right: Expression) : Expression
    data class Call(val name: String, val arguments: List<Expression>) : Expression
}

object ExpressionFormatter {
    fun format(expression: Expression): String = when (expression) {
        is Expression.Value -> expression.text
        is Expression.Unary -> "(${expression.operator}${format(expression.operand)})"
        is Expression.Binary -> "(${format(expression.left)} ${expression.operator} ${format(expression.right)})"
        is Expression.Call -> "${expression.name}(${expression.arguments.joinToString(", ") { format(it) }})"
    }
}
