package com.danylom73.equivalence

import com.danylom73.expression.Expression

sealed interface Form {
    data class Atom(val text: String) : Form
    data class Call(val name: String, val arguments: List<Form>) : Form
    data class Group(val kind: GroupKind, val operands: List<Operand>) : Form
}

enum class GroupKind(val direct: String, val inverse: String) {
    SUM("+", "-"),
    PRODUCT("*", "/"),
}

data class Operand(val form: Form, val inverse: Boolean = false)

object Forms {
    fun from(expression: Expression): Form = when (expression) {
        is Expression.Value -> Form.Atom(expression.text)
        is Expression.Call -> Form.Call(expression.name, expression.arguments.map(::from))
        is Expression.Unary -> if (expression.operator == "+") from(expression.operand) else group(GroupKind.SUM, expression)
        is Expression.Binary -> group(kindOf(expression.operator), expression)
    }

    private fun group(kind: GroupKind, root: Expression): Form.Group {
        val operands = mutableListOf<Operand>()
        fun collect(node: Expression, inverse: Boolean) {
            when {
                node is Expression.Binary && kindOf(node.operator) == kind -> {
                    collect(node.left, inverse)
                    operands += Operand(from(node.right), inverse != (node.operator == kind.inverse))
                }
                kind == GroupKind.SUM && node is Expression.Unary && node.operator == "-" ->
                    operands += Operand(from(node.operand), !inverse)
                else -> operands += Operand(from(node), inverse)
            }
        }
        collect(root, false)
        return Form.Group(kind, operands)
    }

    fun canonical(form: Form): Form = when (form) {
        is Form.Atom -> form
        is Form.Call -> form.copy(arguments = form.arguments.map(::canonical))
        is Form.Group -> {
            val operands = form.operands.flatMap { operand ->
                val inner = canonical(operand.form)
                if (inner is Form.Group && inner.kind == form.kind) {
                    inner.operands.map { it.copy(inverse = it.inverse != operand.inverse) }
                } else {
                    listOf(operand.copy(form = inner))
                }
            }
            operands.singleOrNull()?.takeIf { !it.inverse }?.form
                ?: Form.Group(form.kind, operands.sortedBy(Operand::toString))
        }
    }

    private fun kindOf(operator: String): GroupKind = when (operator) {
        "+", "-" -> GroupKind.SUM
        "*", "/" -> GroupKind.PRODUCT
        else -> error("Невідома операція: $operator")
    }
}

object FormFormatter {
    fun format(form: Form): String = format(form, leading = true)

    private fun format(form: Form, leading: Boolean): String = when (form) {
        is Form.Atom -> form.text
        is Form.Call -> "${form.name}(${form.arguments.joinToString(", ") { format(it, true) }})"
        is Form.Group -> if (form.kind == GroupKind.SUM) sum(form) else product(form, leading)
    }

    private fun sum(group: Form.Group): String = buildString {
        group.operands.forEachIndexed { index, operand ->
            val form = operand.form
            when {
                index > 0 -> append(if (operand.inverse) " - " else " + ").append(term(form, leading = false))
                operand.inverse -> append('-').append(if (form is Form.Group) "(${format(form)})" else format(form))
                else -> append(term(form, leading = true))
            }
        }
    }

    private fun term(form: Form, leading: Boolean): String =
        if (form is Form.Group && form.kind == GroupKind.SUM) "(${format(form)})" else format(form, leading)

    private fun product(group: Form.Group, leading: Boolean): String = buildString {
        group.operands.forEachIndexed { index, operand ->
            when {
                index > 0 -> append(if (operand.inverse) " / " else " * ")
                operand.inverse -> append("1 / ")
            }
            append(factor(operand.form, leading && index == 0 && !operand.inverse))
        }
    }

    private fun factor(form: Form, leading: Boolean): String = when {
        form !is Form.Group -> format(form, leading)
        leading && form.kind == GroupKind.SUM && form.operands.singleOrNull()?.inverse == true -> format(form)
        else -> "(${format(form)})"
    }
}
