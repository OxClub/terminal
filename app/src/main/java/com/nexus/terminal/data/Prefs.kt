package com.nexus.terminal.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("nexus", Context.MODE_PRIVATE)
    fun getString(k:String, d:String) = p.getString(k,d) ?: d
    fun putString(k:String,v:String) = p.edit().putString(k,v).apply()
    fun getInt(k:String,d:Int) = p.getInt(k,d)
    fun putInt(k:String,v:Int) = p.edit().putInt(k,v).apply()
    fun history(): List<HistoryEntry> = runCatching { val a=JSONArray(p.getString("history","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); HistoryEntry(o.getString("c"),o.getLong("t")) } }.getOrDefault(emptyList())
    fun addHistory(command:String) { if(command.isBlank()) return; val old=history().filterNot{it.command==command}.toMutableList(); old.add(0,HistoryEntry(command,System.currentTimeMillis())); val max=getInt("history_size",500); val a=JSONArray(); old.take(max).forEach{a.put(JSONObject().put("c",it.command).put("t",it.time))}; p.edit().putString("history",a.toString()).apply() }
}
