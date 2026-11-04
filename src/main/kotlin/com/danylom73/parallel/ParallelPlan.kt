package com.danylom73.parallel

import com.danylom73.expression.Expression
import java.util.IdentityHashMap

data class ScheduledOperation(
    val id: Int,
    val level: Int,
    val expression: Expression,
)

class ParallelPlan private constructor(
    val root: Expression,
    val operations: List<ScheduledOperation>,
    private val operationsByNode: IdentityHashMap<Expression, ScheduledOperation>,
) {
    val depth: Int = operations.maxOfOrNull(ScheduledOperation::level) ?: 0
    val maximumWidth: Int = operations.groupingBy(ScheduledOperation::level).eachCount().values.maxOrNull() ?: 0

    fun renderTree(): String = LevelTableRenderer.render(root)

    fun renderSchedule(): String = if (operations.isEmpty()) {
        "Операцій немає."
    } else buildString {
        operations.groupBy(ScheduledOperation::level).toSortedMap().forEach { (level, batch) ->
            append("Ярус ").append(level).append(" (паралельно: ").append(batch.size).append("):\n")
            batch.forEach { step ->
                append("  #").append(step.id).append(" = ")
                    .append(operationWithReferences(step.expression)).append('\n')
            }
        }
    }.trimEnd()

    private fun operationWithReferences(node: Expression): String = when (node) {
        is Expression.Value -> node.text
        is Expression.Unary -> node.operator + reference(node.operand)
        is Expression.Binary -> "${reference(node.left)} ${node.operator} ${reference(node.right)}"
        is Expression.Call -> "${node.name}(${node.arguments.joinToString(", ") { reference(it) }})"
    }

    private fun reference(node: Expression): String = operationsByNode[node]?.let { "#${it.id}" }
        ?: (node as Expression.Value).text

    companion object {
        fun from(root: Expression): ParallelPlan {
            val operations = mutableListOf<ScheduledOperation>()
            val operationsByNode = IdentityHashMap<Expression, ScheduledOperation>()
            val levels = IdentityHashMap<Expression, Int>()

            fun visit(node: Expression): Int {
                levels[node]?.let { return it }
                val children = children(node)
                if (children.isEmpty()) return 0
                val level = children.maxOf(::visit) + 1
                levels[node] = level
                operations += ScheduledOperation(0, level, node)
                return level
            }

            visit(root)
            val ordered = operations.sortedBy(ScheduledOperation::level)
                .mapIndexed { index, operation -> operation.copy(id = index + 1) }
            ordered.forEach { operationsByNode[it.expression] = it }
            return ParallelPlan(root, ordered, operationsByNode)
        }

        private fun children(node: Expression): List<Expression> = when (node) {
            is Expression.Value -> emptyList()
            is Expression.Unary -> listOf(node.operand)
            is Expression.Binary -> listOf(node.left, node.right)
            is Expression.Call -> node.arguments
        }
    }
}
