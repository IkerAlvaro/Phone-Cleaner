package com.ikeralvaro.phonecleaner.core

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ikeralvaro.phonecleaner.core.system.CpuReader
import com.ikeralvaro.phonecleaner.core.system.ThermalReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Lectores de /sys con una carpeta falsa. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class SysfsReadersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(root: File, path: String, text: String) =
        File(root, path).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test fun cpuReaderReadsCurrentFrequencyAndCachesLimits() {
        val root = tmp.newFolder("cpu")
        val cores = Runtime.getRuntime().availableProcessors()
        repeat(cores) { i ->
            write(root, "cpu$i/cpufreq/cpuinfo_min_freq", "300000\n")
            write(root, "cpu$i/cpufreq/cpuinfo_max_freq", "2000000\n")
            write(root, "cpu$i/cpufreq/scaling_cur_freq", "1000000\n")
        }
        val reader = CpuReader(root)
        val first = reader.read()
        assertEquals(cores, first.freqs.size)
        assertEquals(1_000_000L, first.freqs[0].curKhz)
        assertEquals(2_000_000L, first.freqs[0].maxKhz)

        // El máximo es fijo: se leyó una vez. La frecuencia actual sí se relee.
        write(root, "cpu0/cpufreq/cpuinfo_max_freq", "9000000\n")
        write(root, "cpu0/cpufreq/scaling_cur_freq", "1500000\n")
        val second = reader.read()
        assertEquals(2_000_000L, second.freqs[0].maxKhz)
        assertEquals(1_500_000L, second.freqs[0].curKhz)
    }

    @Test fun cpuReaderWithoutSysfsGivesNulls() {
        val snapshot = CpuReader(tmp.newFolder("empty")).read()
        assertNull(snapshot.freqs[0].curKhz)
        assertNull(snapshot.estimatedLoad)
    }

    @Test fun thermalReaderUsesOnlyCpuZonesAndCachesTheList() {
        val root = tmp.newFolder("thermal")
        write(root, "thermal_zone0/type", "cpu-0-0\n")
        write(root, "thermal_zone0/temp", "41000\n")
        write(root, "thermal_zone1/type", "battery\n")
        write(root, "thermal_zone1/temp", "60000\n")
        write(root, "thermal_zone2/type", "tsens_tz_sensor1\n")
        write(root, "thermal_zone2/temp", "45\n")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reader = ThermalReader(context, root)
        assertEquals(45f, reader.read().cpuTempC!!, 0.01f)

        // Las temperaturas se releen; una zona nueva no (la lista se calcula una vez).
        write(root, "thermal_zone0/temp", "50000\n")
        write(root, "thermal_zone3/type", "cpu-1-0\n")
        write(root, "thermal_zone3/temp", "99000\n")
        assertEquals(50f, reader.read().cpuTempC!!, 0.01f)
    }

    @Test fun thermalReaderWithoutZonesGivesNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNull(ThermalReader(context, tmp.newFolder("none")).read().cpuTempC)
    }
}
