package com.mp.ai_chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.plugins.ui.theme.rDP
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ultra-light ChatViewModel that you can replace with your own ChattingViewModel.
 */
class ChatViewModel(context: Context) : ViewModel() {
    private val _messages = MutableStateFlow(listOf<Message>())
    val messages = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<FileAttachment>>(emptyList())
    val attachedFiles = _attachedFiles.asStateFlow()

    fun send(text: String) {
        val userMsg = Message(ROLE.USER, text, timeStamp = "TIME")
        _messages.value += userMsg
        generateEcho(text)
    }

    private fun generateEcho(src: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            delay(350)
            _messages.value += Message(
                ROLE.SYSTEM, "Echo: $src",
                timeStamp = "TIME",
            )
            _isGenerating.value = false
        }
    }

    fun stop() {
        _isGenerating.value = false
    }

    fun attach(file: FileAttachment) {
        _attachedFiles.value += file
    }

    fun clearAttachment(idx: Int) {
        _attachedFiles.value = _attachedFiles.value.toMutableList().also { it.removeAt(idx) }
    }
}

// ---------------------------------------------------------------------
//  Main Composable – drop-in replacement for your old ChattingScreen()
// ---------------------------------------------------------------------
@Composable
fun ChattingScreen() {
    val context = LocalContext.current
    val viewModel: ChatViewModel = remember { ChatViewModel(context) }
    rememberCoroutineScope()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val files by viewModel.attachedFiles.collectAsState()

    var input by remember { mutableStateOf("") }
    LocalContext.current

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            //viewModel.attach(FileAttachment(doc = uri, isLoading =  false))
        }

    Scaffold(
        topBar = {
        Text(
            "AI Chat",
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            style = MaterialTheme.typography.headlineSmall
        )
    }, bottomBar = {
        BottomBarContent(
            text = input,
            onTextChange = { input = it },
            attachments = files,
            isGenerating = isGenerating,
            onAttach = { picker.launch("*/*") },
            onSend = {
                if (isGenerating) viewModel.stop() else if (input.isNotBlank()) {
                    viewModel.send(input.trim())
                    input = ""
                }
            },
            onRemove = viewModel::clearAttachment
        )
    }, modifier = Modifier.fillMaxSize()
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = rDP(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            items(messages.size) { idx ->
                ChatBubble(msg = messages[idx])
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Chat bubble – user right, assistant left, thinking tag support
// ---------------------------------------------------------------------
@Composable
private fun ChatBubble(msg: Message) {
    val isUser = msg.role == ROLE.USER
    val think = msg.content.startsWith("<think>")
    val content = msg.content.removePrefix("<think>").removeSuffix("</think>")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (think) ThinkingChip(isUser) else Unit
        MarkdownText(
            text = content.trim(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier
                .then(
                    if (isUser) Modifier.background(
                        MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large
                    ) else Modifier
                )
                .widthIn(max = rDP(300.dp))
                .padding(vertical = rDP(8.dp), horizontal = rDP(14.dp))
        )
    }
}

@Composable
private fun ThinkingChip(user: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { }
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(rDP(6.dp))) {
        Icon(
            imageVector = if (user) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            modifier = Modifier.size(rDP(14.dp))
        )
        Spacer(Modifier.width(rDP(6.dp)))
        Text("AI is thinking…", style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------------
//  Bottom bar – attachments, stop / send button, progress overlay
// ---------------------------------------------------------------------
@Composable
private fun BottomBarContent(
    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<FileAttachment>,
    isGenerating: Boolean,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(rDP(6.dp))
    ) {
        AnimatedVisibility(attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rDP(6.dp))) {
                items(attachments.size) { idx ->
                    FileChip(attachments[idx]) { onRemove(idx) }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary)
                .padding(rDP(6.dp)),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.weight(1f),
                singleLine = false,
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Type…", color = Color.Gray)
                    inner()
                })

            Action(icon = Icons.Default.AttachFile, "Attach", onAttach)
            ActionProgress(icon = Icons.AutoMirrored.TwoTone.Send, "Send", isGenerating, onSend)
        }
    }
}

@Composable
private fun FileChip(f: FileAttachment, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small
            )
            .padding(horizontal = rDP(8.dp), vertical = rDP(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(rDP(14.dp))
        )
        Spacer(Modifier.width(rDP(4.dp)))
        Text(f.doc.name, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(rDP(4.dp)))
        if (f.isLoading) CircularProgressIndicator(
            modifier = Modifier.size(rDP(12.dp)),
            strokeWidth = rDP(2.dp)
        )
        else Icon(
            Icons.Default.ExpandMore,
            null,
            modifier = Modifier
                .size(rDP(12.dp))
                .clickable { onRemove() })
    }
}

// ---------------------------------------------------------------------
//  Small reusable icon buttons
// ---------------------------------------------------------------------
@Composable
private fun Action(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(rDP(28.dp)),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.onPrimary,
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) { Icon(icon, desc, modifier = Modifier.size(rDP(14.dp))) }
}

@Composable
private fun ActionProgress(
    icon: ImageVector,
    desc: String,
    show: Boolean,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(show) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = rDP(2.dp)
            )
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(rDP(28.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            AnimatedContent(show) { running ->
                if (running) Icon(Icons.Default.Stop, desc) else Icon(
                    icon,
                    desc,
                    modifier = Modifier.size(rDP(14.dp))
                )
            }
        }
    }
}
