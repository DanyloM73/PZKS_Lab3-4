package com.danylom73.equivalence

import com.danylom73.expression.Expression
import kotlin.math.abs

object ParallelTreeBuilder {
    private class Part(val expression: Expression, val inverse: Boolean, val height: Int)

    fun build(form: Form): Expression = when (form) {
        is Form.Atom -> Expression.Value(form.text)
        is Form.Call -> Expression.Call(form.name, form.arguments.map(::build))
        is Form.Group -> group(form)
    }

    fun height(form: Form): Int = height(build(form))

    private fun group(group: Form.Group): Expression {
        val parts = group.operands.map { operand ->
            build(operand.form).let { Part(it, operand.inverse, height(it)) }
        }
        val count = parts.size
        val best = Array(count) { index -> arrayOfNulls<Part>(count).also { it[index] = parts[index] } }
        for (length in 2..count) {
            for (start in 0..count - length) {
                val end = start + length - 1
                best[start][end] = (start until end)
                    .sortedBy { split -> abs((split - start + 1) - (end - split)) }
                    .map { split -> merge(group.kind, best[start][split]!!, best[split + 1][end]!!) }
                    .minBy(Part::height)
            }
        }
        val result = best[0][count - 1]!!
        return when {
            !result.inverse -> result.expression
            group.kind == GroupKind.SUM -> Expression.Unary("-", result.expression)
            else -> Expression.Binary("/", Expression.Value("1"), result.expression)
        }
    }

    private fun merge(kind: GroupKind, left: Part, right: Part): Part {
        val operator = if (left.inverse == right.inverse) kind.direct else kind.inverse
        return Part(Expression.Binary(operator, left.expression, right.expression), left.inverse, maxOf(left.height, right.height) + 1)
    }

    private fun height(node: Expression): Int = when (node) {
        is Expression.Value -> 0
        is Expression.Unary -> height(node.operand) + 1
        is Expression.Binary -> maxOf(height(node.left), height(node.right)) + 1
        is Expression.Call -> (node.arguments.maxOfOrNull(::height) ?: 0) + 1
    }
}
