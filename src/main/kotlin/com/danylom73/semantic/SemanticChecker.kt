package com.danylom73.semantic

import com.danylom73.analysis.AnalysisError
import com.danylom73.analysis.ErrorCategory
import com.danylom73.analysis.ErrorCode
import com.danylom73.expression.Expression
import com.danylom73.expression.SourceSpan
import com.danylom73.parallel.ConstantFolding
import com.danylom73.parallel.ParallelExpressionOptimizer
import java.math.BigDecimal

object SemanticChecker {
    fun check(source: String, expression: Expression, spans: Map<Expression, SourceSpan>): List<AnalysisError> {
        val optimizer = ParallelExpressionOptimizer()
        val errors = mutableListOf<AnalysisError>()

        fun text(node: Expression): String = spans.getValue(node).let { source.substring(it.start.offset, it.endOffset) }

        fun constant(node: Expression): BigDecimal? = ConstantFolding.number(optimizer.optimize(node))

        fun origin(role: String, node: Expression, value: BigDecimal): String? {
            val written = text(node)
            if (written.filterNot { it.isWhitespace() || it == '(' || it == ')' }.toBigDecimalOrNull() != null) return null
            return "$role «$written» спрощується до ${value.stripTrailingZeros().toPlainString()}"
        }

        fun report(node: Expression, code: ErrorCode, problem: String, details: List<String?> = emptyList()) {
            val span = spans.getValue(node)
            val explanation = details.filterNotNull().joinToString("; ").let { if (it.isEmpty()) "" else ": $it" }
            errors += AnalysisError(
                category = ErrorCategory.SEMANTIC,
                code = code,
                message = "$problem у підвиразі «${text(node)}»$explanation.",
                offset = span.start.offset,
                line = span.start.line,
                column = span.start.column,
                length = span.length,
            )
        }

        fun checkCall(call: Expression.Call) {
            val argument = call.arguments[0]
            fun reportArgument(problem: String, value: BigDecimal) =
                report(call, ErrorCode.INVALID_FUNCTION_ARGUMENT, problem, listOf(origin("аргумент", argument, value)))

            when (call.name) {
                "sqrt" -> constant(argument)?.takeIf { it.signum() < 0 }
                    ?.let { reportArgument("Корінь з від'ємного числа", it) }
                "ln", "log10" -> constant(argument)?.takeIf { it.signum() <= 0 }
                    ?.let { reportArgument("Логарифм недодатного числа", it) }
                "ctg" -> constant(argument)?.takeIf { it.signum() == 0 }
                    ?.let { reportArgument("Котангенс нуля не визначений", it) }
                "pow" -> {
                    val exponentNode = call.arguments[1]
                    val base = constant(argument) ?: return
                    val exponent = constant(exponentNode) ?: return
                    val problem = when {
                        base.signum() == 0 && exponent.signum() < 0 -> "Нуль у від'ємному степені (ділення на нуль)"
                        base.signum() < 0 && exponent.stripTrailingZeros().scale() > 0 ->
                            "Від'ємна основа з дробовим показником не має дійсного значення"
                        else -> return
                    }
                    report(call, ErrorCode.INVALID_FUNCTION_ARGUMENT, problem,
                        listOf(origin("основа", argument, base), origin("показник", exponentNode, exponent)))
                }
            }
        }

        fun visit(node: Expression) {
            when (node) {
                is Expression.Value -> Unit
                is Expression.Unary -> visit(node.operand)
                is Expression.Binary -> {
                    visit(node.left)
                    visit(node.right)
                    if (node.operator == "/") {
                        constant(node.right)?.takeIf { it.signum() == 0 }?.let {
                            report(node, ErrorCode.DIVISION_BY_ZERO, "Ділення на нуль", listOf(origin("знаменник", node.right, it)))
                        }
                    }
                }
                is Expression.Call -> {
                    node.arguments.forEach(::visit)
                    checkCall(node)
                }
            }
        }

        visit(expression)
        return errors.sortedBy(AnalysisError::offset)
    }
}
