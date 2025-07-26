package com.mp.macro_plugin_compose.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo
import com.dark.plugins.ui.theme.NeuroVersePluginTheme

class DemoMacroPlugin(context: Context) : PluginApi(context) {

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(
            "Demo Macro Plugin",
            "1.0.0",
        )
    }

    @Composable
    override fun AppContent() {
        NeuroVersePluginTheme {
            Scaffold {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(it),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Demo Macro Plugin")
                    Spacer(modifier = Modifier.padding(16.dp))
                    Button(onClick = {
                        Toast.makeText(appContext, "Button Clicked", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Click Me")
                    }
                }
            }
        }
    }
}