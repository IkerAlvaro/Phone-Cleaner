package com.ikeralvaro.phonecleaner.feature.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.system.BatterySnapshot
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import com.ikeralvaro.phonecleaner.feature.apps.AppsLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatteryUiState(
    val battery: BatterySnapshot? = null,
    /** Apps de usuario que arrancan solas al encender el móvil (pueden gastar batería sin abrirlas). */
    val autostartApps: List<AppEntry> = emptyList(),
    val unrestrictedApps: List<AppEntry> = emptyList(),
    val loadingApps: Boolean = true,
)

class BatteryViewModel(private val container: AppContainer) : ViewModel() {
    private val apps = MutableStateFlow(BatteryUiState())

    val state: StateFlow<BatteryUiState> = combine(
        container.batteryReader.observe().onStart { container.batteryReader.read()?.let { emit(it) } },
        apps,
    ) { battery, s -> s.copy(battery = battery) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), BatteryUiState())

    fun loadApps() {
        viewModelScope.launch {
            val all = container.appRepository.loadApps()
            apps.value = BatteryUiState(
                autostartApps = AppsLogic.autostartApps(all),
                unrestrictedApps = all.filter { it.ignoresBatteryOptimizations && !it.isSystem }.sortedBy { it.label.lowercase() },
                loadingApps = false,
            )
        }
    }
}
