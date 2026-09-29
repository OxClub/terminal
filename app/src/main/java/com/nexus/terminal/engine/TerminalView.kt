package com.nexus.terminal.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import java.nio.charset.StandardCharsets

class TerminalView(context: Context) : View(context) {

    private val buffer = TerminalBuffer()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 27f
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var charWidth = 16f
    private var lineHeight = 32f

    private var attachedSession: SessionRuntime? = null
    private var outputListener: ((ByteArray) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    private var cursorVisible = true

    private val blink = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            main.postDelayed(this, 530)
        }
    }

    var onCommandSubmitted: ((String) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.rgb(5, 8, 12))
        main.post(blink)
    }

    fun attach(session: SessionRuntime) {
        detach()

        attachedSession = session

        val listener: (ByteArray) -> Unit = { bytes ->
            main.post {
                buffer.write(
                    String(bytes, StandardCharsets.UTF_8)
                )

                invalidate()
            }
        }

        outputListener = listener

        session.attach(listener)

        requestFocus()
        showKeyboard()
    }

    fun detach() {
        val s = attachedSession
        val l = outputListener

        if (s != null && l != null) {
            s.detach(l)
        }

        attachedSession = null
        outputListener = null
    }

    private fun showKeyboard() {
        postDelayed({
            requestFocus()

            val imm =
                context.getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager

            imm.showSoftInput(
                this,
                InputMethodManager.SHOW_IMPLICIT
            )
        }, 150)
    }

    private fun send(text: String) {
        attachedSession?.send(text)

        if (text.contains('\r') || text.contains('\n')) {
            onCommandSubmitted?.invoke("")
        }
    }

    private fun sendBytes(bytes: ByteArray) {
        attachedSession?.sendBytes(bytes)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(
        outAttrs: EditorInfo
    ): InputConnection {

        outAttrs.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        outAttrs.imeOptions =
            EditorInfo.IME_FLAG_NO_EXTRACT_UI

        return object : BaseInputConnection(this, false) {

            override fun commitText(
                text: CharSequence,
                newCursorPosition: Int
            ): Boolean {
                if (text.isNotEmpty()) {
                    send(text.toString())
                }

                return true
            }

            override fun setComposingText(
                text: CharSequence,
                newCursorPosition: Int
            ): Boolean {
                if (text.isNotEmpty()) {
                    send(text.toString())
                }

                return true
            }

            override fun deleteSurroundingText(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                repeat(beforeLength.coerceAtMost(16)) {
                    send("\u007F")
                }

                return true
            }

            override fun sendKeyEvent(
                event: KeyEvent
            ): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    return handleKey(event)
                }

                return true
            }

            override fun performEditorAction(
                editorAction: Int
            ): Boolean {
                send("\r")
                return true
            }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        return handleKey(event)
    }

    private fun handleKey(
        event: KeyEvent
    ): Boolean {

        if (event.isCtrlPressed) {
            val u = event.unicodeChar

            if (u != 0) {
                val ctrl = u and 0x1F

                if (ctrl != 0) {
                    sendBytes(byteArrayOf(ctrl.toByte()))
                    return true
                }
            }
        }

        if (event.isAltPressed && event.unicodeChar != 0) {
            sendBytes(
                byteArrayOf(
                    0x1B,
                    event.unicodeChar.toByte()
                )
            )

            return true
        }

        val sequence = when (event.keyCode) {

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER ->
                "\r"

            KeyEvent.KEYCODE_DEL ->
                "\u007F"

            KeyEvent.KEYCODE_FORWARD_DEL ->
                "\u001B[3~"

            KeyEvent.KEYCODE_TAB ->
                "\t"

            KeyEvent.KEYCODE_ESCAPE ->
                "\u001B"

            KeyEvent.KEYCODE_DPAD_UP ->
                "\u001B[A"

            KeyEvent.KEYCODE_DPAD_DOWN ->
                "\u001B[B"

            KeyEvent.KEYCODE_DPAD_RIGHT ->
                "\u001B[C"

            KeyEvent.KEYCODE_DPAD_LEFT ->
                "\u001B[D"

            KeyEvent.KEYCODE_MOVE_HOME ->
                "\u001B[H"

            KeyEvent.KEYCODE_MOVE_END ->
                "\u001B[F"

            KeyEvent.KEYCODE_PAGE_UP ->
                "\u001B[5~"

            KeyEvent.KEYCODE_PAGE_DOWN ->
                "\u001B[6~"

            else -> null
        }

        if (sequence != null) {
            send(sequence)
            return true
        }

        val unicode = event.unicodeChar

        if (unicode != 0) {
            send(unicode.toChar().toString())
            return true
        }

        return false
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            showKeyboard()
        }

        return true
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)

        charWidth =
            paint.measureText("M").coerceAtLeast(1f)

        lineHeight =
            (paint.fontMetrics.descent -
             paint.fontMetrics.ascent)
                .coerceAtLeast(1f)

        val cols =
            (w / charWidth).toInt().coerceAtLeast(1)

        val rows =
            (h / lineHeight).toInt().coerceAtLeast(1)

        buffer.resize(cols, rows)

        attachedSession?.resize(
            rows,
            cols,
            w,
            h
        )
    }

    private fun ansiColor(index: Int): Int {

        val normal = intArrayOf(
            Color.rgb(0, 0, 0),
            Color.rgb(205, 49, 49),
            Color.rgb(13, 188, 121),
            Color.rgb(229, 229, 16),
            Color.rgb(36, 114, 200),
            Color.rgb(188, 63, 188),
            Color.rgb(17, 168, 205),
            Color.rgb(229, 229, 229)
        )

        val bright = intArrayOf(
            Color.rgb(102, 102, 102),
            Color.rgb(241, 76, 76),
            Color.rgb(35, 209, 139),
            Color.rgb(245, 245, 67),
            Color.rgb(59, 142, 234),
            Color.rgb(214, 112, 214),
            Color.rgb(41, 184, 219),
            Color.rgb(255, 255, 255)
        )

        return when {
            index in 0..7 -> normal[index]
            index in 8..15 -> bright[index - 8]

            index in 16..231 -> {
                val n = index - 16
                val r = n / 36
                val g = (n % 36) / 6
                val b = n % 6

                fun v(x: Int): Int =
                    if (x == 0) 0 else 55 + x * 40

                Color.rgb(v(r), v(g), v(b))
            }

            index in 232..255 -> {
                val v = 8 + (index - 232) * 10
                Color.rgb(v, v, v)
            }

            else -> Color.WHITE
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(5, 8, 12))

        val cells = buffer.cells()

        for (row in cells.indices) {

            val line = cells[row]

            for (col in line.indices) {

                val cell = line[col]

                val x = col * charWidth
                val y = row * lineHeight

                if (cell.bg != 0) {
                    cursorPaint.color = ansiColor(cell.bg)

                    canvas.drawRect(
                        x,
                        y,
                        x + charWidth,
                        y + lineHeight,
                        cursorPaint
                    )
                }

                if (cell.ch != ' ') {

                    paint.color = ansiColor(cell.fg)

                    paint.typeface =
                        if (cell.bold)
                            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        else
                            Typeface.MONOSPACE

                    canvas.drawText(
                        cell.ch.toString(),
                        x,
                        y - paint.fontMetrics.ascent,
                        paint
                    )
                }
            }
        }

        if (cursorVisible) {

            val cursor = buffer.cursorPosition()

            val x = cursor.second * charWidth
            val y = cursor.first * lineHeight

            cursorPaint.color =
                Color.argb(190, 0, 220, 255)

            canvas.drawRect(
                x,
                y,
                x + charWidth,
                y + lineHeight,
                cursorPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(blink)
        detach()
        super.onDetachedFromWindow()
    }
}
