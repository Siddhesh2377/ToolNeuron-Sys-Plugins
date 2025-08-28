package com.mp.ai_chat.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.plugins.api.PluginApi
import com.mp.ai_chat.model.Message
import com.mp.ai_chat.model.ROLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(private val pluginApi: PluginApi) : ViewModel() {

    // Immutable message history (finalized messages only)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Streaming assistant text (keeps huge diffs out of _messages)
    private val _liveAnswer = MutableStateFlow("")
    val liveAnswer: StateFlow<String> = _liveAnswer.asStateFlow()

    private var currentJob: Job? = null
    private val genCounter = AtomicLong(0L)

    fun send(text: String) {
        if (text.isBlank()) return

        _messages.update { it + Message(ROLE.USER, text, System.currentTimeMillis().toString()) }

        // Build lightweight JSON payload
        val inputJson = JSONObject().apply {
            put("userPrompt", text)
        }

        Log.d("ChatViewModel", "payload.size=${inputJson.toString().length}")
        generateStreaming(inputJson)
    }

    private fun generateStreaming(src: JSONObject) {
        currentJob?.cancel()
        val myGenId = genCounter.incrementAndGet()

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true

            // Reserve an assistant slot (empty until final)
            val assistantIdx = _messages.value.size
            _messages.update {
                it + Message(
                    ROLE.SYSTEM,
                    "",
                    timeStamp = System.currentTimeMillis().toString()
                )
            }

            val answer = StringBuilder()
            val flushSignal = Channel<Unit>(capacity = Channel.CONFLATED)

            // Throttled UI updater (every ~120ms)
            val updater = launch(Dispatchers.Main) {
                for (ignored in flushSignal) {
                    delay(120)
                    if (myGenId != genCounter.get()) continue
                    _liveAnswer.value = answer.toString()
                }
            }

            fun processChunk(chunk: String) {
                if (chunk.isEmpty() || myGenId != genCounter.get()) return
                answer.append(chunk)
                flushSignal.trySend(Unit)
            }

            try {
                pluginApi.aiCall(input = src, onToken = ::processChunk)
            } catch (_: CancellationException) {
            } finally {
                withContext(Dispatchers.Main) {
                    val finalAnswer = answer.toString()
                    _messages.update { list ->
                        if (assistantIdx in list.indices) {
                            list.toMutableList().apply {
                                this[assistantIdx] = this[assistantIdx].copy(content = finalAnswer)
                            }
                        } else list
                    }
                    _liveAnswer.value = ""
                }
                flushSignal.close()
                updater.join()
                _isGenerating.value = false
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        viewModelScope.launch { pluginApi.stopGeneration() }
        _isGenerating.value = false
        _liveAnswer.value = ""
    }

    companion object {
        fun provideFactory(pluginApi: PluginApi) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(pluginApi) as T
        }
    }
}
