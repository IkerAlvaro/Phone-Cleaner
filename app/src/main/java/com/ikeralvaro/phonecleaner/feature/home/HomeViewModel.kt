package com.ikeralvaro.phonecleaner.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.system.BatterySnapshot
import com.ikeralvaro.phonecleaner.core.system.MemorySnapshot
import com.ikeralvaro.phonecleaner.core.system.StorageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val storage: StorageSnapshot? = null,
    val memory: MemorySnapshot? = null,
    val battery: BatterySnapshot? = null,
    val lastCleanAt: Long = 0L,
    val totalFreedBytes: Long = 0L,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val system = MutableStateFlow(HomeUiState())

    val state: StateFlow<HomeUiState> = combine(system, container.settingsRepository.settings) { s, settings ->
        s.copy(lastCleanAt = settings.lastCleanAt, totalFreedBytes = settings.totalFreedBytes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            system.value = system.value.copy(
                storage = container.storageReader.read(),
                memory = container.memoryReader.read(),
                battery = container.batteryReader.read(),
            )
        }
    }
}
