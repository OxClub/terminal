package com.nexus.terminal.engine
import java.nio.charset.StandardCharsets
class NativePty(private val onOutput:(ByteArray)->Unit){
 companion object{init{System.loadLibrary("nexuspty")}}
 private var handle=0L
 private external fun start(shell:String,home:String,cwd:String):Long
 private external fun write(handle:Long,data:ByteArray):Int
 private external fun resize(handle:Long,rows:Int,cols:Int,px:Int,py:Int):Int
 private external fun stop(handle:Long)
 @Suppress("unused") private fun onNativeOutput(data:ByteArray)=onOutput(data)
 fun startSession(shell:String,home:String,cwd:String):Int{handle=start(shell,home,cwd);return if(handle==0L)-1 else 1}
 fun send(text:String)=sendBytes(text.toByteArray(StandardCharsets.UTF_8))
 fun sendBytes(bytes:ByteArray)=if(handle!=0L)write(handle,bytes) else -1
 fun resize(rows:Int,cols:Int,px:Int=0,py:Int=0)=if(handle!=0L)resize(handle,rows,cols,px,py) else -1
 fun close(){if(handle!=0L){stop(handle);handle=0L}}
}
