package com.danylom73.equivalence

enum class DistributiveRule(val description: String) {
    FACTOR_OUT("винесення спільного множника за дужки"),
    FACTOR_OUT_DIVISOR("винесення спільного дільника за дужки"),
    EXPAND("розкриття дужок"),
    EXPAND_DIVISORS("множення дільників із розкриттям дужок"),
}

object DistributiveLaw {
    data class Rewrite(val form: Form, val rule: DistributiveRule, val before: Form, val after: Form)

    private const val MAX_SUBSET_SOURCE = 5
    private val one = Form.Atom("1")

    fun rewrites(form: Form): List<Rewrite> = rewrites(form, isTerm = false)

    private fun rewrites(form: Form, isTerm: Boolean): List<Rewrite> = when (form) {
        is Form.Atom -> emptyList()
        is Form.Call -> form.arguments.flatMapIndexed { index, argument ->
            rewrites(argument).map { it.copy(form = form.copy(arguments = form.arguments.replaced(index, it.form))) }
        }
        is Form.Group -> when {
            form.kind == GroupKind.SUM -> factorings(form) + termExpansions(form) + nested(form)
            isTerm -> divisorExpansions(form) + nested(form)
            else -> expansions(form).map { terms ->
                val sum = Form.Group(GroupKind.SUM, terms)
                Rewrite(sum, DistributiveRule.EXPAND, form, sum)
            } + divisorExpansions(form) + nested(form)
        }
    }

    private fun nested(group: Form.Group): List<Rewrite> = group.operands.flatMapIndexed { index, operand ->
        rewrites(operand.form, isTerm = group.kind == GroupKind.SUM).map { rewrite ->
            rewrite.copy(form = group.copy(operands = group.operands.replacedWith(index, adopt(group.kind, operand, rewrite.form))))
        }
    }

    private fun adopt(kind: GroupKind, operand: Operand, form: Form): List<Operand> {
        val group = form as? Form.Group
        val single = group?.takeIf { it.kind == GroupKind.SUM }?.operands?.singleOrNull()
        return when {
            kind == GroupKind.SUM && single != null -> listOf(Operand(single.form, operand.inverse != single.inverse))
            kind == GroupKind.PRODUCT && group?.kind == GroupKind.PRODUCT && !operand.inverse -> group.operands
            else -> listOf(operand.copy(form = form))
        }
    }

    private fun factorings(sum: Form.Group): List<Rewrite> {
        val factors = sum.operands.map { factorsOf(it.form) }
        return factors.flatten().distinct().filter { it.form != one }.flatMap { factor ->
            val holders = sum.operands.indices.filter { factor in factors[it] }
            subsets(holders).map { factorOut(sum, factors, factor, it) }
        }
    }

    private fun factorOut(sum: Form.Group, factors: List<List<Operand>>, factor: Operand, terms: List<Int>): Rewrite {
        val lead = terms.firstOrNull { !sum.operands[it].inverse } ?: terms.first()
        val ordered = listOf(lead) + (terms - lead)
        val sign = sum.operands[lead].inverse
        val inner = Form.Group(GroupKind.SUM, ordered.flatMap { index ->
            val rest = product(factors[index].toMutableList().apply { removeAt(indexOf(factor)) })
            val inverse = sum.operands[index].inverse != sign
            if (rest is Form.Group && rest.kind == GroupKind.SUM) {
                rest.operands.map { it.copy(inverse = it.inverse != inverse) }
            } else {
                listOf(Operand(rest, inverse))
            }
        })
        val factored = when {
            factor.inverse -> Form.Group(GroupKind.PRODUCT, listOf(Operand(inner), factor))
            factors[lead].indexOf(factor) == 0 -> Form.Group(GroupKind.PRODUCT, listOf(factor, Operand(inner)))
            else -> Form.Group(GroupKind.PRODUCT, listOf(Operand(inner), factor))
        }
        val first = terms.first()
        val operands = sum.operands.toMutableList()
        operands[first] = Operand(factored, sign)
        terms.drop(1).sortedDescending().forEach { operands.removeAt(it) }
        val result = if (operands.size == 1 && !sign) factored else Form.Group(GroupKind.SUM, operands)
        val before = Form.Group(GroupKind.SUM, ordered.map { sum.operands[it].let { term -> term.copy(inverse = term.inverse != sign) } })
        val rule = if (factor.inverse) DistributiveRule.FACTOR_OUT_DIVISOR else DistributiveRule.FACTOR_OUT
        return Rewrite(result, rule, before, factored)
    }

    private fun termExpansions(sum: Form.Group): List<Rewrite> = sum.operands.flatMapIndexed { index, term ->
        val product = (term.form as? Form.Group)?.takeIf { it.kind == GroupKind.PRODUCT } ?: return@flatMapIndexed emptyList()
        expansions(product).map { terms ->
            val spliced = terms.map { it.copy(inverse = it.inverse != term.inverse) }
            Rewrite(sum.copy(operands = sum.operands.replacedWith(index, spliced)), DistributiveRule.EXPAND,
                product, Form.Group(GroupKind.SUM, terms))
        }
    }

    private fun divisorExpansions(product: Form.Group): List<Rewrite> {
        val divisors = product.operands.filter(Operand::inverse)
        if (divisors.size < 2) return emptyList()
        val divisor = Form.Group(GroupKind.PRODUCT, divisors.map { it.copy(inverse = false) })
        val factors = product.operands.filterNot(Operand::inverse)
        return expansions(divisor).map { terms ->
            val sum = Form.Group(GroupKind.SUM, terms)
            Rewrite(product(factors + Operand(sum, inverse = true)), DistributiveRule.EXPAND_DIVISORS, divisor, sum)
        }
    }

    private fun expansions(product: Form.Group): List<List<Operand>> = product.operands.indices.mapNotNull { index ->
        val operand = product.operands[index]
        val sum = (operand.form as? Form.Group)
            ?.takeIf { !operand.inverse && it.kind == GroupKind.SUM && it.operands.size > 1 }
            ?: return@mapNotNull null
        sum.operands.map { term -> Operand(product(product.operands.replacedWith(index, factorsOf(term.form))), term.inverse) }
    }

    private fun factorsOf(form: Form): List<Operand> =
        if (form is Form.Group && form.kind == GroupKind.PRODUCT) form.operands else listOf(Operand(form))

    private fun product(factors: List<Operand>): Form {
        val meaningful = factors.filterNot { !it.inverse && it.form == one }
        val leading = meaningful.firstOrNull { !it.inverse }
        val ordered = if (leading == null) listOf(Operand(one)) + meaningful else listOf(leading) + (meaningful - leading)
        return ordered.singleOrNull()?.form ?: Form.Group(GroupKind.PRODUCT, ordered)
    }

    private fun subsets(holders: List<Int>): List<List<Int>> = when {
        holders.size < 2 -> emptyList()
        holders.size > MAX_SUBSET_SOURCE -> listOf(holders)
        else -> (1 until (1 shl holders.size))
            .filter { Integer.bitCount(it) > 1 }
            .map { mask -> holders.filterIndexed { bit, _ -> mask and (1 shl bit) != 0 } }
            .sortedByDescending(List<Int>::size)
    }

    private fun <T> List<T>.replaced(index: Int, item: T): List<T> = toMutableList().also { it[index] = item }

    private fun <T> List<T>.replacedWith(index: Int, items: List<T>): List<T> = take(index) + items + drop(index + 1)
}
