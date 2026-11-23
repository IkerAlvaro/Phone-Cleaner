package com.ikeralvaro.phonecleaner.feature.apps

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.util.IntentQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val sort: AppSort = AppSort.SIZE,
    val selected: Set<String> = emptySet(),
    val now: Long = System.currentTimeMillis(),
) {
    /** Se ordena una vez al crear el estado (cuando cambian las apps o el orden), no en cada recomposición. */
    val visibleApps: List<AppEntry> = AppsLogic.sort(apps, sort)
}

class AppsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()
    private val queue = IntentQueue<String>()

    private var loadJob: Job? = null

    /** Una sola carga a la vez: las llamadas repetidas mientras carga se ignoran. */
    fun load(forceRefresh: Boolean = false) {
        if (queue.isRunning || loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val apps = container.appRepository.loadApps(forceRefresh = forceRefresh)
            val installed = apps.map { it.packageName }.toSet()
            _state.update {
                it.copy(loading = false, apps = apps, selected = it.selected intersect installed, now = System.currentTimeMillis())
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
            // Puede que se haya desinstalado algo: se descarta la lista guardada.
            container.appRepository.invalidate()
            load(forceRefresh = true)
        }
        return next
    }
}
