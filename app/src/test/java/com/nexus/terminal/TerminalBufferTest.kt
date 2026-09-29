package com.nexus.terminal
import com.nexus.terminal.engine.TerminalBuffer
import org.junit.Assert.assertEquals
import org.junit.Test
class TerminalBufferTest{
 @Test fun ansiColorsDoNotRemainInText(){val b=TerminalBuffer(20,3);b.feed("hello\u001b[31m red\u001b[0m world".toByteArray());val s=b.snapshot()[0].joinToString(""){it.ch.toString()}.trim();assertEquals("hello red world",s)}
 @Test fun cursorMovementWorks(){val b=TerminalBuffer(10,2);b.feed("abc\u001b[2DXY".toByteArray());val s=b.snapshot()[0].joinToString(""){it.ch.toString()}.trim();assertEquals("aXY",s)}
}
