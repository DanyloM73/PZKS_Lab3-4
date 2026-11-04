package com.danylom73.equivalence

import com.danylom73.cli.TerminalColors
import com.danylom73.expression.ExpressionFormatter
import java.math.BigInteger

internal object EquivalenceReport {
    private const val DISPLAY_LIMIT = 30

    fun print(result: EquivalenceResult) {
        val original = result.original
        println("Закон: ${result.law.title} (${result.law.statement}).")
        println("Вихідна форма: ${original.text}")
        if (result.law == AlgebraicLaw.COMMUTATIVE) printGroups(result)

        if (result.forms.size == 1) {
            val reason = when (result.law) {
                AlgebraicLaw.COMMUTATIVE -> "у виразі немає груп, де операнди можна переставити"
                AlgebraicLaw.DISTRIBUTIVE -> "немає ні спільних множників у доданках, ні добутків на суму"
            }
            println(TerminalColors.yellow("Інших еквівалентних форм немає: $reason."))
            printForm(original)
            return
        }

        println("Еквівалентних форм: ${totalText(result)}.")
        println(when (result.law) {
            AlgebraicLaw.COMMUTATIVE -> "Дерево кожної форми будується без перестановок: об'єднуються лише сусідні операнди групи."
            AlgebraicLaw.DISTRIBUTIVE -> "Дерево кожної форми будується з термами, відсортованими за вагою, " +
                "тому кількість ярусів не залежить від порядку запису доданків."
        })
        val shown = if (result.forms.size <= DISPLAY_LIMIT) {
            result.forms
        } else {
            (listOf(original) + result.ranked.filter { it !== original }.take(DISPLAY_LIMIT - 1)).sortedWith(EquivalenceResult.QUALITY)
        }
        println()
        printTable(shown, result)
        if (shown.size < result.forms.size) {
            println("Показано вихідну та ${shown.size - 1} найкращих з ${result.forms.size} отриманих форм; :all — показати всі.")
        }
        printDistribution(result)

        println()
        val best = result.best
        println(TerminalColors.bold("Найкраща форма №${best.id}: ${best.text}"))
        val sameCount = result.bestCount - 1
        if (sameCount > 0) {
            println("Ще $sameCount ${plural(sameCount, "форма має", "форми мають", "форм мають")} такі самі показники.")
        }
        if (best === original) {
            println("Вихідна форма вже найкраща серед отриманих.")
        } else {
            println("Порівняно з вихідною: ярусів ${original.depth} → ${best.depth}, операцій ${original.operations} → " +
                "${best.operations}, максимальна ширина ${original.maximumWidth} → ${best.maximumWidth}.")
        }
        printForm(best)
        println("\nКоманди: :tree N — дерево форми N, :all — усі форми.")
    }

    fun printAll(result: EquivalenceResult) {
        printTable(result.forms, result)
        printDistribution(result)
    }

    fun printForm(form: EquivalentForm) {
        val plan = form.plan()
        println("\nФорма №${form.id}: ${form.text}")
        form.origin?.let { println("Отримана ${originText(it)}.") }
        println("Паралельна форма: ${ExpressionFormatter.format(form.tree())}")
        println("Ярусів: ${form.depth}; операцій: ${form.operations}; максимальна ширина: ${form.maximumWidth}.")
        println("\nДерево паралельної форми:")
        if (plan.operations.isNotEmpty()) {
            println("Угорі операнди; рядок «ярус N» містить операції, які виконуються одночасно на N-му кроці.")
        }
        println(plan.renderTree())
        println("\nНайраніший розклад виконання за ярусами:")
        println(plan.renderSchedule())
    }

    private fun printGroups(result: EquivalenceResult) {
        val groups = CommutativeLaw.groups(result.original.form).filter { it.group.operands.size > 1 }
        if (groups.isEmpty()) return
        println("Групи операндів, які можна переставляти:")
        groups.forEach { (group, arrangements) ->
            val kind = if (group.kind == GroupKind.SUM) "доданки" else "множники"
            println("  • ${FormFormatter.format(group)} — $kind: ${group.operands.size}, перестановок: $arrangements")
        }
    }

    private fun printTable(forms: List<EquivalentForm>, result: EquivalenceResult) {
        val headers = listOf("№", "Ярусів", "Операцій", "Ширина")
        val rows = forms.map { listOf(it.id, it.depth, it.operations, it.maximumWidth).map(Int::toString) }
        val widths = headers.indices.map { column -> maxOf(headers[column].length, rows.maxOfOrNull { it[column].length } ?: 0) }
        fun line(cells: List<String>) = cells.mapIndexed { column, cell -> cell.padStart(widths[column]) }.joinToString("  ")
        val indent = " ".repeat(widths.sum() + 2 * widths.size)

        println(TerminalColors.bold(line(headers) + "  Форма"))
        forms.forEachIndexed { index, form ->
            val marks = buildList {
                if (form === result.original) add("вихідна")
                if (result.isBest(form)) add("найкраща")
            }
            val mark = if (marks.isEmpty()) "" else "  (${marks.joinToString(", ")})"
            val text = if ("найкраща" in marks) TerminalColors.green(form.text + mark) else form.text + mark
            println(line(rows[index]) + "  " + text)
            form.origin?.let { println(indent + "↳ " + originText(it)) }
        }
    }

    private fun printDistribution(result: EquivalenceResult) {
        val byDepth = result.forms.groupingBy(EquivalentForm::depth).eachCount().toSortedMap()
        if (byDepth.size < 2) return
        println("Розподіл форм за кількістю ярусів: " + byDepth.entries.joinToString("; ") { (depth, count) ->
            "$depth ${plural(depth, "ярус", "яруси", "ярусів")} — $count ${plural(count, "форма", "форми", "форм")}"
        } + ".")
    }

    private fun originText(origin: FormOrigin) = "з №${origin.parentId}, ${origin.description}: " +
        "${FormFormatter.format(origin.before)} → ${FormFormatter.format(origin.after)}"

    private fun totalText(result: EquivalenceResult): String = when {
        result.total == null -> "понад ${result.forms.size} (перебір зупинено на ліміті ${result.forms.size})"
        result.complete -> result.total.toString()
        else -> "${result.total}; через ліміт оцінено ${result.forms.size}, але мінімальну кількість ярусів " +
            "гарантує форма з термами, відсортованими за вагою"
    }

    private fun plural(count: Int, one: String, few: String, many: String): String = plural(count.toBigInteger(), one, few, many)

    private fun plural(count: BigInteger, one: String, few: String, many: String): String {
        val lastTwo = count.mod(BigInteger.valueOf(100)).toInt()
        return when {
            lastTwo in 11..14 -> many
            lastTwo % 10 == 1 -> one
            lastTwo % 10 in 2..4 -> few
            else -> many
        }
    }
}
