package com.ikeralvaro.phonecleaner.feature.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.system.BatterySnapshot
import com.ikeralvaro.phonecleaner.feature.apps.AppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatteryUiState(
    val battery: BatterySnapshot? = null,
    val topApps: List<AppEntry> = emptyList(),
    val unrestrictedApps: List<AppEntry> = emptyList(),
    val hasUsageAccess: Boolean = true,
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
            val hasUsage = container.permissionManager.hasUsageAccess()
            val all = container.appRepository.loadApps()
            apps.value = BatteryUiState(
                topApps = all.filter { it.foregroundMs24h > 60_000 }.sortedByDescending { it.foregroundMs24h }.take(8),
                unrestrictedApps = all.filter { it.ignoresBatteryOptimizations && !it.isSystem }.sortedBy { it.label.lowercase() },
                hasUsageAccess = hasUsage,
                loadingApps = false,
            )
        }
    }
}
