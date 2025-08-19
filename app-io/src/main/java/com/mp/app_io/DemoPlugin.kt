package com.mp.app_io

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dark.plugins.api.PluginApi
import com.dark.plugins.api.PluginInfo


class AppIoPlugin(private val ctx: Context) : PluginApi(ctx) {

    override fun getPluginInfo(): PluginInfo = PluginInfo(
        name = "AppIoPlugin", description = "Browse and launch installed apps"
    )

    @Composable
    override fun AppContent() {
        val context = LocalContext.current.applicationContext
        val owner = checkNotNull(LocalViewModelStoreOwner.current)

        val factory = viewModelFactory {
            initializer {
                AppIoViewModel(context as Application)
            }
        }
        val vm = viewModel<AppIoViewModel>(
            viewModelStoreOwner = owner,
            factory = factory
        )
        LaunchedEffect(Unit) {
            vm.loadApps()
        }

        AppListScreen(
            query = vm.query.collectAsState().value,
            showSystem = vm.showSystem.collectAsState().value,
            apps = vm.filteredApps.collectAsState().value,
            onQueryChange = vm::setQuery,
            onToggleSystem = vm::setShowSystem,
            onLaunch = vm::launchApp
        )
    }
}

data class AppEntry(
    val label: String, val packageName: String, val icon: ImageBitmap?, val isSystem: Boolean
)

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

@Composable
fun AppListScreen(
    query: String,
    showSystem: Boolean,
    apps: List<AppEntry>,
    onQueryChange: (String) -> Unit,
    onToggleSystem: (Boolean) -> Unit,
    onLaunch: (String) -> Unit
) {
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
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search") })
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)
        ) {
            Checkbox(checked = showSystem, onCheckedChange = onToggleSystem)
            Text("Show system apps")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                AppRow(app = app, onLaunch = onLaunch)
            }
        }
    }
}
