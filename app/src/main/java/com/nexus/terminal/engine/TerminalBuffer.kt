package com.nexus.terminal.engine

class TerminalBuffer(
    private var columns: Int = 80,
    private var rows: Int = 24
) {
    private val lines = ArrayList<CharArray>()
    private var cursorRow = 0
    private var cursorCol = 0

    init {
        reset()
    }

    fun reset() {
        lines.clear()
        repeat(rows) {
            lines.add(CharArray(columns) { ' ' })
        }
        cursorRow = 0
        cursorCol = 0
    }

    fun clear() {
        for (row in lines.indices) {
            java.util.Arrays.fill(lines[row], ' ')
        }
        cursorRow = 0
        cursorCol = 0
    }

    fun resize(newColumns: Int, newRows: Int) {
        columns = newColumns.coerceAtLeast(1)
        rows = newRows.coerceAtLeast(1)

        val old = snapshot()

        lines.clear()
        repeat(rows) {
            lines.add(CharArray(columns) { ' ' })
        }

        for (r in old.indices.take(rows)) {
            for (c in old[r].indices.take(columns)) {
                lines[r][c] = old[r][c]
            }
        }

        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)
    }

    fun write(text: String) {
        for (ch in text) {
            when (ch) {
                '\r' -> cursorCol = 0
                '\n' -> newLine()
                '\b' -> {
                    if (cursorCol > 0) cursorCol--
                }
                '\t' -> {
                    cursorCol = ((cursorCol / 8) + 1) * 8
                    if (cursorCol >= columns) newLine()
                }
                else -> {
                    if (ch.code >= 32) {
                        putChar(ch)
                    }
                }
            }
        }
    }

    private fun putChar(ch: Char) {
        if (cursorCol >= columns) {
            newLine()
        }

        lines[cursorRow][cursorCol] = ch
        cursorCol++

        if (cursorCol >= columns) {
            newLine()
        }
    }

    private fun newLine() {
        cursorCol = 0
        cursorRow++

        if (cursorRow >= rows) {
            lines.removeAt(0)
            lines.add(CharArray(columns) { ' ' })
            cursorRow = rows - 1
        }
    }

    fun snapshot(): List<CharArray> {
        return lines.map { it.copyOf() }
    }

    fun text(): String {
        return lines.joinToString("\n") { String(it).trimEnd() }
    }

    fun cursorPosition(): Pair<Int, Int> {
        return cursorRow to cursorCol
    }
}
