package com.nexus.terminal.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.nio.charset.StandardCharsets

class TerminalView(context: Context) : View(context) {

    private val buffer = TerminalBuffer()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 235, 240)
        typeface = Typeface.MONOSPACE
        textSize = 28f
    }

    private val backgroundPaint = Paint().apply {
        color = Color.rgb(8, 12, 16)
    }

    private var charWidth = 0f
    private var lineHeight = 0f
    private var attachedSession: SessionRuntime? = null
    private var outputListener: ((ByteArray) -> Unit)? = null

    var onCommandSubmitted: ((String) -> Unit)? = null

    init {
        setBackgroundColor(Color.rgb(8, 12, 16))
        isFocusable = true
        isFocusableInTouchMode = true

        val metrics = textPaint.fontMetrics
        charWidth = textPaint.measureText("M")
        lineHeight = metrics.descent - metrics.ascent
    }

    fun write(text: String) {
        buffer.write(text)
        invalidate()
    }

    fun clearTerminal() {
        buffer.clear()
        invalidate()
    }

    fun resetTerminal() {
        buffer.reset()
        invalidate()
    }

    fun snapshot(): List<CharArray> {
        return buffer.snapshot()
    }

    fun attach(session: SessionRuntime) {
        detach()
        attachedSession = session

        val listener: (ByteArray) -> Unit = { bytes ->
            val text = String(bytes, StandardCharsets.UTF_8)
            post {
                write(text)
            }
        }

        outputListener = listener
        session.attach(listener)
        requestFocus()
    }

    fun detach() {
        val session = attachedSession
        val listener = outputListener

        if (session != null && listener != null) {
            session.detach(listener)
        }

        attachedSession = null
        outputListener = null
    }

    override fun onDetachedFromWindow() {
        detach()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )

        val rows = buffer.snapshot()

        for (row in rows.indices) {
            val line = String(rows[row])
            val y = lineHeight * (row + 1)

            canvas.drawText(
                line,
                0f,
                y,
                textPaint
            )
        }

        val cursor = buffer.cursorPosition()
        val cursorX = cursor.second * charWidth
        val cursorY = cursor.first * lineHeight

        val cursorPaint = Paint().apply {
            color = Color.rgb(0, 220, 255)
            style = Paint.Style.FILL
        }

        canvas.drawRect(
            cursorX,
            cursorY,
            cursorX + charWidth,
            cursorY + lineHeight,
            cursorPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
        }
        return true
    }
}
