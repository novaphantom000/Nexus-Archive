package com.nexusarchive.android.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SafLabelsTest {
    @Test
    fun usesLastFolderNameWhenPresent() {
        assertEquals("SD card: Games", SafLabels.defaultLabel("1A2B-3C4D:Android/Games", SafKind.SD_CARD))
        assertEquals("Folder: Music", SafLabels.defaultLabel("primary:Music", SafKind.FOLDER))
    }

    @Test
    fun fallsBackToKindLabel() {
        assertEquals("USB drive", SafLabels.defaultLabel("1A2B-3C4D:", SafKind.USB))
        assertEquals("Provider folder", SafLabels.defaultLabel("", SafKind.PROVIDER))
    }
}
