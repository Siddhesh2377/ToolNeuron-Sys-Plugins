package com.mp.notification_io

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dark.plugins.api.CommandQueue
import com.dark.plugins.api.PluginApi
import com.dark.plugins.api.PluginInfo
import org.json.JSONObject

class NotifyIOPlugin(context: Context) : PluginApi(context) {

    @Composable
    override fun AppContent() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            super.AppContent()
        }
    }

    override fun onCreate(data: Any) {
        super.onCreate(data)
        CommandQueue.enqueue(JSONObject().apply {
            put("run", "removeNotification")
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("NotifyIOPlugin", "onDestroy :(")
    }
}