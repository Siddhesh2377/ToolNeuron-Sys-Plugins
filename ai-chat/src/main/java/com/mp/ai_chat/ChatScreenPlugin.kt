package com.mp.ai_chat

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import com.dark.plugins.api.ComposableBlock
import com.dark.plugins.api.PluginApi
import com.dark.plugins.api.PluginInfo
import com.mp.ai_chat.ui.screens.ChattingScreen

@Keep
class ChatScreenPlugin(context: Context) : PluginApi(context) {

    @Keep
    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            "AI Chat",
            "Chat with AI",
        )
    }

    @Keep
    @Composable
    override fun AppContent() {
        ChattingScreen(this)
    }

    @Keep
    override fun content(): ComposableBlock {
        return { AppContent() }
    }
}