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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 240, 245)
        typeface = Typeface.MONOSPACE
        textSize = 27f
    }

    private val bgPaint = Paint().apply {
        color = Color.rgb(7, 10, 13)
    }

    private val cursorPaint = Paint().apply {
        color = Color.rgb(0, 220, 255)
    }

    private var charWidth = 16f
    private var lineHeight = 32f

    private var session: SessionRuntime? = null
    private var listener: ((ByteArray) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    var onCommandSubmitted: ((String) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.rgb(7, 10, 13))
    }

    fun attach(runtime: SessionRuntime) {
        detach()

        session = runtime

        val l: (ByteArray) -> Unit = { bytes ->
            val text = String(bytes, StandardCharsets.UTF_8)

            main.post {
                buffer.write(text)
                invalidate()
            }
        }

        listener = l
        runtime.attach(l)

        requestFocus()
        showKeyboard()
    }

    fun detach() {
        val s = session
        val l = listener

        if (s != null && l != null) {
            s.detach(l)
        }

        session = null
        listener = null
    }

    private fun showKeyboard() {
        postDelayed({
            requestFocus()

            val imm =
                context.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as InputMethodManager

            imm.showSoftInput(
                this,
                InputMethodManager.SHOW_IMPLICIT
            )
        }, 150)
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
                    writeToPty(text.toString())
                }

                return true
            }

            override fun setComposingText(
                text: CharSequence,
                newCursorPosition: Int
            ): Boolean {

                if (text.isNotEmpty()) {
                    writeToPty(text.toString())
                }

                return true
            }

            override fun deleteSurroundingText(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {

                repeat(beforeLength.coerceAtMost(16)) {
                    writeToPty("\u007F")
                }

                return true
            }

            override fun sendKeyEvent(
                event: KeyEvent
            ): Boolean {

                if (event.action != KeyEvent.ACTION_DOWN) {
                    return true
                }

                return handleKey(event)
            }

            override fun performEditorAction(
                editorAction: Int
            ): Boolean {

                writeToPty("\r")
                return true
            }
        }
    }

    private fun writeToPty(text: String) {
        session?.send(text)

        if (text.contains('\r') || text.contains('\n')) {
            onCommandSubmitted?.invoke("")
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

        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed

        if (ctrl) {
            val unicode = event.unicodeChar

            if (unicode != 0) {
                val value = unicode and 0x1f

                if (value != 0) {
                    session?.sendBytes(
                        byteArrayOf(value.toByte())
                    )

                    return true
                }
            }
        }

        if (alt && event.unicodeChar != 0) {

            session?.sendBytes(
                byteArrayOf(
                    0x1b,
                    event.unicodeChar.toByte()
                )
            )

            return true
        }

        val sequence = when (event.keyCode) {

            KeyEvent.KEYCODE_ENTER ->
                "\r"

            KeyEvent.KEYCODE_DEL ->
                "\u007F"

            KeyEvent.KEYCODE_TAB ->
                "\t"

            KeyEvent.KEYCODE_ESCAPE ->
                "\u001B"

            KeyEvent.KEYCODE_DPAD_UP ->
                "\u001B[A"

            KeyEvent.KEYCODE_DPAD_DOWN ->
                "\u001B[B"

            KeyEvent.KEYCODE_DPAD_LEFT ->
                "\u001B[D"

            KeyEvent.KEYCODE_DPAD_RIGHT ->
                "\u001B[C"

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
            writeToPty(sequence)
            return true
        }

        val unicode = event.unicodeChar

        if (unicode != 0) {
            writeToPty(unicode.toChar().toString())
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
            textPaint.measureText("M").coerceAtLeast(1f)

        lineHeight =
            (textPaint.fontMetrics.descent -
             textPaint.fontMetrics.ascent)
                .coerceAtLeast(1f)

        val columns =
            (w / charWidth)
                .toInt()
                .coerceAtLeast(1)

        val rows =
            (h / lineHeight)
                .toInt()
                .coerceAtLeast(1)

        buffer.resize(columns, rows)

        session?.resize(
            rows,
            columns,
            w,
            h
        )
    }

    override fun onDetachedFromWindow() {
        detach()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            bgPaint
        )

        val lines = buffer.snapshot()

        for (row in lines.indices) {

            canvas.drawText(
                String(lines[row]),
                0f,
                lineHeight * (row + 1),
                textPaint
            )
        }

        val cursor = buffer.cursorPosition()

        val x =
            cursor.second * charWidth

        val y =
            cursor.first * lineHeight

        canvas.drawRect(
            x,
            y,
            x + charWidth,
            y + lineHeight,
            cursorPaint
        )
    }
}
