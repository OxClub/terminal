package com.nexus.terminal
import android.app.Application
import com.nexus.terminal.engine.SessionManager
class NexusApp:Application(){lateinit var sessions:SessionManager;override fun onCreate(){super.onCreate();sessions=SessionManager(this)}}
