package com.ikeralvaro.phonecleaner.cleaner

import com.ikeralvaro.phonecleaner.core.util.DAY_MS
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkCategory
import com.ikeralvaro.phonecleaner.feature.cleaner.JunkRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkRulesTest {
    private val now = 1_800_000_000_000L
    private val recent = now - DAY_MS
    private val old = now - 120 * DAY_MS

    @Test fun thumbnailsAnywhere() {
        assertEquals(JunkCategory.THUMBNAILS, JunkRules.classifyFile("DCIM/.thumbnails/1234.jpg", recent, now))
        assertEquals(JunkCategory.THUMBNAILS, JunkRules.classifyFile("Pictures/.Thumbnails/a.jpg", recent, now))
        assertTrue(JunkRules.isThumbnailDir("DCIM/.thumbnails"))
        assertFalse(JunkRules.isThumbnailDir("DCIM/thumbnails"))
    }

    @Test fun tempAndLogFilesOnlyWhenOldOrInsideCacheFolders() {
        val week = now - 8 * DAY_MS
        // Recientes fuera de una carpeta de caché: pueden estar en uso.
        assertNull(JunkRules.classifyFile("WhatsApp/foo.tmp", recent, now))
        assertNull(JunkRules.classifyFile("app/logs/debug.LOG", recent, now))
        // Antiguos: sí.
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("WhatsApp/foo.tmp", week, now))
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("app/logs/debug.LOG", week, now))
        // Dentro de una carpeta tipo caché: aunque sean recientes.
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("app/cache/a.tmp", recent, now))
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("app/.cache/a.log", recent, now))
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("Foo/TMP/x.temp", recent, now))
        // Fecha desconocida: no.
        assertNull(JunkRules.classifyFile("WhatsApp/foo.tmp", 0L, now))
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("Music/Thumbs.db", recent, now))
    }

    @Test fun documentsAndDownloadsAreNeverTempUnlessOld() {
        assertNull(JunkRules.classifyFile("Documents/informe.log", recent, now))
        assertNull(JunkRules.classifyFile("Download/cache/a.tmp", recent, now))
        assertNull(JunkRules.classifyFile("Documents/tmp/notas.tmp", recent, now))
        assertEquals(JunkCategory.TEMP_FILES, JunkRules.classifyFile("Documents/informe.log", old, now))
    }

    @Test fun residualFoldersNeedThirtyDaysWithoutChanges() {
        assertFalse(JunkRules.isPossibleLeftover(now - 29 * DAY_MS, now))
        assertTrue(JunkRules.isPossibleLeftover(now - 31 * DAY_MS, now))
        assertFalse(JunkRules.isPossibleLeftover(0L, now))
    }

    @Test fun downloadsInProgressAreNeverJunk() {
        assertNull(JunkRules.classifyFile("Download/video.mp4.part", recent, now))
        assertNull(JunkRules.classifyFile("Download/file.crdownload", recent, now))
    }

    @Test fun apkFilesAreFlagged() {
        assertEquals(JunkCategory.APK_OTHER, JunkRules.classifyFile("Download/game.apk", recent, now))
        assertEquals(JunkCategory.APK_OTHER, JunkRules.classifyFile("Bluetooth/app.XAPK", old, now))
    }

    @Test fun onlyOldTopLevelDownloads() {
        assertEquals(JunkCategory.OLD_DOWNLOADS, JunkRules.classifyFile("Download/manual.pdf", old, now))
        assertNull(JunkRules.classifyFile("Download/manual.pdf", recent, now))
        assertNull(JunkRules.classifyFile("Download/Trabajo/informe.pdf", old, now))
        assertNull(JunkRules.classifyFile("Documents/manual.pdf", old, now))
    }

    @Test fun regularFilesAreNotJunk() {
        assertNull(JunkRules.classifyFile("DCIM/Camera/IMG_0001.jpg", old, now))
        assertNull(JunkRules.classifyFile("Music/song.mp3", old, now))
    }

    @Test fun skipsProtectedAppData() {
        assertTrue(JunkRules.shouldSkipDir("Android/data"))
        assertTrue(JunkRules.shouldSkipDir("/Android/obb/"))
        assertFalse(JunkRules.shouldSkipDir("Android/media"))
    }

    @Test fun standardAndHiddenFoldersAreNeverDeletedWhenEmpty() {
        assertFalse(JunkRules.isDeletableEmptyDir("Music"))
        assertFalse(JunkRules.isDeletableEmptyDir("DCIM/Camera"))
        assertFalse(JunkRules.isDeletableEmptyDir(".config"))
        assertFalse(JunkRules.isDeletableEmptyDir("Foo/.cache"))
        assertFalse(JunkRules.isDeletableEmptyDir(""))
        assertTrue(JunkRules.isDeletableEmptyDir("OldApp/Backups"))
        assertTrue(JunkRules.isDeletableEmptyDir("Pictures/Viaje 2019"))
    }

    @Test fun residualPackageFolders() {
        assertEquals("com.whatsapp", JunkRules.residualPackage("Android/media/com.whatsapp"))
        assertNull(JunkRules.residualPackage("Android/media"))
        assertNull(JunkRules.residualPackage("Android/media/com.whatsapp/WhatsApp"))
        assertNull(JunkRules.residualPackage("Android/media/not a package"))
    }

    @Test fun systemPackagesAreNeverResidual() {
        assertTrue(JunkRules.isSystemPackage("com.google.android.gms"))
        assertTrue(JunkRules.isSystemPackage("com.samsung.android.app.notes"))
        assertTrue(JunkRules.isSystemPackage("android"))
        assertFalse(JunkRules.isSystemPackage("com.whatsapp"))
    }

    @Test fun categoriesNeedingReviewAreNotPreselected() {
        assertTrue(JunkCategory.THUMBNAILS.preselected)
        assertTrue(JunkCategory.APK_INSTALLED.preselected)
        assertFalse(JunkCategory.APK_OTHER.preselected)
        assertFalse(JunkCategory.OLD_DOWNLOADS.preselected)
        assertFalse(JunkCategory.RESIDUAL_FOLDERS.preselected)
    }
}
