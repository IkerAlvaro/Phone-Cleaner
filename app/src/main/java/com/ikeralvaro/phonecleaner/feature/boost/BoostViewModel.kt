package com.ikeralvaro.phonecleaner.feature.boost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.system.MemorySnapshot
import com.ikeralvaro.phonecleaner.core.util.IntentQueue
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BoostPhase { LOADING, READY, WORKING, QUICK_STOP, DONE }

data class BoostUiState(
    val phase: BoostPhase = BoostPhase.LOADING,
    val memory: MemorySnapshot? = null,
    val candidates: List<AppEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val canStopOtherApps: Boolean = true,
    val hasUsageAccess: Boolean = true,
    val freedBytes: Long = 0L,
    val quickStopPosition: Int = 0,
    val quickStopTotal: Int = 0,
    val quickStopLabel: String? = null,
)

class BoostViewModel(private val container: AppContainer) : ViewModel() {
    private val booster = container.ramBooster
    private val queue = IntentQueue<AppEntry>()
    private var availableBefore = 0L

    private val _state = MutableStateFlow(BoostUiState(canStopOtherApps = booster.canStopOtherApps))
    val state: StateFlow<BoostUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.phase == BoostPhase.WORKING || _state.value.phase == BoostPhase.QUICK_STOP) return
        viewModelScope.launch {
            val hasUsage = container.permissionManager.hasUsageAccess()
            val memory = withContext(Dispatchers.IO) { container.memoryReader.read() }
            val candidates = booster.candidates(hasUsage)
            _state.update { s ->
                s.copy(
                    phase = if (s.phase == BoostPhase.DONE) BoostPhase.DONE else BoostPhase.READY,
                    memory = memory,
                    candidates = candidates,
                    selected = candidates.map { it.packageName }.toSet(),
                    hasUsageAccess = hasUsage,
                )
            }
        }
    }

    fun toggle(packageName: String) = _state.update { s ->
        s.copy(selected = if (packageName in s.selected) s.selected - packageName else s.selected + packageName)
    }

    /** Android 13 o inferior. */
    fun stopInBackground() {
        val packages = _state.value.selected.toList()
        if (packages.isEmpty()) return
        _state.update { it.copy(phase = BoostPhase.WORKING) }
        viewModelScope.launch {
            val freed = booster.stopBackground(packages)
            finish(freed)
        }
    }

    /** Android 14+: devuelve el primer paquete cuya ficha hay que abrir. */
    fun startQuickStop(): String? {
        val selected = _state.value.candidates.filter { it.packageName in _state.value.selected }
        val first = queue.start(selected) ?: return null
        availableBefore = booster.availableBytes()
        publishQueue()
        return first.packageName
    }

    /** Al volver de los Ajustes de una app: devuelve el siguiente paquete o null si se ha terminado. */
    fun onReturnedFromSettings(): String? {
        val next = queue.next()
        if (next != null) {
            publishQueue()
            return next.packageName
        }
        viewModelScope.launch {
            delay(RamBooster.SETTLE_MS)
            finish((booster.availableBytes() - availableBefore).coerceAtLeast(0L))
        }
        return null
    }

    private fun publishQueue() = _state.update {
        it.copy(
            phase = BoostPhase.QUICK_STOP,
            quickStopPosition = queue.position,
            quickStopTotal = queue.size,
            quickStopLabel = queue.current?.label,
        )
    }

    private suspend fun finish(freed: Long) {
        val memory = withContext(Dispatchers.IO) { container.memoryReader.read() }
        _state.update { it.copy(phase = BoostPhase.DONE, freedBytes = freed, memory = memory) }
        load()
    }
}
