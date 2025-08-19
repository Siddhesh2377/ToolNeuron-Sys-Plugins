package com.mp.ai_chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dark.plugins.api.PluginApi
import com.mp.ai_chat.model.FileAttachment
import com.mp.ai_chat.model.Message
import com.mp.ai_chat.model.ROLE
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(private val pluginApi: PluginApi) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<FileAttachment>>(emptyList())
    val attachedFiles = _attachedFiles.asStateFlow()

    private var currentJob: Job? = null
    private val genCounter = AtomicLong(0L)

    fun send(text: String) {
        if (text.isBlank()) return
        _messages.update { it + Message(ROLE.USER, text, System.currentTimeMillis().toString()) }
        generateStreaming(text)
    }

    private fun generateStreaming(src: String) {
        currentJob?.cancel()
        val myGenId = genCounter.incrementAndGet()

        currentJob = viewModelScope.launch {
            _isGenerating.value = true

            val assistantIdx = _messages.value.size
            _messages.update {
                it + Message(ROLE.SYSTEM, "", timeStamp = System.currentTimeMillis().toString())
            }

            // --- streaming state ---
            val reason = StringBuilder()
            val answer = StringBuilder()
            var answerStarted = false
            var openingStripped = false
            var firstChunk = true

            // small lookbehind buffer to catch split tags across chunks
            val buf = StringBuilder()
            val OPEN = "<think>"
            val CLOSE = "</think>"
            val OPEN_LEN = OPEN.length
            val CLOSE_LEN = CLOSE.length

            // conflated UI updater
            val flushSignal = Channel<Unit>(capacity = Channel.CONFLATED)
            val updater = launch(Dispatchers.Main.immediate) {
                var last = 0L
                for (ignored in flushSignal) {
                    val now = android.os.SystemClock.uptimeMillis()
                    val wait = (16 - (now - last)).coerceAtLeast(0L)
                    if (wait > 0) delay(wait)
                    last = android.os.SystemClock.uptimeMillis()

                    if (myGenId != genCounter.get()) continue

                    val text = if (answerStarted) {
                        // emit fully wrapped once we started answer
                        "$OPEN${reason}$CLOSE$answer"
                    } else if (openingStripped) {
                        // still thinking; no closing tag yet
                        "$OPEN$reason"
                    } else if (answer.isNotEmpty()) {
                        // fallback: no think tag; pure answer
                        answer.toString()
                    } else {
                        "" // nothing yet
                    }

                    _messages.update { list ->
                        if (assistantIdx in list.indices) {
                            list.toMutableList().apply {
                                this[assistantIdx] = this[assistantIdx].copy(content = text)
                            }
                        } else list
                    }
                }
            }

            fun processChunk(chunk: String) {
                if (chunk.isEmpty() || myGenId != genCounter.get()) return

                // Fallback: if first chunk has no <think>, assume direct answer mode
                if (firstChunk) {
                    firstChunk = false
                    if (!chunk.contains(OPEN)) {
                        answerStarted = true
                        answer.append(chunk)
                        flushSignal.trySend(Unit)
                        return
                    }
                }

                buf.append(chunk)

                if (!openingStripped) {
                    val openIdx = buf.indexOf(OPEN)
                    if (openIdx >= 0) {
                        // discard everything up to and including <think>
                        buf.delete(0, openIdx + OPEN_LEN)
                        openingStripped = true
                    } else {
                        // keep only last OPEN_LEN-1 chars to detect split "<think>"
                        if (buf.length > OPEN_LEN - 1) {
                            buf.delete(0, buf.length - (OPEN_LEN - 1))
                        }
                        // not enough to show yet
                        return
                    }
                }

                if (!answerStarted) {
                    val closeIdx = buf.indexOf(CLOSE)
                    if (closeIdx >= 0) {
                        // consume everything before </think> as reasoning
                        reason.append(buf.substring(0, closeIdx))
                        // drop through the closing tag
                        buf.delete(0, closeIdx + CLOSE_LEN)
                        answerStarted = true
                        // whatever remains is the start of the answer
                        if (buf.isNotEmpty()) {
                            answer.append(buf.toString())
                            buf.clear()
                        }
                    } else {
                        // no closing tag yet: append all except a tiny lookbehind
                        val safeLen = (buf.length - (CLOSE_LEN - 1)).coerceAtLeast(0)
                        if (safeLen > 0) {
                            reason.append(buf.substring(0, safeLen))
                            buf.delete(0, safeLen)
                        }
                    }
                } else {
                    // already in answer mode: everything is answer
                    if (buf.isNotEmpty()) {
                        answer.append(buf.toString())
                        buf.clear()
                    }
                }

                flushSignal.trySend(Unit)
            }

            try {
                pluginApi.aiCall(
                    input = JSONObject().put("userPrompt", src),
                    onToken = ::processChunk
                )
            } catch (_: CancellationException) {
                // normal cancel/stop
            } finally {
                // drain any remaining buffer safely
                if (buf.isNotEmpty()) {
                    if (!openingStripped) {
                        // no <think> ever found → treat rest as answer
                        answerStarted = true
                        answer.append(buf)
                    } else if (!answerStarted) {
                        // still thinking when stream ended → show as thinking
                        reason.append(buf)
                    } else {
                        answer.append(buf)
                    }
                    buf.clear()
                }

                withContext(Dispatchers.Main.immediate) {
                    val finalText = if (answerStarted) {
                        "$OPEN${reason}$CLOSE$answer"
                    } else if (openingStripped) {
                        "$OPEN$reason"
                    } else {
                        answer.toString()
                    }
                    _messages.update { list ->
                        if (assistantIdx in list.indices) {
                            list.toMutableList().apply {
                                this[assistantIdx] = this[assistantIdx].copy(content = finalText)
                            }
                        } else list
                    }
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
    }

    fun attach(file: FileAttachment) {
        _attachedFiles.update { it + file }
    }

    fun clearAttachment(idx: Int) {
        _attachedFiles.update { list ->
            list.toMutableList().also { if (idx in it.indices) it.removeAt(idx) }
        }
    }

    companion object {
        fun provideFactory(pluginApi: PluginApi) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(pluginApi) as T
        }
    }
}
