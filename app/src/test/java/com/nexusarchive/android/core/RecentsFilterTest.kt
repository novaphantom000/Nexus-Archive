package com.nexusarchive.android.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecentsFilterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun ordinaryFoldersAreAllowed() {
        val dir = tmp.newFolder("docs")
        val file = File(dir, "a.txt").apply { writeText("x") }
        assertTrue(RecentsFilter.isAllowed(dir.path))
        assertTrue(RecentsFilter.isAllowed(file.path))
    }

    @Test
    fun dotFoldersAreExcluded() {
        val dir = tmp.newFolder(".private")
        val file = File(dir, "a.txt").apply { writeText("x") }
        assertFalse(RecentsFilter.isAllowed(dir.path))
        assertFalse(RecentsFilter.isAllowed(file.path))
    }

    @Test
    fun nomediaFoldersAndTheirChildrenAreExcluded() {
        val dir = tmp.newFolder("pics")
        File(dir, ".nomedia").writeText("")
        val child = File(dir, "inner").apply { mkdirs() }
        val file = File(child, "a.jpg").apply { writeText("x") }
        assertFalse(RecentsFilter.isAllowed(dir.path))
        assertFalse(RecentsFilter.isAllowed(child.path))
        assertFalse(RecentsFilter.isAllowed(file.path))
    }
}
