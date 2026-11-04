package com.danylom73.equivalence

import com.danylom73.parallel.ParallelPlan
import java.math.BigInteger
import java.util.PriorityQueue

object CommutativeLaw {
    private class OperandClass(val representative: Operand, val size: Int)

    private class Branch(val operands: List<Operand>, val height: Int, val position: Int) {
        val direct: Boolean
            get() = !operands.first().inverse
    }

    data class GroupArrangements(val group: Form.Group, val arrangements: BigInteger)

    fun arrange(form: Form): Form = when (form) {
        is Form.Atom -> form
        is Form.Call -> form.copy(arguments = form.arguments.map(::arrange))
        is Form.Group -> {
            val written = form.copy(operands = form.operands.map { it.copy(form = arrange(it.form)) })
            val sorted = written.copy(operands = byWeight(written.operands))
            if (improves(sorted, written)) sorted else written
        }
    }

    fun variants(form: Form, limit: Int): Sequence<Form> = when (form) {
        is Form.Atom -> sequenceOf(form)
        is Form.Call -> combinations(form.arguments.map { variants(it, limit).take(limit).toList() })
            .map { form.copy(arguments = it) }
        is Form.Group -> {
            val classes = classes(form)
            val options = classes.map { variants(it.representative.form, limit).take(limit).toList() }
            arrangements(classes).flatMap { arrangement ->
                combinations(arrangement.map(options::get)).map { forms ->
                    Form.Group(form.kind, forms.mapIndexed { position, variant ->
                        Operand(variant, classes[arrangement[position]].representative.inverse)
                    })
                }
            }
        }
    }

    fun count(form: Form): BigInteger = when (form) {
        is Form.Atom -> BigInteger.ONE
        is Form.Call -> form.arguments.fold(BigInteger.ONE) { product, argument -> product * count(argument) }
        is Form.Group -> {
            val classes = classes(form)
            classes.fold(arrangementCount(classes)) { product, operandClass ->
                product * count(operandClass.representative.form).pow(operandClass.size)
            }
        }
    }

    fun groups(form: Form): List<GroupArrangements> = when (form) {
        is Form.Atom -> emptyList()
        is Form.Call -> form.arguments.flatMap(::groups)
        is Form.Group -> listOf(GroupArrangements(form, arrangementCount(classes(form)))) +
            form.operands.flatMap { groups(it.form) }
    }

    fun isArranged(form: Form): Boolean = when (form) {
        is Form.Atom -> true
        is Form.Call -> form.arguments.all(::isArranged)
        is Form.Group -> (!form.operands.first().inverse || form.operands.all(Operand::inverse)) &&
            form.operands.all { isArranged(it.form) }
    }

    private fun classes(group: Form.Group): List<OperandClass> = group.operands
        .groupBy { Operand(canonical(it.form), it.inverse) }
        .values
        .map { OperandClass(it.first(), it.size) }

    private fun canonical(form: Form): Form = when (form) {
        is Form.Atom -> form
        is Form.Call -> form.copy(arguments = form.arguments.map(::canonical))
        is Form.Group -> form.copy(operands = form.operands.map { it.copy(form = canonical(it.form)) }.sortedBy(Operand::toString))
    }

    private fun arrangements(classes: List<OperandClass>): Sequence<List<Int>> = sequence {
        val remaining = classes.map(OperandClass::size).toIntArray()
        arrange(classes, remaining, mutableListOf(), remaining.sum())
    }

    private suspend fun SequenceScope<List<Int>>.arrange(
        classes: List<OperandClass>, remaining: IntArray, current: MutableList<Int>, size: Int,
    ) {
        if (current.size == size) {
            yield(current.toList())
            return
        }
        for (index in classes.indices) {
            if (remaining[index] == 0 || current.isEmpty() && !canStart(classes, index)) continue
            remaining[index]--
            current += index
            arrange(classes, remaining, current, size)
            current.removeAt(current.lastIndex)
            remaining[index]++
        }
    }

    private fun canStart(classes: List<OperandClass>, index: Int): Boolean =
        !classes[index].representative.inverse || classes.all { it.representative.inverse }

    private fun arrangementCount(classes: List<OperandClass>): BigInteger {
        val sizes = classes.map(OperandClass::size)
        return classes.indices.filter { canStart(classes, it) }.fold(BigInteger.ZERO) { sum, first ->
            sum + multinomial(sizes.mapIndexed { index, size -> if (index == first) size - 1 else size })
        }
    }

    private fun multinomial(sizes: List<Int>): BigInteger =
        sizes.fold(factorial(sizes.sum())) { result, size -> result / factorial(size) }

    private fun factorial(value: Int): BigInteger =
        (2..value).fold(BigInteger.ONE) { result, factor -> result * factor.toBigInteger() }

    private fun byWeight(operands: List<Operand>): List<Operand> {
        val weighted = operands.map { it to ParallelTreeBuilder.height(it.form) }
            .sortedWith(compareByDescending<Pair<Operand, Int>> { it.second }.thenBy { it.first.inverse })
        val sorted = weighted.map { it.first }
        if (!sorted.first().inverse || sorted.all(Operand::inverse)) return sorted

        val queue = PriorityQueue(compareBy(Branch::height).thenByDescending(Branch::position))
        weighted.forEachIndexed { position, (operand, height) -> queue += Branch(listOf(operand), height, position) }
        while (queue.size > 1) {
            val (left, right) = listOf(queue.remove(), queue.remove()).sortedWith(compareBy({ !it.direct }, { it.position }))
            queue += Branch(left.operands + right.operands, maxOf(left.height, right.height) + 1, minOf(left.position, right.position))
        }
        return queue.remove().operands
    }

    private fun improves(candidate: Form, current: Form): Boolean {
        val after = ParallelPlan.from(ParallelTreeBuilder.build(candidate))
        val before = ParallelPlan.from(ParallelTreeBuilder.build(current))
        return after.depth < before.depth || after.depth == before.depth && after.operations.size < before.operations.size
    }

    private fun combinations(options: List<List<Form>>): Sequence<List<Form>> =
        options.fold(sequenceOf(emptyList())) { prefixes, choices ->
            prefixes.flatMap { prefix -> choices.asSequence().map { prefix + it } }
        }
}
