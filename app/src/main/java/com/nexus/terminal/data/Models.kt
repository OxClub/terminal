package com.nexus.terminal.data

data class TerminalSession(val id: String, var name: String, val shell: String, val cwd: String, var pid: Int = -1)
data class HistoryEntry(val command: String, val time: Long)
data class ThemeConfig(val name: String, val background: Int, val foreground: Int, val cursor: Int)
