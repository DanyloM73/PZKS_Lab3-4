package com.danylom73.cli

import com.danylom73.analysis.AnalysisError

object ErrorHighlighter {
    private const val TAB_SIZE = 4

    fun format(source: String, errors: List<AnalysisError>): String {
        if (errors.isEmpty()) return ""

        val sourceLines = source.split('\n')
        return errors
            .groupBy { it.line }
            .toSortedMap()
            .map { (lineNumber, lineErrors) ->
                val originalLine = sourceLines.getOrElse(lineNumber - 1) { "" }.removeSuffix("\r")
                val displayedLine = expandTabs(originalLine)
                val ranges = lineErrors.map { error ->
                    val logicalStart = (error.column - 1).coerceAtLeast(0)
                    val logicalEnd = logicalStart + error.length.coerceAtLeast(1)
                    val visualStart = visualColumn(originalLine, logicalStart)
                    val visualEnd = if (logicalStart >= originalLine.length) {
                        visualStart + 1
                    } else {
                        visualColumn(originalLine, logicalEnd.coerceAtMost(originalLine.length))
                            .coerceAtLeast(visualStart + 1)
                    }
                    visualStart until visualEnd
                }

                val markerLength = maxOf(displayedLine.length, ranges.maxOf { it.last + 1 })
                val markers = CharArray(markerLength) { ' ' }
                ranges.forEach { range -> range.forEach { markers[it] = '^' } }

                val markerLine = markers.concatToString().trimEnd()
                val highlightedLine = TerminalColors.redMarked(
                    displayedLine,
                    BooleanArray(displayedLine.length) { markers[it] == '^' },
                )
                "$highlightedLine\n${TerminalColors.redCarets(markerLine)}"
            }
            .joinToString("\n")
    }

    private fun expandTabs(line: String): String = buildString {
        line.forEach { character ->
            if (character == '\t') {
                repeat(TAB_SIZE - length % TAB_SIZE) { append(' ') }
            } else {
                append(character)
            }
        }
    }

    private fun visualColumn(line: String, logicalColumn: Int): Int {
        var visualColumn = 0
        line.take(logicalColumn).forEach { character ->
            visualColumn += if (character == '\t') {
                TAB_SIZE - visualColumn % TAB_SIZE
            } else {
                1
            }
        }
        return visualColumn
    }
}
