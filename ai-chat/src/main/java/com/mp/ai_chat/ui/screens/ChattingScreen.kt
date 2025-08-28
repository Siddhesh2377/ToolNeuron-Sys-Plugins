package com.mp.ai_chat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
fun ChattingScreen(
    pluginApi: PluginApi,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory(pluginApi))
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val files by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val liveAnswer by viewModel.liveAnswer.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var input by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.handleFileUri(context, it) }
    }

    val listState = rememberLazyListState()

    // Auto-scroll on new messages / stream
    LaunchedEffect(messages.size, liveAnswer) {
        scrollToBottom(listState, messages.size, liveAnswer.isNotBlank())
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
        Column(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(pad),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(rDP(10.dp))
            ) {
                itemsIndexed(messages, key = { idx, msg -> msg.timeStamp + "-" + idx }) { _, m ->
                    ChatBubble(msg = m)
                }
                // Ephemeral streaming bubble while generating
                if (liveAnswer.isNotBlank()) {
                    item(key = "live-bubble") {
                        AssistantStreamingBubble(liveAnswer)
                    }
                }
            }

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
        }
    }
}

private suspend fun scrollToBottom(state: LazyListState, count: Int, hasLive: Boolean) {
    val last = (count + if (hasLive) 1 else 0).coerceAtLeast(0)
    if (last > 0) state.scrollToItem(last - 1)
}

// ---------------------------------------------------------------------
// Chat bubble — supports <think> ... </think> folding
// ---------------------------------------------------------------------
@Composable
private fun ChatBubble(msg: Message) {
    val isUser = msg.role == ROLE.USER
    val think = msg.content.startsWith("<think>")

    val raw = msg.content.trim()
    val cleanThinking = remember(raw) {
        if (think) {
            val withoutOpen = raw.removePrefix("<think>").trimStart()
            if (withoutOpen.endsWith("</think>")) withoutOpen.removeSuffix("</think>").trimEnd() else withoutOpen
        } else ""
    }

    val actualResponse = remember(raw) {
        if (think && raw.contains("</think>")) raw.substringAfter("</think>").trimStart()
        else if (!think) raw else ""
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (think) ThinkingBubble(msg.copy(content = cleanThinking))
        MarkdownText(
            text = actualResponse.trim(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier
                .then(if (isUser) Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large) else Modifier)
                .widthIn(max = rDP(320.dp))
                .padding(vertical = rDP(8.dp), horizontal = rDP(14.dp))
        )
    }
}

@Composable
private fun AssistantStreamingBubble(live: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        MarkdownText(
            text = live,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
                .widthIn(max = rDP(320.dp))
                .padding(vertical = rDP(8.dp), horizontal = rDP(14.dp))
        )
    }
}

@Composable
fun ThinkingBubble(message: Message) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = rDP(16.dp))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { expanded = !expanded }
                .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
                .padding(rDP(10.dp))
        ) {
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(rDP(16.dp)))
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text(text = if (expanded) "AI Reasoning (tap to hide)" else "AI is thinking…", style = MaterialTheme.typography.labelMedium)
        }
        AnimatedVisibility(visible = expanded) {
            MarkdownText(
                text = message.content,
                canCopy = message.role != ROLE.USER,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(rDP(12.dp))
            )
        }
    }
}

// ---------------------------------------------------------------------
// Bottom bar — inline attachment chips + stop/send
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
    Column(modifier = Modifier.padding(rDP(6.dp))) {
        // Inline chips row (above text field)
        AnimatedVisibility(attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(rDP(6.dp)), modifier = Modifier.padding(horizontal = rDP(12.dp), vertical = rDP(4.dp))) {
                itemsIndexed(attachments, key = { _, a -> a.id }) { idx, item ->
                    FileChip(item, onRemove = { onRemove(idx) })
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rDP(16.dp))
                .clip(MaterialTheme.shapes.medium)
                .background(color = MaterialTheme.colorScheme.primary)
                .heightIn(max = 320.dp)
                .padding(vertical = rDP(8.dp))
                .padding(start = rDP(12.dp), end = rDP(10.dp)),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(rDP(10.dp))
        ) {
            Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                BasicTextField(
                    value = text,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
                    onValueChange = onTextChange,
                    singleLine = false,
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            val suffix = if (attachments.isNotEmpty()) "  •  Attached: ${attachments.size}" else ""
                            Text(
                                "Say anything…$suffix",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xCCFFFFFF)
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Action(icon = Icons.Default.AttachFile, desc = "Attach", onClick = onAttach)
            ActionProgress(icon = Icons.AutoMirrored.TwoTone.Send, desc = if (isGenerating) "Stop" else "Send", show = isGenerating, onClick = onSend)
        }
    }
}

@Composable
private fun FileChip(f: FileAttachment, onRemove: () -> Unit) {
    var showPreview by remember { mutableStateOf(false) }
    val type = if (f.doc.type.isNullOrEmpty()) "file" else f.doc.type.lowercase()
    val icon = when {
        type.contains("pdf") -> Icons.Default.PictureAsPdf
        type.contains("doc") || type.contains("word") -> Icons.Default.Description
        type.contains("xls") || type.contains("sheet") -> Icons.Default.TableChart
        type.contains("ppt") -> Icons.Default.Slideshow
        else -> Icons.Default.InsertDriveFile
    }

    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .clickable(enabled = !f.isLoading) { showPreview = true }
            .padding(horizontal = rDP(8.dp), vertical = rDP(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(rDP(14.dp)))
        Spacer(Modifier.width(rDP(6.dp)))
        Text(f.doc.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Spacer(Modifier.width(rDP(6.dp)))
        if (f.isLoading) CircularProgressIndicator(modifier = Modifier.size(rDP(12.dp)), strokeWidth = rDP(2.dp))
        else Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(rDP(14.dp)).clickable { onRemove() })
    }

    if (showPreview) FilePreviewDialog(title = f.doc.name, body = if (f.preview.isNotBlank()) f.preview else "No preview available", onDismiss = { showPreview = false })
}

@Composable
private fun FilePreviewDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Serif) }
    )
}

// ---------------------------------------------------------------------
// Small reusable icon buttons
// ---------------------------------------------------------------------
@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
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
private fun ActionProgress(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, show: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        AnimatedVisibility(show) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = rDP(2.dp))
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(rDP(28.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            AnimatedContent(show, label = "sendStop") { running ->
                if (running) Icon(Icons.Default.Stop, desc) else Icon(icon, desc, modifier = Modifier.size(rDP(14.dp)))
            }
        }
    }
}