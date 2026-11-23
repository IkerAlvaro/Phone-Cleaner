package com.ikeralvaro.phonecleaner.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ChargeStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }
enum class PowerSource { AC, USB, WIRELESS, DOCK, NONE }
enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, FAILURE, UNKNOWN }

data class BatterySnapshot(
    val levelPercent: Int,
    val status: ChargeStatus,
    val source: PowerSource,
    val health: BatteryHealth,
    val temperatureC: Float?,
    val voltageMv: Int?,
    val currentMa: Int?,
    val estimatedCapacityMah: Int?,
    val cycleCount: Int?,
    val technology: String?,
)

/** Conversiones puras de los valores que da Android (probadas en tests unitarios). */
object BatteryMath {
    fun statusFromCode(code: Int): ChargeStatus = when (code) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
        else -> ChargeStatus.UNKNOWN
    }

    fun sourceFromPlugged(plugged: Int): PowerSource = when {
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PowerSource.AC
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PowerSource.USB
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PowerSource.WIRELESS
        plugged and 8 != 0 -> PowerSource.DOCK // BATTERY_PLUGGED_DOCK (API 33)
        else -> PowerSource.NONE
    }

    fun healthFromCode(code: Int): BatteryHealth = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
        else -> BatteryHealth.UNKNOWN
    }

    /** La temperatura llega en décimas de grado. */
    fun temperatureC(tenths: Int): Float? = if (tenths <= 0 || tenths == Int.MIN_VALUE) null else tenths / 10f

    /**
     * Android define la corriente en µA, pero algunos fabricantes la dan en mA.
     * Valores muy grandes se interpretan como µA. Devuelve mA en valor absoluto, o null si no hay dato.
     */
    fun normalizeCurrentMa(raw: Int): Int? {
        if (raw == Int.MIN_VALUE || raw == 0 || raw == Int.MAX_VALUE) return null
        val absolute = abs(raw.toLong())
        return if (absolute >= 10_000) (absolute / 1000).toInt() else absolute.toInt()
    }

    /** Capacidad total aproximada: carga restante (µAh) dividida entre el porcentaje. */
    fun estimateCapacityMah(chargeCounterUah: Int, levelPercent: Int): Int? {
        if (chargeCounterUah <= 0 || chargeCounterUah == Int.MIN_VALUE || levelPercent !in 5..100) return null
        val mah = chargeCounterUah / 1000.0 * 100.0 / levelPercent
        return if (mah in 500.0..20_000.0) mah.roundToInt() else null
    }
}

class BatteryReader(private val context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)

    fun read(): BatterySnapshot? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return parse(intent)
    }

    /**
     * Emite un valor nuevo cada vez que cambia el estado de la batería. El intent se procesa fuera del hilo
     * principal (las propiedades de BatteryManager llaman al sistema).
     */
    fun observe(): Flow<BatterySnapshot> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().map(::parse).flowOn(Dispatchers.Default)

    private fun parse(intent: Intent): BatterySnapshot {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        val percent = if (level >= 0) (level * 100 / scale) else 0
        val chargeCounter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        val current = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }
        return BatterySnapshot(
            levelPercent = percent,
            status = BatteryMath.statusFromCode(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
            source = BatteryMath.sourceFromPlugged(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)),
            health = BatteryMath.healthFromCode(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)),
            temperatureC = BatteryMath.temperatureC(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 },
            currentMa = BatteryMath.normalizeCurrentMa(current),
            estimatedCapacityMah = BatteryMath.estimateCapacityMah(chargeCounter, percent),
            cycleCount = cycles,
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
        )
    }
}
