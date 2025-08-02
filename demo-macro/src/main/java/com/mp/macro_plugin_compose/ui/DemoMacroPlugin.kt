package com.mp.macro_plugin_compose.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo
import com.dark.plugins.ui.theme.NeuroVersePluginTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class DemoMacroPlugin(context: Context) : PluginApi(context) {

    override fun getPluginInfo(): PluginInfo {
        return PluginInfo(name = "Macro-Demo", description = "Sample Macro-Demo With AI plugin")
    }

    @Composable
    override fun AppContent() {
        val context = LocalContext.current
        val viewModel = viewModel<MacroViewModel>() // ✅ Scoped to plugin

        val macroText by viewModel.macroText.collectAsState()
        val savedMacros by viewModel.savedMacros.collectAsState()

        NeuroVersePluginTheme {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("Macro Editor (VM)", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Write a Macro:")
                    BasicTextField(
                        value = TextFieldValue(macroText),
                        onValueChange = { viewModel.updateText(it.text) },
                        singleLine = false,
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .heightIn(100.dp, 300.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                            .padding(8.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(onClick = {
                            viewModel.saveMacro()
                            Toast.makeText(context, "Macro saved", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Save Macro")
                        }

                        Button(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                aiCall(JSONObject().apply {
                                    put("system", "You are a helpful assistant that generates macros.")
                                    put("user", "Generate a macro for JSON object And Yaa Keep it short very short")
                                }){
                                    viewModel.updateText(it)
                                }
                            }

                            Toast.makeText(context, "Macro saved", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Generate")
                        }

                        Button(onClick = {
                            val result = viewModel.runMacro()
                            Toast.makeText(context, "Running macro:\n$result", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Run")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Saved Macros:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    savedMacros.forEachIndexed { index, macro ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Macro ${index + 1}: $macro")
                                TextButton(onClick = {
                                    viewModel.loadMacro(macro)
                                }) {
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
