package com.mp.ai_chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.plugins.api.PluginApi
import com.dark.plugins.ui.theme.rDP
import com.mp.ai_chat.model.FileAttachment
import com.mp.ai_chat.model.Message
import com.mp.ai_chat.model.ROLE
import com.mp.ai_chat.ui.components.MarkdownText
import com.mp.ai_chat.viewmodel.ChatViewModel

@Composable
fun ChattingScreen(pluginApi: PluginApi, viewModel: ChatViewModel = viewModel(
    factory = ChatViewModel.provideFactory(pluginApi)
)) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val files by viewModel.attachedFiles.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
          //  viewModel.attach(FileAttachment(doc = it, isLoading = false))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(pad),
                verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
            ) {
                items(messages.size) { idx ->
                    ChatBubble(msg = messages[idx])
                }
            }

            BottomBarContent(
                text = input,
                onTextChange = { input = it },
                attachments = files,
                isGenerating = isGenerating,
                onAttach = { picker.launch("*/*") },
                onSend = {
                    if (isGenerating) {
                        viewModel.stop()
                    } else if (input.isNotBlank()) {
                        viewModel.send(input.trim())
                        input = ""
                    }
                },
                onRemove = viewModel::clearAttachment
            )
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

    val raw = msg.content.trim()
    val cleanThinking = remember(raw) {
        if (think) {
            val withoutOpen = raw.removePrefix("<think>").trimStart()
            if (withoutOpen.endsWith("</think>")) {
                withoutOpen.removeSuffix("</think>").trimEnd()
            } else {
                withoutOpen
            }
        } else ""
    }

    val actualResponse = remember(raw) {
        if (think && raw.contains("</think>")) {
            // Extract actual response that comes after </think>
            raw.substringAfter("</think>").trimStart()
        } else if (!think) {
            // Normal system message
            raw
        } else {
            "" // if still thinking and no closing tag yet
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (think) ThinkingBubble(msg.copy(content = cleanThinking)) else Unit
        MarkdownText(
            text = actualResponse.trim(),
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
fun ThinkingBubble(message: Message) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rDP(16.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(rDP(10.dp))) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Expand",
                modifier = Modifier.size(rDP(16.dp))
            )
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text(
                text = if (expanded) "AI Reasoning (tap to hide)" else "AI is thinking...",
                style = MaterialTheme.typography.labelMedium
            )
        }

        AnimatedVisibility(visible = expanded) {
            MarkdownText(
                text = message.content,
                canCopy = message.role != ROLE.USER,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDP(12.dp))
            )
        }
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
                .padding(horizontal = rDP(16.dp))
                .clip(MaterialTheme.shapes.medium)
                .background(color = MaterialTheme.colorScheme.primary)
                .heightIn(max = 400.dp)
                .padding(vertical = rDP(8.dp))
                .padding(start = rDP(12.dp), end = rDP(10.dp)),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = text,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
                    onValueChange = onTextChange,
                    singleLine = false,
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                "Say Anything...",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                        innerTextField()
                    })
            }
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
            modifier = Modifier.size(rDP(12.dp)), strokeWidth = rDP(2.dp)
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
    icon: ImageVector, desc: String, show: Boolean, onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(show) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = rDP(2.dp)
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
                    icon, desc, modifier = Modifier.size(rDP(14.dp))
                )
            }
        }
    }
}