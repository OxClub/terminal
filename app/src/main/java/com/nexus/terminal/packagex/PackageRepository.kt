package com.nexus.terminal.packagex

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class PackageInfo(val name:String,val version:String,val description:String,val archiveUrl:String,val sha256:String?=null)
class PackageRepository(private val context:Context){
    private val prefs=context.getSharedPreferences("nexus",0)
    fun indexUrl()=prefs.getString("repo_index","").orEmpty()
    fun setIndexUrl(url:String)=prefs.edit().putString("repo_index",url).apply()
    fun fetch():List<PackageInfo>{val u=indexUrl();if(u.isBlank())return emptyList();val c=URL(u).openConnection() as HttpURLConnection;c.connectTimeout=8000;c.readTimeout=12000;c.requestMethod="GET";return c.inputStream.bufferedReader().use{read(it.readText())}.also{c.disconnect()}}
    private fun read(json:String):List<PackageInfo>{val a=JSONArray(json);return (0 until a.length()).map{val o=a.getJSONObject(it);PackageInfo(o.getString("name"),o.optString("version"),o.optString("description"),o.getString("archiveUrl"),o.optString("sha256").ifBlank{null})}}
}
