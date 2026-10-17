package com.nexusarchive.android.archive

import com.nexusarchive.android.apk.ApkInspector
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkInspectorTest {
    @Test
    fun fingerprintIsColonSeparatedUppercaseHex() {
        val bytes = byteArrayOf(0x00, 0x0F, 0xA0.toByte(), 0xFF.toByte())
        assertEquals("00:0F:A0:FF", ApkInspector.hex(bytes))
    }
}
