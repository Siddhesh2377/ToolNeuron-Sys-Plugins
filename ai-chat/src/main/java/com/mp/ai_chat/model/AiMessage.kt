package com.mp.ai_chat.model


import com.dark.plugins.model.Document
import kotlinx.serialization.Serializable
import java.util.UUID


@Serializable
data class Message(
    val role: ROLE,
    var content: String,
    val timeStamp: String,
    val document: MutableList<FileAttachment> = mutableListOf()
)


@Serializable
enum class ROLE { USER, SYSTEM }


@Serializable
data class FileAttachment(
    val id: String = UUID.randomUUID().toString(),
    val doc: Document = Document("", "", "", ""),
    val isLoading: Boolean = true,
// Short preview used by UI chips (keeps Compose diffs light)
    val preview: String = ""
)