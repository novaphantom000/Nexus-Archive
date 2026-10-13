package com.nexusarchive.android.ui.browse

import com.nexusarchive.android.core.StorageVolumeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserModelsTest {
    private fun volume(path: String, label: String, primary: Boolean) =
        StorageVolumeInfo(label, label, path, primary, !primary, false, null, null)

    private val internal = volume("/storage/emulated/0", "Internal storage", true)
    private val sd = volume("/storage/ABCD-1234", "SD card", false)
    private val volumes = listOf(internal, sd)

    @Test
    fun breadcrumbsStartAtTheOwningVolume() {
        val crumbs = buildBreadcrumbs("/storage/ABCD-1234/Music/Albums", volumes)
        assertEquals(listOf("SD card", "Music", "Albums"), crumbs.map { it.label })
        assertEquals("/storage/ABCD-1234/Music", crumbs[1].path)
        assertEquals("/storage/ABCD-1234/Music/Albums", crumbs[2].path)
    }

    @Test
    fun breadcrumbsForVolumeRootIsSingleItem() {
        assertEquals(listOf("Internal storage"), buildBreadcrumbs("/storage/emulated/0", volumes).map { it.label })
    }

    @Test
    fun breadcrumbsOutsideAnyVolumeStartAtDevice() {
        val crumbs = buildBreadcrumbs("/data/local", emptyList())
        assertEquals(listOf("Device", "data", "local"), crumbs.map { it.label })
        assertEquals("/data", crumbs[1].path)
    }

    @Test
    fun parentStopsAtVolumeRoots() {
        assertNull(parentOf("/storage/emulated/0", volumes))
        assertNull(parentOf("/", volumes))
        assertEquals("/storage/emulated/0/Download", parentOf("/storage/emulated/0/Download/x", volumes))
    }

    @Test
    fun deepestVolumeWins() {
        val nested = volume("/storage/emulated/0/Nested", "Nested", false)
        assertEquals("Nested", volumeFor("/storage/emulated/0/Nested/a", listOf(internal, nested))?.label)
    }
}
