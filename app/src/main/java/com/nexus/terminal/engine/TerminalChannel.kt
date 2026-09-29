package com.nexus.terminal.engine

interface TerminalChannel {
    fun send(s: String)
    fun sendBytes(b: ByteArray)
    fun resize(rows: Int, cols: Int, px: Int, py: Int)
}
