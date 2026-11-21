package com.ikeralvaro.phonecleaner.feature.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CleanerPhase { IDLE, SCANNING, RESULTS, CLEANING, DONE }

data class JunkGroup(val category: JunkCategory, val items: List<JunkItem>) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

data class AppCacheState(
    val totalBytes: Long,
    val apps: List<AppEntry>,
    val freedBytes: Long? = null,
)

data class CleanerUiState(
    val phase: CleanerPhase = CleanerPhase.IDLE,
    val progress: ScanProgress? = null,
    val groups: List<JunkGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val expanded: Set<JunkCategory> = emptySet(),
    val appCache: AppCacheState? = null,
    val hasFilesAccess: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val freedBytes: Long = 0L,
) {
    val totalFoundBytes: Long get() = groups.sumOf { it.totalBytes } + (appCache?.totalBytes ?: 0L)
    val selectedItems: List<JunkItem> get() = groups.flatMap { it.items }.filter { it.path in selectedPaths }
    val selectedBytes: Long get() = selectedItems.sumOf { it.sizeBytes }
}

class CleanerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CleanerUiState())
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun startScan() {
        if (job?.isActive == true) return
        val permissions = container.permissionManager.snapshot()
        _state.value = CleanerUiState(
            phase = CleanerPhase.SCANNING,
            hasFilesAccess = permissions.allFiles,
            hasUsageAccess = permissions.usage,
        )
        job = viewModelScope.launch {
            val cache = async { if (permissions.usage) loadAppCache() else null }
            val found = if (permissions.allFiles) {
                container.junkScanner.scan { progress -> _state.update { it.copy(progress = progress) } }
            } else {
                emptyMap()
            }
            val groups = JunkCategory.entries.mapNotNull { category ->
                found[category]?.takeIf { it.isNotEmpty() }?.let { JunkGroup(category, it) }
            }
            val preselected = groups.filter { it.category.preselected }.flatMap { g -> g.items.map { it.path } }.toSet()
            _state.update {
                it.copy(
                    phase = CleanerPhase.RESULTS,
                    groups = groups,
                    selectedPaths = preselected,
                    appCache = cache.await(),
                )
            }
        }
    }

    fun toggleCategory(category: JunkCategory) = _state.update { s ->
        val paths = s.groups.firstOrNull { it.category == category }?.items?.map { it.path }.orEmpty()
        val allSelected = paths.all { it in s.selectedPaths }
        s.copy(selectedPaths = if (allSelected) s.selectedPaths - paths.toSet() else s.selectedPaths + paths)
    }

    fun toggleItem(path: String) = _state.update { s ->
        s.copy(selectedPaths = if (path in s.selectedPaths) s.selectedPaths - path else s.selectedPaths + path)
    }

    fun toggleExpanded(category: JunkCategory) = _state.update { s ->
        s.copy(expanded = if (category in s.expanded) s.expanded - category else s.expanded + category)
    }

    fun clean() {
        val items = _state.value.selectedItems
        if (items.isEmpty() || job?.isActive == true) return
        _state.update { it.copy(phase = CleanerPhase.CLEANING) }
        job = viewModelScope.launch {
            val freed = container.junkScanner.delete(items)
            container.settingsRepository.recordClean(freed)
            _state.update { it.copy(phase = CleanerPhase.DONE, freedBytes = freed) }
        }
    }

    /** Tras la pantalla del sistema "Vaciar caché": vuelve a medir y calcula lo liberado de verdad. */
    fun onAppCacheCleared() {
        val before = _state.value.appCache?.totalBytes ?: return
        viewModelScope.launch {
            val after = loadAppCache()
            val freed = (before - after.totalBytes).coerceAtLeast(0L)
            container.settingsRepository.recordClean(freed)
            _state.update { it.copy(appCache = after.copy(freedBytes = freed)) }
        }
    }

    private suspend fun loadAppCache(): AppCacheState {
        val apps = container.appRepository.loadApps()
            .filter { (it.cacheBytes ?: 0L) > 0L }
            .sortedByDescending { it.cacheBytes }
        return AppCacheState(apps.sumOf { it.cacheBytes ?: 0L }, apps)
    }
}
