# Module: demo‑macro

Demonstrates macro/automation plugin development with a custom Compose UI and rich resources.

## Purpose

* Show how to build and theme an automation plugin using Jetpack Compose, ViewModel, and Android resource pipelines.
* Provide reference backup rules and XML data extraction patterns.

## Entry Point

* `com.mp.macro_plugin_compose.ui.DemoMacroPlugin` (declared in `src/main/Manifest.json`)

## Key Components

* `DemoMacroPlugin.kt` — main plugin UI and logic (Compose)
* `MacroViewModel.kt` — ViewModel backing the plugin UI

## Resources

* Icons and drawables under `res/mipmap-*` and `res/drawable/`
* Theming and values under `res/values/`
* XML backup and data extraction rules under `res/xml/`

## Build and Test

```bash
./gradlew :demo-macro:assemble
./gradlew :demo-macro:test
```

## Notes

* Keep UI logic in Composables thin; move side effects and long‑running work to the ViewModel.
* If the plugin needs permissions, document them in both `AndroidManifest.xml` and `Manifest.json`.

---


