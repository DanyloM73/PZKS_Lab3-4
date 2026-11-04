package com.danylom73.parallel

import com.danylom73.expression.Expression
import java.util.PriorityQueue

class ParallelExpressionOptimizer {
    private data class SignedTerm(val expression: Expression, val positive: Boolean)
    private data class Candidate(
        val expression: Expression,
        val positive: Boolean,
        val height: Int,
        val order: Int,
        val position: Int,
    )
    private data class Prepared(val expression: Expression, val steps: List<OptimizationStep>)

    fun optimize(expression: Expression): Expression = optimizeInternal(expression, false).optimized

    fun optimizeWithTrace(expression: Expression): OptimizationResult = optimizeInternal(expression, true)

    private fun optimizeInternal(expression: Expression, includeTrace: Boolean): OptimizationResult {
        fun prepare(source: Prepared): Prepared {
            val trace = if (includeTrace) OptimizationTrace(source.expression) else null
            val simplifier = AlgebraicSimplifier(trace)
            val balanced = balanceExpression(simplifier.simplify(source.expression), trace)
            return Prepared(simplifier.simplify(balanced), source.steps + (trace?.steps ?: emptyList()))
        }

        var best = Prepared(expression, emptyList())
        var bestPlan = ParallelPlan.from(best.expression)
        fun consider(candidate: Prepared, acceptTie: Boolean = false): Boolean {
            val candidatePlan = ParallelPlan.from(candidate.expression)
            if (candidatePlan.depth > bestPlan.depth || candidatePlan.operations.size > bestPlan.operations.size) return false
            if (!acceptTie && candidatePlan.depth == bestPlan.depth && candidatePlan.operations.size == bestPlan.operations.size) return false
            best = candidate
            bestPlan = candidatePlan
            return true
        }

        consider(prepare(best), acceptTie = true)
        while (consider(prepare(best))) Unit
        return OptimizationResult(expression, best.expression, best.steps)
    }

    private fun balanceExpression(
        expression: Expression,
        trace: OptimizationTrace? = null,
        path: List<Int> = emptyList(),
    ): Expression = when (expression) {
        is Expression.Value -> expression
        is Expression.Unary -> when (expression.operator) {
            "+" -> balanceExpression(expression.operand, trace, path + 0)
            else -> Expression.Unary(expression.operator, balanceExpression(expression.operand, trace, path + 0))
        }
        is Expression.Call -> Expression.Call(expression.name, expression.arguments.mapIndexed { index, argument ->
            balanceExpression(argument, trace, path + index)
        })
        is Expression.Binary -> when (expression.operator) {
            "+", "-" -> {
                val terms = mutableListOf<SignedTerm>()
                collectAdditive(expression, true, terms, trace, path)
                balance(expression, terms, additive = true, trace, path)
            }
            "*", "/" -> {
                val factors = mutableListOf<SignedTerm>()
                collectMultiplicative(expression, true, factors, trace, path)
                balance(expression, factors, additive = false, trace, path)
            }
            else -> error("Невідома операція: ${expression.operator}")
        }
    }

    private fun collectAdditive(
        node: Expression, positive: Boolean, output: MutableList<SignedTerm>, trace: OptimizationTrace?, path: List<Int>,
    ) {
        when (node) {
            is Expression.Binary -> when (node.operator) {
                "+" -> {
                    collectAdditive(node.left, positive, output, trace, path + 0)
                    collectAdditive(node.right, positive, output, trace, path + 1)
                }
                "-" -> {
                    collectAdditive(node.left, positive, output, trace, path + 0)
                    collectAdditive(node.right, !positive, output, trace, path + 1)
                }
                else -> output += SignedTerm(balanceExpression(node, trace, path), positive)
            }
            is Expression.Unary -> when (node.operator) {
                "+" -> collectAdditive(node.operand, positive, output, trace, path + 0)
                "-" -> collectAdditive(node.operand, !positive, output, trace, path + 0)
                else -> output += SignedTerm(balanceExpression(node, trace, path), positive)
            }
            else -> output += SignedTerm(balanceExpression(node, trace, path), positive)
        }
    }

    private fun collectMultiplicative(
        node: Expression, positive: Boolean, output: MutableList<SignedTerm>, trace: OptimizationTrace?, path: List<Int>,
    ) {
        when (node) {
            is Expression.Binary -> when (node.operator) {
                "*" -> {
                    collectMultiplicative(node.left, positive, output, trace, path + 0)
                    collectMultiplicative(node.right, positive, output, trace, path + 1)
                }
                "/" -> if (positive) {
                    collectMultiplicative(node.left, true, output, trace, path + 0)
                    collectMultiplicative(node.right, false, output, trace, path + 1)
                } else {
                    output += SignedTerm(balanceExpression(node, trace, path), false)
                }
                else -> output += SignedTerm(balanceExpression(node, trace, path), positive)
            }
            else -> output += SignedTerm(balanceExpression(node, trace, path), positive)
        }
    }

    private fun rebuild(node: Expression, additive: Boolean, positive: Boolean, terms: Iterator<SignedTerm>): Expression = when {
        additive && node is Expression.Binary && node.operator in listOf("+", "-") ->
            node.copy(left = rebuild(node.left, true, positive, terms), right = rebuild(node.right, true, positive, terms))
        additive && node is Expression.Unary && node.operator in listOf("+", "-") ->
            node.copy(operand = rebuild(node.operand, true, positive, terms))
        !additive && node is Expression.Binary && node.operator == "*" ->
            node.copy(left = rebuild(node.left, false, positive, terms), right = rebuild(node.right, false, positive, terms))
        !additive && node is Expression.Binary && node.operator == "/" && positive ->
            node.copy(left = rebuild(node.left, false, true, terms), right = rebuild(node.right, false, false, terms))
        else -> terms.next().expression
    }

    private fun balance(
        group: Expression, terms: List<SignedTerm>, additive: Boolean, trace: OptimizationTrace?, path: List<Int>,
    ): Expression {
        require(terms.isNotEmpty())
        val sameSign = if (additive) "+" else "*"
        val mixedSign = if (additive) "-" else "/"
        val folded = combineConstants(terms, sameSign, mixedSign)
        val prepared = if (additive) subtractNegativeConstants(folded) else folded

        val queue = PriorityQueue(compareBy(Candidate::height, Candidate::order))
        var nextOrder = 0
        prepared.forEachIndexed { index, term ->
            queue += Candidate(term.expression, term.positive, height(term.expression), nextOrder++, index)
        }
        while (queue.size > 1) {
            val first = queue.remove()
            val second = queue.remove()
            val (left, right) = when {
                first.positive == second.positive -> if (first.position < second.position) first to second else second to first
                first.positive -> first to second
                else -> second to first
            }
            val operator = if (first.positive == second.positive) sameSign else mixedSign
            queue += Candidate(
                Expression.Binary(operator, left.expression, right.expression),
                first.positive || second.positive,
                maxOf(first.height, second.height) + 1,
                nextOrder++,
                minOf(first.position, second.position),
            )
        }

        val result = queue.remove()
        val balanced = if (result.positive) result.expression else Expression.Unary("-", result.expression)
        if (folded.size == terms.size && prepared == folded) {
            val written = rebuild(group, additive, true, terms.iterator())
            if (!improves(ParallelPlan.from(balanced), ParallelPlan.from(written))) return written
        }
        val rule = if (folded.size < terms.size) OptimizationRule.CONSTANT_GROUP else OptimizationRule.BALANCING
        trace?.record(rule, path, balanced)
        return balanced
    }

    private fun combineConstants(terms: List<SignedTerm>, sameSign: String, mixedSign: String): List<SignedTerm> {
        val result = terms.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in result.indices) {
                if (ConstantFolding.number(result[i].expression) == null) continue
                for (j in i + 1 until result.size) {
                    val first = result[i]
                    val second = result[j]
                    val folded = when {
                        first.positive == second.positive -> ConstantFolding.binary(sameSign, first.expression, second.expression)
                        first.positive -> ConstantFolding.binary(mixedSign, first.expression, second.expression)
                        else -> ConstantFolding.binary(mixedSign, second.expression, first.expression)
                    } ?: continue
                    result[i] = SignedTerm(folded, first.positive || second.positive)
                    result.removeAt(j)
                    changed = true
                    break@outer
                }
            }
        }
        return result
    }

    private fun subtractNegativeConstants(terms: List<SignedTerm>): List<SignedTerm> {
        val flipped = terms.map { term ->
            val number = ConstantFolding.number(term.expression)
            if (number != null && number.signum() < 0) SignedTerm(ConstantFolding.value(number.negate()), !term.positive) else term
        }
        return if (flipped.any(SignedTerm::positive)) flipped else terms
    }

    private fun height(node: Expression): Int = when (node) {
        is Expression.Value -> 0
        is Expression.Unary -> height(node.operand) + 1
        is Expression.Binary -> maxOf(height(node.left), height(node.right)) + 1
        is Expression.Call -> (node.arguments.maxOfOrNull(::height) ?: 0) + 1
    }

    private fun improves(candidate: ParallelPlan, current: ParallelPlan): Boolean = when {
        candidate.operations.size > current.operations.size -> false
        candidate.depth != current.depth -> candidate.depth < current.depth
        candidate.operations.size != current.operations.size -> true
        else -> candidate.maximumWidth > current.maximumWidth
    }
}
