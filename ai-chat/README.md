# Module: ai‑chat

AI‑powered chat plugin providing a Compose chat surface, message formatting, and renderer utilities.

## Purpose

* Add an AI chat experience as a plugin component that a host can embed.

## Entry Point

* `com.mp.ai_chat.ChatScreenPlugin` (declared in `src/main/Manifest.json`)

## Key Components

* `ChattingScreen.kt` — primary Composable and interaction logic
* `ChatScreenPlugin.kt` — plugin initialization and contract implementation
* `AiMessage.kt` — message data model
* `RichText.kt` — utilities for rendering and formatting chat messages

## Build and Test

```bash
./gradlew :ai-chat:assemble
./gradlew :ai-chat:test
```

## Notes

* Ensure message rendering is resilient to long texts and code blocks.
* Keep the data model stable; add mappers for host‑specific formats.
