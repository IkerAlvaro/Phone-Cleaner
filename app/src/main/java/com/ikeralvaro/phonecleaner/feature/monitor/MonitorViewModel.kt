package com.ikeralvaro.phonecleaner.feature.monitor

import androidx.compose.runtime.Immutable
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ikeralvaro.phonecleaner.AppContainer
import com.ikeralvaro.phonecleaner.core.system.BatterySnapshot
import com.ikeralvaro.phonecleaner.core.system.CpuSnapshot
import com.ikeralvaro.phonecleaner.core.system.MemorySnapshot
import com.ikeralvaro.phonecleaner.core.system.StorageSnapshot
import com.ikeralvaro.phonecleaner.core.system.ThermalSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class DeviceInfo(val model: String, val androidVersion: String, val uptimeMs: Long)

@Immutable
data class MonitorUiState(
    val cpu: CpuSnapshot? = null,
    val thermal: ThermalSnapshot? = null,
    val memory: MemorySnapshot? = null,
    val storage: StorageSnapshot? = null,
    val battery: BatterySnapshot? = null,
    val device: DeviceInfo? = null,
)

class MonitorViewModel(private val container: AppContainer) : ViewModel() {

    private val ticker = flow {
        var tick = 0
        var storage: StorageSnapshot? = null
        while (true) {
            // El almacenamiento cambia despacio: se lee cada 10 s.
            if (tick % 10 == 0) storage = container.storageReader.read()
            emit(
                MonitorUiState(
                    cpu = container.cpuReader.read(),
                    thermal = container.thermalReader.read(),
                    memory = container.memoryReader.read(),
                    storage = storage,
                    device = DeviceInfo(
                        model = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
                            .distinct().joinToString(" "),
                        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        uptimeMs = SystemClock.elapsedRealtime(),
                    ),
                ),
            )
            tick++
            delay(1_000)
        }
    }.flowOn(Dispatchers.IO)

    private val battery = container.batteryReader.observe()
        .onStart { container.batteryReader.read()?.let { emit(it) } }

    /** Se actualiza cada segundo solo mientras la pantalla está visible. */
    val state: StateFlow<MonitorUiState> = combine(ticker, battery) { s, b -> s.copy(battery = b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), MonitorUiState())
}
