package com.ikeralvaro.phonecleaner.feature.boost

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.system.MemorySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BoostPhase { IDLE, RUNNING, DONE }

@Immutable
data class BoostUiState(
    val phase: BoostPhase = BoostPhase.IDLE,
    val memory: MemorySnapshot? = null,
    /** RAM usada en directo mientras se libera (sube al llenar y baja al soltar). */
    val liveUsedFraction: Float? = null,
    val stage: BoostStage? = null,
    val freedBytes: Long = 0L,
) {
    val usedFraction: Float get() = liveUsedFraction ?: memory?.usedFraction ?: 0f
}

class BoostViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BoostUiState())
    val state: StateFlow<BoostUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        viewModelScope.launch {
            val memory = withContext(Dispatchers.IO) { container.memoryReader.read() }
            _state.update { it.copy(memory = memory) }
        }
    }

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(phase = BoostPhase.RUNNING, stage = BoostStage.FILLING) }
        job = viewModelScope.launch {
            val result = container.memoryBooster.boost { progress ->
                _state.update { it.copy(stage = progress.stage, liveUsedFraction = progress.usedFraction) }
            }
            val memory = withContext(Dispatchers.IO) { container.memoryReader.read() }
            _state.update {
                it.copy(phase = BoostPhase.DONE, memory = memory, liveUsedFraction = null, stage = null, freedBytes = result.freedBytes)
            }
        }
    }

    /**
     * Para ON_STOP de la pantalla: cancela solo si la actividad no se está recreando por un cambio de
     * configuración (girar la pantalla, modo oscuro...). Si el ViewModel se destruye de verdad, viewModelScope
     * se cancela en onCleared y el booster suelta la memoria igualmente.
     */
    fun cancelIfNotChangingConfigurations(activity: Activity?) {
        if (activity?.isChangingConfigurations == true) return
        cancel()
    }

    /** Al salir de la pantalla se cancela: el booster suelta la memoria en su bloque finally. */
    fun cancel() {
        if (job?.isActive != true) return
        job?.cancel()
        _state.update { it.copy(phase = BoostPhase.IDLE, liveUsedFraction = null, stage = null) }
    }
}
