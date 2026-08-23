package com.example.notificationcleaner.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notificationcleaner.data.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppFilterItem(
    val packageName: String,
    val appName: String,
    val isCleanEnabled: Boolean
)

class SettingsViewModel(
    application: Application,
    private val repository: NotificationRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val isLoading = MutableStateFlow(true)

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val myPackage = getApplication<Application>().packageName

            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val apps = installedApps
                .filter { it.packageName != myPackage }
                .map { appInfo ->
                    val name = appInfo.loadLabel(pm).toString()
                    Pair(appInfo.packageName, name)
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }

            _installedApps.value = apps
            isLoading.value = false
        }
    }

    val appList: StateFlow<List<AppFilterItem>> = combine(
        _installedApps,
        repository.allAppFilters,
        _searchQuery
    ) { apps, filters, query ->
        val filterMap = filters.associate { it.packageName to it.isCleanEnabled }
        apps
            .map { (pkg, name) ->
                AppFilterItem(
                    packageName = pkg,
                    appName = name,
                    isCleanEnabled = filterMap[pkg] ?: true
                )
            }
            .filter { item ->
                if (query.isBlank()) true
                else item.appName.contains(query, ignoreCase = true) || item.packageName.contains(query, ignoreCase = true)
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleCleanEnabled(packageName: String, currentEnabled: Boolean) {
        viewModelScope.launch {
            repository.setCleanEnabled(packageName, !currentEnabled)
        }
    }

    fun setAllClean(enabled: Boolean) {
        viewModelScope.launch {
            val allPackages = _installedApps.value.map { it.first }
            repository.setAllCleanEnabled(allPackages, enabled)
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            repository: NotificationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(application, repository) as T
            }
        }
    }
}
