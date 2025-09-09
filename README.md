# ToolNeuron-Sys Plugins Project

Modular plugin set for the NeuroVerse ecosystem. Each plugin is an Android library that can be built, tested, and packaged independently, then loaded by a host app via a lightweight Plugin API and a `Manifest.json` entry file.

---

## Project Overview

Neuro‑V‑Sys is a multi‑module Gradle project containing default, first‑party plugins:

* **ai-chat** — AI‑powered chat surface and renderer
* **app-io** — Application IO utilities and demo plugin
* **demo-macro** — Macro/automation demo with custom Compose UI and resources

These modules are intended to be consumed by a host app (e.g., NeuroVerse) and distributed as packaged plugin zips.

---

## Repository Structure

```
<root>/
├─ ai-chat/
├─ app-io/
├─ demo-macro/
├─ gradle/
├─ build.gradle.kts
├─ settings.gradle.kts
└─ README.md
```

Each module follows this internal layout:

```
module/
├─ libs/                         # Local AARs, optional
├─ src/
│  ├─ main/
│  │  ├─ java/com/mp/<package>/  # Source
│  │  ├─ AndroidManifest.xml     # If needed
│  │  ├─ Manifest.json           # Plugin metadata (required)
│  │  └─ res/                    # Resources (optional; demo-macro uses this)
│  ├─ test/                      # Unit tests
│  └─ androidTest/               # Instrumentation tests
├─ build.gradle.kts
├─ proguard-rules.pro
└─ consumer-rules.pro
```

---

## Plugin Manifest

Each plugin declares a minimal JSON manifest so the host can discover and instantiate it at runtime.

**`src/main/Manifest.json` schema**

```json
{
  "id": "com.mp.plugin.ai_chat",          
  "name": "AI Chat",
  "version": "0.1.0",
  "entryClass": "com.mp.ai_chat.ChatScreenPlugin",
  "minSdk": 26,
  "targetSdk": 34,
  "description": "AI chat UI and renderer",
  "permissions": ["android.permission.RECORD_AUDIO"],
  "ui": { "hasCompose": true }
}
```

**Required fields**: `id`, `name`, `version`, `entryClass`
**Optional**: `minSdk`, `targetSdk`, `description`, `permissions`, `ui`

---

## Building and Packaging

Build a module AAR:

```bash
./gradlew :ai-chat:assembleRelease
```

For runtime loading, package the plugin into a zip containing:

```
plugin.zip
├─ plugin.aar
├─ Manifest.json
└─ LICENSE (optional)
```

> If you already have an export task in your build, keep it; otherwise we can add a simple Gradle `Zip` task per module. Ask if you want me to wire this for each module.

---

## Development Notes

* Each plugin is an Android library; run unit tests with `testDebugUnitTest` and instrumentation with `connectedAndroidTest`.
* The host app is responsible for class loading, lifecycle, and dependency injection boundaries.
* Keep public surface areas small: one entry class plus a minimal plugin contract.

---

## Contribution Guidelines

1. Create a feature branch per plugin.
2. Follow Kotlin style guidelines and prefer Compose first‑party patterns.
3. Update the module README and `Manifest.json` when you add capabilities.
4. Include tests where possible.

---
