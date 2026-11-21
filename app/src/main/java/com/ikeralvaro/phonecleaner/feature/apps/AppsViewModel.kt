package com.ikeralvaro.phonecleaner.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.util.IntentQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val sort: AppSort = AppSort.SIZE,
    val selected: Set<String> = emptySet(),
    val hasUsageAccess: Boolean = true,
    val now: Long = System.currentTimeMillis(),
) {
    val visibleApps: List<AppEntry> get() = AppsLogic.sort(apps, sort, now)
}

class AppsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()
    private val queue = IntentQueue<String>()

    fun load() {
        if (queue.isRunning) return
        viewModelScope.launch {
            val hasUsage = container.permissionManager.hasUsageAccess()
            val apps = container.appRepository.loadApps()
            val installed = apps.map { it.packageName }.toSet()
            _state.update {
                it.copy(
                    loading = false,
                    apps = apps,
                    hasUsageAccess = hasUsage,
                    selected = it.selected intersect installed,
                    now = System.currentTimeMillis(),
                )
            }
        }
    }

    fun setSort(sort: AppSort) = _state.update { it.copy(sort = sort) }

    fun toggle(packageName: String) = _state.update { s ->
        s.copy(selected = if (packageName in s.selected) s.selected - packageName else s.selected + packageName)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Empieza a recorrer las apps seleccionadas (detener o desinstalar). Devuelve la primera. */
    fun startSequence(): String? = queue.start(_state.value.selected.toList())

    fun onReturned(): String? {
        val next = queue.next()
        if (next == null) {
            queue.cancel()
            clearSelection()
            load()
        }
        return next
    }
}
