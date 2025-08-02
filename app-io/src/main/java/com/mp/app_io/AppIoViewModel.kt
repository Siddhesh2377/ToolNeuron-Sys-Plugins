package com.mp.app_io

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.plugins.sys.plugins.AppInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppIoViewModel(application: Application) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _showSystem = MutableStateFlow(false)
    val showSystem: StateFlow<Boolean> = _showSystem

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val filteredApps: StateFlow<List<AppEntry>> = combine(_allApps, _query, _showSystem) {
            apps, q, showSys ->
        val queryLower = q.lowercase()
        apps.filter {
            (showSys || !it.isSystem) &&
                    (queryLower.isBlank() || it.label.lowercase().contains(queryLower) || it.packageName.lowercase().contains(queryLower))
        }.sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setShowSystem(b: Boolean) {
        _showSystem.value = b
    }

    fun loadApps() {
        viewModelScope.launch {
            _allApps.value = listAppsTask(getApplication())
        }
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
