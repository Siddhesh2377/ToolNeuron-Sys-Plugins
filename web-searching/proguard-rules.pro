# Keep the plugin class and everything in it
-keep class com.mp.web_searching.WebPlugin {
    public <init>(android.content.Context);
    *;
}
-keep class com.dark.plugins.api.** { *; }


# Preserve the entire ComposePlugin interface and its implementations (including content())
-keep interface com.dark.plugins.api.ComposePlugin { *; }
-keepclassmembers class * implements com.dark.plugins.api.ComposePlugin {
    * content(...);
}
-keepclassmembers class * implements com.dark.plugins.api.ComposePlugin {
    public *** content(...);
}

-keepattributes *Annotation*
-keep @androidx.annotation.Keep class * { *; }

# In case PluginApi is referenced directly
-keep class com.dark.plugins.api.PluginApi { *; }

# Preserve Kotlin runtime as before
-keep class kotlin.jvm.internal.** { *; }
-dontwarn kotlin.jvm.internal.**
-keep class kotlin.** { *; }
-dontwarn kotlin.**
