package com.danylom73.parallel

import com.danylom73.expression.Expression

internal object LevelTableRenderer {
    private const val GAP = 3

    private class Box(
        val label: String,
        val level: Int,
        val width: Int,
        val anchor: Int,
        val children: List<Placed>,
    )

    private class Placed(val offset: Int, val box: Box)

    fun render(root: Expression): String {
        val tree = measure(root)
        if (tree.children.isEmpty()) return tree.label
        val margin = "ярус ${tree.level}".length + GAP
        val operands = CharArray(tree.width) { ' ' }
        val rows = Array(tree.level + 1) { CharArray(tree.width) { ' ' } }
        draw(tree, 0, operands, rows)
        return buildString {
            append(" ".repeat(margin)).append(operands.concatToString().trimEnd())
            for (level in 1..tree.level) {
                append('\n').append("ярус $level".padEnd(margin)).append(rows[level].concatToString().trimEnd())
            }
        }
    }

    private fun measure(node: Expression): Box {
        val label = label(node)
        val operands = children(node).map(::measure)
        if (operands.isEmpty()) return Box(label, 0, label.length + GAP, label.length / 2, emptyList())

        val offsets = operands.runningFold(0) { offset, box -> offset + box.width }.dropLast(1).toMutableList()
        fun anchorOf(index: Int) = offsets[index] + operands[index].anchor
        fun centre() = if (operands.size == 1) anchorOf(0) else (anchorOf(0) + anchorOf(operands.lastIndex) + 1) / 2
        while (operands.size > 1 && (centre() - label.length / 2 <= anchorOf(0) + 1 ||
                centre() - label.length / 2 + label.length >= anchorOf(operands.lastIndex))) {
            offsets[offsets.lastIndex]++
        }
        val shift = maxOf(0, label.length / 2 - centre())
        val anchor = centre() + shift
        val width = maxOf(shift + offsets.last() + operands.last().width, anchor - label.length / 2 + label.length + 1)
        return Box(
            label,
            operands.maxOf(Box::level) + 1,
            width,
            anchor,
            operands.mapIndexed { index, box -> Placed(shift + offsets[index], box) },
        )
    }

    private fun draw(box: Box, start: Int, operands: CharArray, rows: Array<CharArray>) {
        if (box.children.isEmpty()) {
            box.label.forEachIndexed { index, character -> operands[start + index] = character }
            return
        }
        val row = rows[box.level]
        val anchors = box.children.map { start + it.offset + it.box.anchor }
        box.children.forEachIndexed { index, child ->
            draw(child.box, start + child.offset, operands, rows)
            for (level in child.box.level + 1 until box.level) rows[level][anchors[index]] = '│'
        }
        if (anchors.size > 1) {
            for (column in anchors.first()..anchors.last()) row[column] = '─'
            anchors.forEach { row[it] = '┴' }
            row[anchors.first()] = '└'
            row[anchors.last()] = '┘'
        }
        val labelStart = start + box.anchor - box.label.length / 2
        box.label.forEachIndexed { index, character -> row[labelStart + index] = character }
    }

    private fun children(node: Expression): List<Expression> = when (node) {
        is Expression.Value -> emptyList()
        is Expression.Unary -> listOf(node.operand)
        is Expression.Binary -> listOf(node.left, node.right)
        is Expression.Call -> node.arguments
    }

    private fun label(node: Expression): String = when (node) {
        is Expression.Value -> node.text
        is Expression.Unary -> "(${node.operator})"
        is Expression.Binary -> node.operator
        is Expression.Call -> "${node.name}()"
    }
}
