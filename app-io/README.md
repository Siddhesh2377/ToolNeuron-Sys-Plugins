# Module: app‑io

Provides application IO helpers and a demonstration plugin with ViewModel‑driven Compose screens.

## Purpose

* Offer IO primitives for other plugins and the host (e.g., file pickers, cache helpers).
* Showcase a minimal plugin that uses these utilities.

## Entry Point

* `com.mp.app_io.AppIoPlugin` (declared in `src/main/Manifest.json`)

## Key Components

* `DemoPlugin.kt` — plugin bootstrap and registration
* `AppIoViewModel.kt` — state holder and IO flow management

## Build and Test

```bash
./gradlew :app-io:assemble
./gradlew :app-io:test
```

## Notes

* Avoid accessing external storage without explicit user action; prefer SAF.
* Expose IO operations via small, testable functions.

---
