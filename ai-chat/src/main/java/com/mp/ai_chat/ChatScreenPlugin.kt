package com.mp.ai_chat

import android.content.Context
import androidx.compose.runtime.Composable
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo

class ChatScreenPlugin(context: Context): PluginApi(context) {

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            "AI Chat",
            "Chat with AI",
        )
    }

    @Composable
    override fun AppContent() {
        ChattingScreen()
    }
}