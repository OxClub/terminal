package com.nexus.terminal.engine

import android.content.Context
import com.nexus.terminal.data.TerminalSession
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

class SessionRuntime(val session:TerminalSession, private val home:File): TerminalChannel {
    private val output=ByteArrayOutputStreamLimited(1024*1024); private val listeners=CopyOnWriteArraySet<(ByteArray)->Unit>();
    private var pty:NativePty?=null
    fun start(){if(pty!=null)return;val p=NativePty{bytes->output.write(bytes);listeners.forEach{it(bytes)}};pty=p;session.pid=p.startSession(session.shell,home.absolutePath,session.cwd)}
    fun attach(l:(ByteArray)->Unit){listeners.add(l);val old=output.bytes();if(old.isNotEmpty())l(old)}
    fun detach(l:(ByteArray)->Unit){listeners.remove(l)}
    override fun send(s:String){pty?.send(s)}
    override fun sendBytes(b:ByteArray){pty?.sendBytes(b)}
    override fun resize(r:Int,c:Int,px:Int,py:Int){pty?.resize(r,c,px,py)}
    fun stop(){pty?.close();pty=null;session.pid=-1}
    fun restart(){stop();output.clear();start()}
}
class ByteArrayOutputStreamLimited(private val limit:Int){private val b=java.io.ByteArrayOutputStream();@Synchronized fun write(x:ByteArray){b.write(x);if(b.size()>limit){val a=b.toByteArray();b.reset();b.write(a,a.size-limit,limit)}}@Synchronized fun bytes()=b.toByteArray();@Synchronized fun clear(){b.reset()}}
class SessionManager(private val context:Context){
    val home=File(context.filesDir,"home").apply{mkdirs()};val bin=File(context.filesDir,"bin").apply{mkdirs()};private val map=LinkedHashMap<String,SessionRuntime>();
    init{File(home,".nexusrc").takeIf{!it.exists()}?.writeText("# NEXUS TERMINAL startup file\nexport NEXUS_HOME=\"$home\"\n")}
    @Synchronized fun list()=map.values.map{it.session}
    @Synchronized fun create(name:String="Terminal",shell:String=detectShell(),cwd:File=home):SessionRuntime{val s=TerminalSession(UUID.randomUUID().toString(),name,shell,cwd.absolutePath);val r=SessionRuntime(s,home);map[s.id]=r;r.start();return r}
    @Synchronized fun get(id:String)=map[id]
    @Synchronized fun close(id:String){map.remove(id)?.stop()}
    fun detectShell():String{val configured=context.getSharedPreferences("nexus",0).getString("shell",null);if(!configured.isNullOrBlank()&&File(configured).canExecute())return configured;return listOf("/system/bin/bash","/system/bin/sh","/bin/bash","/bin/sh").firstOrNull{File(it).canExecute()}?:"/system/bin/sh"}
}
