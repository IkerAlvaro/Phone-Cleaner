package com.ikeralvaro.phonecleaner.cleaner

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ikeralvaro.phonecleaner.core.permissions.PermissionManager
import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.feature.apps.AppRepository
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkCategory
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class JunkScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var scanner: JunkScanner

    private fun file(path: String, size: Int = 100, ageDays: Int = 0): File =
        File(root, path).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(size))
            if (ageDays > 0) setLastModified(System.currentTimeMillis() - ageDays * DAY_MS)
        }

    private fun dir(path: String) = File(root, path).apply { mkdirs() }

    @Before
    fun setUp() {
        root = tmp.newFolder("storage")
        val context = ApplicationProvider.getApplicationContext<Context>()
        scanner = JunkScanner(context, AppRepository(context, PermissionManager(context)), root)

        file("DCIM/.thumbnails/a.jpg", 300)
        file("DCIM/Camera/IMG_1.jpg", 5000, ageDays = 400)
        file("Download/old.pdf", 700, ageDays = 200)
        file("Download/new.pdf", 700)
        file("Download/Trabajo/informe.pdf", 700, ageDays = 200)
        file("Download/app.apk", 900)
        file("Telegram/cache.tmp", 50)
        file("Android/data/com.x/cache/huge.tmp", 9999) // protegido: no se recorre
        file("Android/media/com.gone.app/stuff.bin", 400)
        file("Android/media/com.google.android.gms/x.bin", 400) // sistema: nunca es resto
        dir("Foo/Bar")
        dir("Foo/Baz/Deep")
        dir("Music/Sub")
        dir(".config/empty")
    }

    private fun scan() = runBlocking { scanner.scan { } }

    private fun Map<JunkCategory, List<com.ikeralvaro.phonecleaner.feature.cleaner.JunkItem>>.labels(c: JunkCategory) =
        this[c].orEmpty().map { it.label }.toSet()

    @Test fun findsEachKindOfJunk() {
        val result = scan()
        assertEquals(setOf("DCIM/.thumbnails"), result.labels(JunkCategory.THUMBNAILS))
        assertEquals(300L, result[JunkCategory.THUMBNAILS]!!.single().sizeBytes)
        assertTrue("Telegram/cache.tmp" in result.labels(JunkCategory.TEMP_FILES))
        assertEquals(setOf("Download/old.pdf"), result.labels(JunkCategory.OLD_DOWNLOADS))
        assertEquals(setOf("Download/app.apk"), result.labels(JunkCategory.APK_OTHER))
        assertEquals(setOf("com.gone.app"), result.labels(JunkCategory.RESIDUAL_FOLDERS))
    }

    @Test fun neverEntersProtectedAppData() {
        val all = scan().values.flatten().map { it.path }
        assertFalse(all.any { "Android/data" in it })
    }

    @Test fun emptyFoldersAreReportedAtTheirTopmostDeletableLevel() {
        val empty = scan().labels(JunkCategory.EMPTY_FOLDERS)
        // "Foo" entera (sus subcarpetas también vacías) y "Music/Sub" (Music es estándar y se conserva).
        assertEquals(setOf("Foo", "Music/Sub"), empty)
    }

    @Test fun deletingFreesBytesAndKeepsEverythingElse() {
        val result = scan()
        val selected = result.filterKeys { it.preselected }.values.flatten()
        val freed = runBlocking { scanner.delete(selected) }
        assertEquals(300L + 50L, freed)
        assertFalse(File(root, "DCIM/.thumbnails").exists())
        assertFalse(File(root, "Foo").exists())
        assertTrue(File(root, "Music").exists())
        assertTrue(File(root, "DCIM/Camera/IMG_1.jpg").exists())
        assertTrue(File(root, "Download/old.pdf").exists()) // descargas antiguas no van marcadas
    }

    @Test fun emptyFolderThatGotAFileAfterTheScanIsNotDeleted() {
        val result = scan()
        file("Foo/Bar/nuevo.txt", 10)
        val freed = runBlocking { scanner.delete(result[JunkCategory.EMPTY_FOLDERS]!!) }
        assertEquals(0L, freed)
        assertTrue(File(root, "Foo/Bar/nuevo.txt").exists())
        assertNull(File(root, "Music/Sub").listFiles()?.firstOrNull())
    }
}
