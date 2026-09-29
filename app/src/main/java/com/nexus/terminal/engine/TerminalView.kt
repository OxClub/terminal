package com.nexus.terminal.engine

import android.content.Context
import android.graphics.*
import android.text.InputType
import android.view.*
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView

interface TerminalChannel { fun send(text:String); fun sendBytes(bytes:ByteArray); fun resize(rows:Int,cols:Int,px:Int=0,py:Int=0) }

class TerminalView(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.MONOSPACE;textSize=14f}
    private val buffer=TerminalBuffer(); private var channel:TerminalChannel?=null; private var cellW=9f; private var cellH=20f; private var scrollOffset=0; private var selecting=false; private var sx=0; private var sy=0; private var ex=0; private var ey=0; private val inputLine=StringBuilder(); var onCommandSubmitted:((String)->Unit)?=null
    init{isFocusable=true;isFocusableInTouchMode=true;setBackgroundColor(Color.rgb(9,12,16));setOnLongClickListener{showContextMenu();true}}
    fun attach(c:TerminalChannel){channel=c;requestFocus();post{resizePty()}}
    fun feed(bytes:ByteArray){buffer.feed(bytes);postInvalidate()}
    fun clear(){buffer.feed("\u001b[2J\u001b[H".toByteArray())}
    fun copyAll(){val s=buffer.snapshot().joinToString("\n"){it.joinToString(""){c->c.ch.toString()}};val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal",s))}
    private fun selected(x:Int,y:Int):Boolean{if(!selecting)return false;val a=sy*100000+sx;val b=ey*100000+ex;val p=y*100000+x;val lo=minOf(a,b);val hi=maxOf(a,b);return p in lo..hi}
private fun selectedText():String{val lines=buffer.snapshot();val a=minOf(sy*100000+sx,ey*100000+ex);val b=maxOf(sy*100000+sx,ey*100000+ex);val out=StringBuilder();for(y in lines.indices){for(x in lines[y].indices){val p=y*100000+x;if(p in a..b)out.append(lines[y][x].ch)}if(y<lines.lastIndex)out.append('\n')};return out.toString().trimEnd()}
private fun showSelectionMenu(){val popup=android.widget.PopupMenu(context,this);popup.menu.add("Copy").setOnMenuItemClickListener{val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal",selectedText()));selecting=false;invalidate();true};popup.menu.add("Paste").setOnMenuItemClickListener{paste();true};popup.show()}
private fun showContextMenu(){val popup=android.widget.PopupMenu(context,this);popup.menu.add("Copy All").setOnMenuItemClickListener{copyAll();true};popup.menu.add("Paste").setOnMenuItemClickListener{paste();true};popup.menu.add("Select All").setOnMenuItemClickListener{copyAll();true};popup.show()}
    fun paste(){val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;if(cm.hasPrimaryClip())channel?.send(cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty())}
    override fun onDraw(c:Canvas){super.onDraw(c);val lines=buffer.snapshot();paint.textSize=resources.displayMetrics.scaledDensity*14f;cellW=paint.measureText("M");cellH=paint.fontMetrics.run{bottom-top};for((r,line) in lines.withIndex()){for((x,cell) in line.withIndex()){if(selecting&&selected(x,r)){paint.color=Color.rgb(45,80,110);c.drawRect(x*cellW,r*cellH,(x+1)*cellW,(r+1)*cellH,paint)};if(cell.bg!=Color.TRANSPARENT){paint.color=cell.bg;c.drawRect(x*cellW,r*cellH,(x+1)*cellW,(r+1)*cellH,paint)};if(cell.ch!=' '){paint.color=cell.fg;paint.typeface=if(cell.bold)Typeface.MONOSPACE_BOLD else Typeface.MONOSPACE;c.drawText(cell.ch.toString(),x*cellW,r*cellH-paint.fontMetrics.top,paint)}}}}
    private fun resizePty(){val cols=(width/cellW).toInt().coerceAtLeast(20);val rows=(height/cellH).toInt().coerceAtLeast(4);buffer.resize(cols,rows);channel?.resize(rows,cols,width,height)}
    override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){post{resizePty()}}
    override fun onTouchEvent(e:MotionEvent):Boolean{val x=(e.x/cellW).toInt().coerceAtLeast(0);val y=(e.y/cellH).toInt().coerceAtLeast(0);when(e.action){MotionEvent.ACTION_DOWN->{requestFocus();sx=x;sy=y;ex=x;ey=y;selecting=false;return true};MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(x-sx)+kotlin.math.abs(y-sy)>1)selecting=true;ex=x;ey=y;invalidate();return true};MotionEvent.ACTION_UP->{ex=x;ey=y;if(selecting)showSelectionMenu();return true}};return true}
    override fun onCheckIsTextEditor()=true
    override fun onCreateInputConnection(out:EditorInfo):InputConnection{out.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;out.imeOptions=EditorInfo.IME_FLAG_NO_EXTRACT_UI;return object:BaseInputConnection(this,false){override fun commitText(text:CharSequence?,newCursorPosition:Int):Boolean{val v=text?.toString().orEmpty();inputLine.append(v);channel?.send(v);return true};override fun deleteSurroundingText(beforeLength:Int,afterLength:Int):Boolean{if(inputLine.isNotEmpty())inputLine.deleteCharAt(inputLine.lastIndex);channel?.send("\u007f");return true};override fun sendKeyEvent(event:KeyEvent):Boolean{if(event.action!=KeyEvent.ACTION_DOWN)return true;handleKey(event);return true}}}
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean{handleKey(event);return true}
    private fun handleKey(e:KeyEvent){val ctrl=e.isCtrlPressed;val alt=e.isAltPressed;val s=when(e.keyCode){KeyEvent.KEYCODE_ENTER->{onCommandSubmitted?.invoke(inputLine.toString());inputLine.clear();"\r"};KeyEvent.KEYCODE_DEL->"\u007f";KeyEvent.KEYCODE_TAB->"\t";KeyEvent.KEYCODE_ESCAPE->"\u001b";KeyEvent.KEYCODE_DPAD_UP->"\u001b[A";KeyEvent.KEYCODE_DPAD_DOWN->"\u001b[B";KeyEvent.KEYCODE_DPAD_LEFT->"\u001b[D";KeyEvent.KEYCODE_DPAD_RIGHT->"\u001b[C";KeyEvent.KEYCODE_MOVE_HOME->"\u001b[H";KeyEvent.KEYCODE_MOVE_END->"\u001b[F";else->null};if(s!=null){channel?.send(s);return};if(ctrl&&e.unicodeChar>0){val ch=e.unicodeChar.toChar().uppercaseChar();val code=(ch.code-64).toChar();channel?.send(code.toString());return};if(alt&&e.unicodeChar>0)channel?.send("\u001b"+e.unicodeChar.toChar())}
}
