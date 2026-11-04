package com.danylom73.cli

import com.danylom73.analysis.AnalysisError
import com.danylom73.analysis.ArithmeticExpressionAnalyzer
import com.danylom73.analysis.ErrorCategory
import com.danylom73.analysis.SupportedFunctions
import com.danylom73.equivalence.EquivalenceReport
import com.danylom73.equivalence.EquivalenceResult
import com.danylom73.equivalence.EquivalentForms
import com.danylom73.expression.Expression
import com.danylom73.expression.ExpressionFormatter
import com.danylom73.expression.ExpressionParser
import com.danylom73.parallel.ParallelExpressionOptimizer
import com.danylom73.parallel.ParallelPlan
import com.danylom73.semantic.SemanticChecker

private enum class Mode(val command: String, val shortCommand: String, val prompt: String, val title: String) {
    PARALLEL("parallel", "par", "паралельна форма", "побудова паралельної форми виразу"),
    COMMUTATIVE("commutative", "comm", "комутативність", "еквівалентні форми за комутативним законом"),
    DISTRIBUTIVE("distributive", "dist", "дистрибутивність", "еквівалентні форми за дистрибутивним законом"),
    ;

    companion object {
        fun find(name: String): Mode? = entries.firstOrNull { name == it.command || name == it.shortCommand }

        val names: String
            get() = entries.joinToString(", ") { "${it.command} (${it.shortCommand})" }
    }
}

fun main(args: Array<String>) {
    val analyzer = ArithmeticExpressionAnalyzer()
    var mode = Mode.PARALLEL
    var arguments = args.toList()

    val modeOption = arguments.firstOrNull()?.takeIf { it.startsWith("--mode") }
    if (modeOption != null) {
        val value = modeOption.substringAfter("=", "").ifEmpty { arguments.getOrNull(1).orEmpty() }
        mode = Mode.find(value.lowercase()) ?: run {
            println(TerminalColors.red("Невідомий режим «$value». Доступні: ${Mode.names}."))
            return
        }
        arguments = arguments.drop(if ("=" in modeOption) 1 else 2)
    }
    arguments.firstOrNull()?.takeIf { it.startsWith("--") }?.let { option ->
        println(TerminalColors.red("Невідомий параметр «$option». Режим задається так: --mode=distributive."))
        return
    }

    if (arguments.isNotEmpty()) {
        val input = arguments.joinToString(" ")
        if (input.trim().equals(":functions", ignoreCase = true)) {
            printSupportedFunctions()
        } else {
            process(input, mode, analyzer)
        }
        return
    }

    println("\n${TerminalColors.bold(TerminalColors.cyan("Паралельні та еквівалентні форми арифметичного виразу"))}")
    println("Режим: ${mode.title}.")
    println("Режими: :parallel — паралельна форма; :commutative — комутативний закон;")
    println("        :distributive — дистрибутивний закон (скорочено :par, :comm, :dist).")
    println("Команди: :tree N — дерево форми N; :all — усі форми; :functions — перелік функцій; :exit — вихід.")
    var last: EquivalenceResult? = null
    while (true) {
        print("\n(${mode.prompt}) > ")
        val line = readlnOrNull() ?: break
        val command = line.trim().lowercase()
        val selectedMode = command.takeIf { it.startsWith(":") }?.let { Mode.find(it.removePrefix(":")) }
        when {
            command == ":exit" -> break
            command == ":functions" -> printSupportedFunctions()
            selectedMode != null -> {
                mode = selectedMode
                last = null
                println("Режим: ${mode.title}.")
            }
            command == ":all" -> last?.let(EquivalenceReport::printAll) ?: println(noResultMessage(mode))
            command.startsWith(":tree") -> printTree(last, command.removePrefix(":tree").trim(), mode)
            command.startsWith(":") -> println(TerminalColors.red("Невідома команда «${line.trim()}»."))
            else -> last = process(line, mode, analyzer)
        }
    }
}

private fun process(expression: String, mode: Mode, analyzer: ArithmeticExpressionAnalyzer): EquivalenceResult? {
    val parsed = parse(expression, analyzer) ?: return null
    println(TerminalColors.green("Вираз коректний."))
    return when (mode) {
        Mode.PARALLEL -> {
            printParallelForm(parsed)
            null
        }
        Mode.COMMUTATIVE -> EquivalentForms.commutative(parsed).also(EquivalenceReport::print)
        Mode.DISTRIBUTIVE -> EquivalentForms.distributive(parsed).also(EquivalenceReport::print)
    }
}

private fun parse(expression: String, analyzer: ArithmeticExpressionAnalyzer): Expression? {
    val result = analyzer.analyze(expression)
    if (!result.isValid) {
        printErrors(expression, result.errors)
        return null
    }

    val parser = ExpressionParser(result.tokens)
    val parsed = parser.parse()
    val semanticErrors = SemanticChecker.check(expression, parsed, parser.sourceSpans)
    if (semanticErrors.isNotEmpty()) {
        printErrors(expression, semanticErrors)
        return null
    }
    return parsed
}

private fun printParallelForm(original: Expression) {
    val optimization = ParallelExpressionOptimizer().optimizeWithTrace(original)
    val optimized = optimization.optimized
    val before = ParallelPlan.from(original)
    val after = ParallelPlan.from(optimized)

    println("\nПокрокова оптимізація:")
    println(optimization.renderSteps())
    println()
    println("Паралельна форма: ${ExpressionFormatter.format(optimized)}")
    println("Операцій: ${before.operations.size} → ${after.operations.size}; ярусів: ${before.depth} → ${after.depth}; " +
        "максимальна ширина: ${before.maximumWidth} → ${after.maximumWidth}.")
    println("\nДерево паралельної форми:")
    if (after.operations.isNotEmpty()) {
        println("Угорі операнди; рядок «ярус N» містить операції, які виконуються одночасно на N-му кроці.")
    }
    println(after.renderTree())
    println("\nНайраніший розклад виконання за ярусами:")
    println(after.renderSchedule())
}

private fun printTree(last: EquivalenceResult?, argument: String, mode: Mode) {
    if (last == null) {
        println(noResultMessage(mode))
        return
    }
    val form = argument.toIntOrNull()?.let(last::find)
    if (form == null) {
        println(TerminalColors.red("Вкажіть номер форми від 1 до ${last.forms.size}, наприклад :tree 2."))
        return
    }
    EquivalenceReport.printForm(form)
}

private fun noResultMessage(mode: Mode): String = if (mode == Mode.PARALLEL) {
    TerminalColors.yellow("Команда доступна лише в режимах :commutative і :distributive.")
} else {
    TerminalColors.yellow("Спочатку введіть вираз.")
}

private fun printErrors(expression: String, errors: List<AnalysisError>) {
    println(TerminalColors.red("Знайдено помилок: ${errors.size}"))
    println(ErrorHighlighter.format(expression, errors))
    errors.forEachIndexed { index, error ->
        val category = "[${error.category.displayName}]"
        val coloredCategory = when (error.category) {
            ErrorCategory.LEXICAL -> TerminalColors.yellow(category)
            ErrorCategory.SYNTACTIC, ErrorCategory.SEMANTIC -> TerminalColors.red(category)
        }
        println("${index + 1}. $coloredCategory ${error.message} (${error.line}:${error.column})")
    }
}

private fun printSupportedFunctions() {
    println(TerminalColors.bold(TerminalColors.cyan("Доступні функції:")))
    SupportedFunctions.all().forEach { function ->
        val parameters = if (function.argumentCount == 1) "x" else "x, y"
        val argumentWord = if (function.argumentCount == 1) "аргумент" else "аргументи"
        println("  ${TerminalColors.green("${function.name}($parameters)")} — " +
            "${function.argumentCount} $argumentWord")
    }
    println("Назви функцій чутливі до регістру.")
}
