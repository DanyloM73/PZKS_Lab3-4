package com.danylom73.cli

object TerminalColors {
    private const val ESC = "\u001B["
    private const val RESET = "${ESC}0m"
    private const val RED = "${ESC}31m"
    private const val GREEN = "${ESC}32m"
    private const val YELLOW = "${ESC}33m"
    private const val CYAN = "${ESC}36m"
    private const val BOLD = "${ESC}1m"

    val enabled: Boolean = System.getenv("NO_COLOR") == null &&
        (System.console() != null || System.getenv("FORCE_COLOR") != null)

    fun red(text: String): String = color(text, RED)
    fun green(text: String): String = color(text, GREEN)
    fun yellow(text: String): String = color(text, YELLOW)
    fun cyan(text: String): String = color(text, CYAN)
    fun bold(text: String): String = color(text, BOLD)

    fun redCarets(text: String): String {
        if (!enabled) return text

        return buildString {
            var colored = false
            text.forEach { character ->
                if (character == '^' && !colored) {
                    append(RED)
                    colored = true
                } else if (character != '^' && colored) {
                    append(RESET)
                    colored = false
                }
                append(character)
            }
            if (colored) append(RESET)
        }
    }

    fun redMarked(text: String, mask: BooleanArray): String {
        if (!enabled) return text

        return buildString {
            var colored = false
            text.forEachIndexed { index, character ->
                val shouldBeColored = mask.getOrElse(index) { false }
                if (shouldBeColored && !colored) {
                    append(RED)
                    colored = true
                } else if (!shouldBeColored && colored) {
                    append(RESET)
                    colored = false
                }
                append(character)
            }
            if (colored) append(RESET)
        }
    }

    private fun color(text: String, code: String): String =
        if (enabled) "$code$text$RESET" else text
}
