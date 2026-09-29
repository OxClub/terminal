package com.nexus.terminal.engine

data class TerminalCell(
    var ch: Char = ' ',
    var fg: Int = 7,
    var bg: Int = 0,
    var bold: Boolean = false
)

class TerminalBuffer(
    private var columns: Int = 80,
    private var rows: Int = 24
) {
    companion object {
        const val SCROLLBACK = 1000
    }

    private val lines = ArrayList<MutableList<TerminalCell>>()

    private var cursorRow = 0
    private var cursorCol = 0

    private var savedRow = 0
    private var savedCol = 0

    private var fg = 7
    private var bg = 0
    private var bold = false

    private var state = 0
    private val csi = StringBuilder()

    init {
        reset()
    }

    private fun blankLine(): MutableList<TerminalCell> =
        MutableList(columns) { TerminalCell() }

    fun reset() {
        lines.clear()
        repeat(rows) {
            lines.add(blankLine())
        }

        cursorRow = 0
        cursorCol = 0
        savedRow = 0
        savedCol = 0

        fg = 7
        bg = 0
        bold = false

        state = 0
        csi.clear()
    }

    fun clear() {
        for (r in lines.indices) {
            for (c in lines[r].indices) {
                lines[r][c] = TerminalCell()
            }
        }

        cursorRow = 0
        cursorCol = 0
    }

    fun resize(newColumns: Int, newRows: Int) {
        val cols = newColumns.coerceAtLeast(1)
        val rs = newRows.coerceAtLeast(1)

        if (cols == columns && rs == rows) return

        columns = cols
        rows = rs

        while (lines.size < rows) {
            lines.add(blankLine())
        }

        while (lines.size > rows + SCROLLBACK) {
            lines.removeAt(0)
            cursorRow--
        }

        for (r in lines.indices) {
            while (lines[r].size < columns) {
                lines[r].add(TerminalCell())
            }

            while (lines[r].size > columns) {
                lines[r].removeAt(lines[r].lastIndex)
            }
        }

        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)

        while (lines.size < rows) {
            lines.add(blankLine())
        }

        if (lines.size > rows) {
            val start = lines.size - rows
            val visible = ArrayList(lines.subList(start, lines.size))
            lines.clear()
            lines.addAll(visible)
            cursorRow = rows - 1
        }
    }

    fun write(text: String) {
        for (ch in text) {
            consume(ch)
        }
    }

    private fun consume(ch: Char) {
        when (state) {
            0 -> normal(ch)
            1 -> escape(ch)
            2 -> csi(ch)
            3 -> osc(ch)
        }
    }

    private fun normal(ch: Char) {
        when (ch) {
            '\u001B' -> {
                state = 1
            }

            '\r' -> {
                cursorCol = 0
            }

            '\n' -> {
                cursorRow++
                if (cursorRow >= rows) {
                    scroll()
                    cursorRow = rows - 1
                }
            }

            '\b' -> {
                cursorCol = (cursorCol - 1).coerceAtLeast(0)
            }

            '\t' -> {
                cursorCol = ((cursorCol / 8) + 1) * 8
                if (cursorCol >= columns) {
                    cursorCol = columns - 1
                }
            }

            '\u0007' -> {
                // BEL
            }

            else -> {
                if (ch.code >= 32) {
                    put(ch)
                }
            }
        }
    }

    private fun escape(ch: Char) {
        when (ch) {
            '[' -> {
                csi.clear()
                state = 2
            }

            ']' -> {
                state = 3
            }

            '7' -> {
                savedRow = cursorRow
                savedCol = cursorCol
                state = 0
            }

            '8' -> {
                cursorRow = savedRow.coerceIn(0, rows - 1)
                cursorCol = savedCol.coerceIn(0, columns - 1)
                state = 0
            }

            'c' -> {
                reset()
            }

            'D' -> {
                cursorRow++
                if (cursorRow >= rows) {
                    scroll()
                    cursorRow = rows - 1
                }
                state = 0
            }

            'M' -> {
                if (cursorRow > 0) cursorRow--
                state = 0
            }

            'E' -> {
                cursorCol = 0
                cursorRow++
                if (cursorRow >= rows) {
                    scroll()
                    cursorRow = rows - 1
                }
                state = 0
            }

            else -> {
                state = 0
            }
        }
    }

    private fun csi(ch: Char) {
        if (ch.code in 0x40..0x7E) {
            executeCsi(ch, csi.toString())
            csi.clear()
            state = 0
            return
        }

        csi.append(ch)

        if (csi.length > 64) {
            csi.clear()
            state = 0
        }
    }

    private fun osc(ch: Char) {
        if (ch == '\u0007') {
            state = 0
        } else if (ch == '\u001B') {
            state = 1
        }
    }

    private fun params(raw: String): IntArray {
        val clean = raw
            .removePrefix("?")
            .removePrefix(">")
            .removePrefix("!")

        if (clean.isBlank()) return intArrayOf(0)

        return clean.split(';').map {
            it.toIntOrNull() ?: 0
        }.toIntArray()
    }

    private fun n(raw: String, default: Int = 1): Int {
        val p = params(raw).firstOrNull() ?: 0
        return if (p == 0) default else p
    }

    private fun executeCsi(command: Char, raw: String) {
        val p = params(raw)

        when (command) {
            'A' -> cursorRow =
                (cursorRow - n(raw)).coerceAtLeast(0)

            'B' -> cursorRow =
                (cursorRow + n(raw)).coerceAtMost(rows - 1)

            'C' -> cursorCol =
                (cursorCol + n(raw)).coerceAtMost(columns - 1)

            'D' -> cursorCol =
                (cursorCol - n(raw)).coerceAtLeast(0)

            'E' -> {
                cursorRow =
                    (cursorRow + n(raw)).coerceAtMost(rows - 1)
                cursorCol = 0
            }

            'F' -> {
                cursorRow =
                    (cursorRow - n(raw)).coerceAtLeast(0)
                cursorCol = 0
            }

            'G', '`' -> {
                cursorCol =
                    ((p.firstOrNull() ?: 1) - 1)
                        .coerceIn(0, columns - 1)
            }

            'd' -> {
                cursorRow =
                    ((p.firstOrNull() ?: 1) - 1)
                        .coerceIn(0, rows - 1)
            }

            'H', 'f' -> {
                val r = (p.getOrNull(0) ?: 1) - 1
                val c = (p.getOrNull(1) ?: 1) - 1

                cursorRow = r.coerceIn(0, rows - 1)
                cursorCol = c.coerceIn(0, columns - 1)
            }

            'J' -> eraseDisplay(p.firstOrNull() ?: 0)

            'K' -> eraseLine(p.firstOrNull() ?: 0)

            'P' -> {
                val count = n(raw)
                repeat(count) {
                    if (cursorCol < columns) {
                        lines[cursorRow].removeAt(cursorCol)
                        lines[cursorRow].add(TerminalCell())
                    }
                }
            }

            '@' -> {
                val count = n(raw)

                repeat(count) {
                    lines[cursorRow].add(
                        cursorCol.coerceIn(0, lines[cursorRow].size),
                        TerminalCell()
                    )

                    if (lines[cursorRow].size > columns) {
                        lines[cursorRow].removeAt(lines[cursorRow].lastIndex)
                    }
                }
            }

            'X' -> {
                val count = n(raw)

                repeat(count) {
                    if (cursorCol < columns) {
                        lines[cursorRow][cursorCol] =
                            TerminalCell()
                    }

                    cursorCol =
                        (cursorCol + 1).coerceAtMost(columns - 1)
                }
            }

            's' -> {
                savedRow = cursorRow
                savedCol = cursorCol
            }

            'u' -> {
                cursorRow =
                    savedRow.coerceIn(0, rows - 1)

                cursorCol =
                    savedCol.coerceIn(0, columns - 1)
            }

            'm' -> {
                sgr(p)
            }

            'h', 'l' -> {
                // DEC private modes.
                // PTY applications commonly send ?25h/?25l
                // for cursor visibility. Rendering always keeps
                // a local cursor.
            }

            'r' -> {
                // Scroll-region commands are accepted.
            }
        }
    }

    private fun sgr(p: IntArray) {
        if (p.isEmpty()) {
            fg = 7
            bg = 0
            bold = false
            return
        }

        var i = 0

        while (i < p.size) {
            when (val code = p[i]) {
                0 -> {
                    fg = 7
                    bg = 0
                    bold = false
                }

                1 -> bold = true

                22 -> bold = false

                in 30..37 -> fg = code - 30

                39 -> fg = 7

                in 40..47 -> bg = code - 40

                49 -> bg = 0

                in 90..97 -> fg = code - 90 + 8

                in 100..107 -> bg = code - 100 + 8

                38, 48 -> {
                    val isForeground = code == 38

                    if (i + 2 < p.size && p[i + 1] == 5) {
                        val color = p[i + 2].coerceIn(0, 255)

                        if (isForeground) fg = color
                        else bg = color

                        i += 2
                    }
                }
            }

            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            2, 3 -> {
                for (r in lines.indices) {
                    for (c in 0 until columns) {
                        lines[r][c] = TerminalCell()
                    }
                }

                cursorRow = 0
                cursorCol = 0
            }

            0 -> {
                for (c in cursorCol until columns) {
                    lines[cursorRow][c] = TerminalCell()
                }

                for (r in cursorRow + 1 until rows) {
                    if (r < lines.size) {
                        for (c in 0 until columns) {
                            lines[r][c] = TerminalCell()
                        }
                    }
                }
            }

            1 -> {
                for (r in 0..cursorRow) {
                    for (c in 0 until columns) {
                        if (r < cursorRow || c <= cursorCol) {
                            lines[r][c] = TerminalCell()
                        }
                    }
                }
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> {
                for (c in cursorCol until columns) {
                    lines[cursorRow][c] = TerminalCell()
                }
            }

            1 -> {
                for (c in 0..cursorCol.coerceAtMost(columns - 1)) {
                    lines[cursorRow][c] = TerminalCell()
                }
            }

            2 -> {
                for (c in 0 until columns) {
                    lines[cursorRow][c] = TerminalCell()
                }
            }
        }
    }

    private fun put(ch: Char) {
        if (cursorCol >= columns) {
            cursorCol = 0
            cursorRow++

            if (cursorRow >= rows) {
                scroll()
                cursorRow = rows - 1
            }
        }

        lines[cursorRow][cursorCol] =
            TerminalCell(ch, fg, bg, bold)

        cursorCol++

        if (cursorCol >= columns) {
            cursorCol = columns
        }
    }

    private fun scroll() {
        lines.add(blankLine())

        while (lines.size > rows) {
            lines.removeAt(0)
        }
    }

    fun snapshot(): List<CharArray> {
        return lines.takeLast(rows).map { line ->
            CharArray(columns) { c ->
                line.getOrNull(c)?.ch ?: ' '
            }
        }
    }

    fun cells(): List<List<TerminalCell>> {
        return lines.takeLast(rows).map { line ->
            List(columns) { c ->
                line.getOrNull(c) ?: TerminalCell()
            }
        }
    }

    fun text(): String {
        return snapshot().joinToString("\n") {
            String(it).trimEnd()
        }
    }

    fun cursorPosition(): Pair<Int, Int> {
        return cursorRow to cursorCol.coerceIn(0, columns - 1)
    }

    fun rowsCount(): Int = rows

    fun columnsCount(): Int = columns
}
