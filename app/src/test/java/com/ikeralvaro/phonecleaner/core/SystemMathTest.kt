package com.ikeralvaro.phonecleaner.core

import android.os.BatteryManager
import com.ikeralvaro.phonecleaner.core.system.BatteryHealth
import com.ikeralvaro.phonecleaner.core.system.BatteryMath
import com.ikeralvaro.phonecleaner.core.system.ChargeStatus
import com.ikeralvaro.phonecleaner.core.system.CoreFreq
import com.ikeralvaro.phonecleaner.core.system.CpuMath
import com.ikeralvaro.phonecleaner.core.system.PowerSource
import com.ikeralvaro.phonecleaner.core.system.ThermalLevel
import com.ikeralvaro.phonecleaner.core.system.ThermalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMathTest {
    // --- Batería ---
    @Test fun currentInMicroampsIsConvertedToMilliamps() {
        assertEquals(1250, BatteryMath.normalizeCurrentMa(-1_250_000))
        assertEquals(1250, BatteryMath.normalizeCurrentMa(1_250_000))
    }

    @Test fun currentAlreadyInMilliampsIsKept() {
        assertEquals(850, BatteryMath.normalizeCurrentMa(-850))
    }

    @Test fun missingCurrentIsNull() {
        assertNull(BatteryMath.normalizeCurrentMa(Int.MIN_VALUE))
        assertNull(BatteryMath.normalizeCurrentMa(0))
    }

    @Test fun capacityIsEstimatedFromChargeCounter() {
        // 2.250.000 µAh al 50 % -> 4500 mAh.
        assertEquals(4500, BatteryMath.estimateCapacityMah(2_250_000, 50))
    }

    @Test fun capacityIsNullWhenDataIsMissingOrAbsurd() {
        assertNull(BatteryMath.estimateCapacityMah(Int.MIN_VALUE, 50))
        assertNull(BatteryMath.estimateCapacityMah(2_000_000, 2))
        assertNull(BatteryMath.estimateCapacityMah(100, 50))
    }

    @Test fun temperatureComesInTenths() {
        assertEquals(31.5f, BatteryMath.temperatureC(315)!!, 0.001f)
        assertNull(BatteryMath.temperatureC(Int.MIN_VALUE))
    }

    @Test fun codesAreMapped() {
        assertEquals(BatteryHealth.GOOD, BatteryMath.healthFromCode(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals(BatteryHealth.OVERHEAT, BatteryMath.healthFromCode(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals(BatteryHealth.UNKNOWN, BatteryMath.healthFromCode(-1))
        assertEquals(ChargeStatus.CHARGING, BatteryMath.statusFromCode(BatteryManager.BATTERY_STATUS_CHARGING))
        assertEquals(PowerSource.USB, BatteryMath.sourceFromPlugged(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(PowerSource.NONE, BatteryMath.sourceFromPlugged(0))
    }

    // --- CPU ---
    @Test fun loadIsAverageOfReadableCores() {
        val freqs = listOf(
            CoreFreq(0, curKhz = 1_000_000, minKhz = 0, maxKhz = 2_000_000), // 0.5
            CoreFreq(1, curKhz = 2_000_000, minKhz = 0, maxKhz = 2_000_000), // 1.0
            CoreFreq(2, curKhz = null, minKhz = 0, maxKhz = 2_000_000), // ilegible
        )
        assertEquals(0.75f, CpuMath.estimateLoad(freqs)!!, 0.001f)
    }

    @Test fun loadIsRelativeToMinimumFrequency() {
        val freqs = listOf(CoreFreq(0, curKhz = 600_000, minKhz = 300_000, maxKhz = 2_100_000))
        assertEquals(300f / 1800f, CpuMath.estimateLoad(freqs)!!, 0.001f)
    }

    @Test fun loadIsNullWhenNothingReadable() {
        assertNull(CpuMath.estimateLoad(listOf(CoreFreq(0, null, null, null))))
        assertNull(CpuMath.estimateLoad(emptyList()))
    }

    @Test fun parsesKhz() {
        assertEquals(1_804_800L, CpuMath.parseKhz("1804800\n"))
        assertNull(CpuMath.parseKhz("garbage"))
        assertNull(CpuMath.parseKhz(null))
    }

    // --- Temperatura ---
    @Test fun zoneTempInMillidegrees() {
        assertEquals(42.5f, ThermalMath.parseZoneTemp("42500")!!, 0.001f)
    }

    @Test fun zoneTempInDegrees() {
        assertEquals(38f, ThermalMath.parseZoneTemp("38")!!, 0.001f)
    }

    @Test fun absurdZoneTempsAreDiscarded() {
        assertNull(ThermalMath.parseZoneTemp("0"))
        assertNull(ThermalMath.parseZoneTemp("-40000"))
        assertNull(ThermalMath.parseZoneTemp("250000"))
        assertNull(ThermalMath.parseZoneTemp(null))
    }

    @Test fun cpuZonesAreRecognised() {
        assertTrue(ThermalMath.isCpuZone("cpu-0-0-usr"))
        assertTrue(ThermalMath.isCpuZone("mtktscpu"))
        assertTrue(ThermalMath.isCpuZone("tsens_tz_sensor1"))
        assertFalse(ThermalMath.isCpuZone("battery"))
        assertFalse(ThermalMath.isCpuZone("gpu-usr"))
    }

    @Test fun thermalStatusLevels() {
        assertEquals(ThermalLevel.NONE, ThermalMath.levelFromStatus(0))
        assertEquals(ThermalLevel.MODERATE, ThermalMath.levelFromStatus(2))
        assertEquals(ThermalLevel.CRITICAL, ThermalMath.levelFromStatus(5))
    }
}
