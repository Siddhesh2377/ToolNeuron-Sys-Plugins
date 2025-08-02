package com.mp.macro_plugin_compose.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MacroViewModel : ViewModel() {

    private val _macroText = MutableStateFlow("")
    val macroText = _macroText.asStateFlow()

    private val _savedMacros = MutableStateFlow<List<String>>(emptyList())
    val savedMacros = _savedMacros.asStateFlow()

    fun updateText(newText: String) {
        _macroText.value += newText
    }

    fun saveMacro() {
        val trimmed = _macroText.value.trim()
        if (trimmed.isNotEmpty()) {
            _savedMacros.value = _savedMacros.value + trimmed
            _macroText.value = ""
        }
    }

    fun loadMacro(text: String) {
        _macroText.value = text
    }

    fun runMacro(): String = _macroText.value
}
