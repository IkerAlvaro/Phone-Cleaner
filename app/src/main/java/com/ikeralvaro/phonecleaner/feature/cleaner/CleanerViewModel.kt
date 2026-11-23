package com.ikeralvaro.phonecleaner.feature.cleaner

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CleanerPhase { IDLE, SCANNING, RESULTS, CLEANING, DONE }

data class JunkGroup(val category: JunkCategory, val items: List<JunkItem>) {
    val totalBytes: Long = items.sumOf { it.sizeBytes }
}

/** Resultado de "Vaciar caché de todas las apps": lo liberado se mide con el espacio libre antes y después. */
data class AppCacheState(val freedBytes: Long? = null)

@Immutable
data class CleanerUiState(
    val phase: CleanerPhase = CleanerPhase.IDLE,
    val progress: ScanProgress? = null,
    val groups: List<JunkGroup> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val expanded: Set<JunkCategory> = emptySet(),
    val appCache: AppCacheState = AppCacheState(),
    val hasFilesAccess: Boolean = false,
    val freedBytes: Long = 0L,
) {
    // Se calculan una vez por estado (no en cada recomposición).
    val totalFoundBytes: Long = groups.sumOf { it.totalBytes }
    val selectedItems: List<JunkItem> = groups.flatMap { it.items }.filter { it.path in selectedPaths }
    val selectedBytes: Long = selectedItems.sumOf { it.sizeBytes }
    val selectedCount: Int = selectedItems.size
}

class CleanerViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CleanerUiState())
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var freeBeforeCacheClear: Long? = null

    fun startScan() {
        if (job?.isActive == true) return
        val permissions = container.permissionManager.snapshot()
        _state.value = CleanerUiState(
            phase = CleanerPhase.SCANNING,
            hasFilesAccess = permissions.allFiles,
        )
        job = viewModelScope.launch {
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

    /** Justo antes de abrir la pantalla del sistema "Vaciar caché": se anota el espacio libre. */
    fun beforeAppCacheClear() {
        viewModelScope.launch {
            freeBeforeCacheClear = withContext(Dispatchers.IO) { container.storageReader.read().freeBytes }
        }
    }

    /**
     * Al volver: se mide de nuevo el espacio libre y se calcula lo liberado de verdad.
     * [resultOk]: el sistema devolvió RESULT_OK (si el usuario canceló no se registra nada).
     */
    fun onAppCacheCleared(resultOk: Boolean = true) {
        val before = freeBeforeCacheClear ?: return
        freeBeforeCacheClear = null
        if (!resultOk) return
        viewModelScope.launch {
            delay(1_000) // el sistema termina de borrar en segundo plano
            val after = withContext(Dispatchers.IO) { container.storageReader.read().freeBytes }
            val freed = (after - before).coerceAtLeast(0L)
            container.settingsRepository.recordClean(freed)
            _state.update { it.copy(appCache = AppCacheState(freedBytes = freed)) }
        }
    }
}
