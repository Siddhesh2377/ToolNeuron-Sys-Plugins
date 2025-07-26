package com.mp.app_io

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.plugins.engine.PluginApi
import com.dark.plugins.engine.PluginInfo
import com.dark.plugins.sys.plugins.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AppIoPlugin(private val ctx: Context) : PluginApi(ctx) {

    override fun getPluginInfo(): PluginInfo = PluginInfo(
        name = "AppIoPlugin", description = "Browse and launch installed apps"
    )

    @Composable
    override fun AppContent() {
        AppListScreen(ctx, onLaunch = { pkg ->
            // Try to launch directly
            val pm = ctx.packageManager
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(intent)
                } catch (_: Throwable) { /* ignore */
                }
            } else {
                // Fallback to host AppIO plugin if available
                runCatching {
                    val payload = JSONObject().apply {
                        put("tasks", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("task", "open")
                                put("packageName", pkg)
                            })
                        })
                    }
                    callPlugin("AppIOPlugin", payload)
                }
            }
        })
    }
}

// ---------------- UI ----------------

data class AppEntry(
    val label: String, val packageName: String, val icon: ImageBitmap?, val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(context: Context, onLaunch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val allApps by produceState(initialValue = emptyList(), context) {
        value = listAppsTask(context)
    }

    val filtered = remember(allApps, query, showSystem) {
        val q = query.trim().lowercase()
        allApps.asSequence().filter { showSystem || !it.isSystem }.filter {
                q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase()
                    .contains(q)
            }.sortedBy { it.label.lowercase() }.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Installed apps",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search") })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
            Text("Show system apps")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.packageName }) { app ->
                AppRow(app = app, onLaunch = onLaunch)
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onLaunch: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {}
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = { onLaunch(app.packageName) }) { Text("Open") }
        }
    }
}

// ---------------- data / helpers ----------------

@SuppressLint("QueryPermissionsNeeded")
fun listAppsTask(
    context: Context,
    includeSystem: Boolean = false
): List<AppEntry> {
    val pm = context.packageManager

    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolves = pm.queryIntentActivities(
        launcherIntent,
        PackageManager.ResolveInfoFlags.of(0)
    )

    val result = ArrayList<AppEntry>(resolves.size)

    for (ri in resolves) {
        val activityInfo = ri.activityInfo ?: continue
        val appInfo = activityInfo.applicationInfo
        val pkg = activityInfo.packageName

        val flags = appInfo.flags
        val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        if (!includeSystem && isSystem) continue

        // ensure it’s actually launchable
        val launchable = pm.getLaunchIntentForPackage(pkg) != null
        if (!launchable) continue

        val label = ri.loadLabel(pm).toString().orEmpty()
        val iconDrawable = runCatching { ri.loadIcon(pm) }.getOrNull()
        val icon = iconDrawable?.let { drawableToBitmap(it).asImageBitmap() }

        result += AppEntry(
            label = label,
            packageName = pkg,
            icon = icon,
            isSystem = isSystem
        )
    }

    // stable alphabetical order
    result.sortBy { it.label.lowercase() }
    return result
}


private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable) return drawable.bitmap
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    drawable.setBounds(0, 0, c.width, c.height)
    drawable.draw(c)
    return bmp
}
