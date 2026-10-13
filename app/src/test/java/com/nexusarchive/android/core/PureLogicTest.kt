package com.nexusarchive.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

class NaturalOrderTest {
    @Test
    fun numbersCompareByValueNotText() {
        val sorted = listOf("file10.txt", "file2.txt", "File1.txt", "file02.txt").sortedWith(NaturalOrder)
        assertEquals(listOf("File1.txt", "file2.txt", "file02.txt", "file10.txt"), sorted)
    }

    @Test
    fun caseInsensitive() {
        assertEquals(0, NaturalOrder.compare("ABC", "abc"))
    }

    @Test
    fun prefixSortsFirst() {
        assertTrue(NaturalOrder.compare("a", "a1") < 0)
    }
}

class FileTypesTest {
    @Test
    fun classifiesByExtension() {
        assertEquals(FileKind.IMAGE, FileTypes.kindOf("photo.JPG", false))
        assertEquals(FileKind.ARCHIVE, FileTypes.kindOf("backup.tar.gz", false))
        assertEquals(FileKind.APK, FileTypes.kindOf("app.apk", false))
        assertEquals(FileKind.FOLDER, FileTypes.kindOf("anything.zip", true))
        assertEquals(FileKind.OTHER, FileTypes.kindOf("noextension", false))
    }

    @Test
    fun dotfilesHaveNoExtension() {
        assertEquals("", FileTypes.extensionOf(".bashrc"))
        assertEquals("", FileTypes.extensionOf("trailingdot."))
        assertEquals("gz", FileTypes.extensionOf("a.tar.gz"))
    }
}

class FileNamesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun validateRejectsBadNames() {
        assertNotNull(FileNames.validate(""))
        assertNotNull(FileNames.validate("   "))
        assertNotNull(FileNames.validate("."))
        assertNotNull(FileNames.validate(".."))
        assertNotNull(FileNames.validate("a/b"))
        assertNotNull(FileNames.validate("x".repeat(300)))
        assertNull(FileNames.validate("fine name (1).txt"))
    }

    @Test
    fun splitNameHandlesDoubleExtensionsAndFolders() {
        assertEquals("photo" to ".jpg", FileNames.splitName("photo.jpg", false))
        assertEquals("backup" to ".tar.gz", FileNames.splitName("backup.tar.gz", false))
        assertEquals(".bashrc" to "", FileNames.splitName(".bashrc", false))
        assertEquals("my.folder" to "", FileNames.splitName("my.folder", true))
    }

    @Test
    fun uniqueFileNumbersUntilFree() {
        val dir = tmp.root
        java.io.File(dir, "a.txt").writeText("1")
        assertEquals("a (1).txt", FileNames.uniqueFile(dir, "a.txt", false).name)
        java.io.File(dir, "a (1).txt").writeText("2")
        assertEquals("a (2).txt", FileNames.uniqueFile(dir, "a.txt", false).name)
        assertEquals("free.txt", FileNames.uniqueFile(dir, "free.txt", false).name)
    }
}

class ArrangeTest {
    private fun file(name: String, size: Long = 1, modified: Long = 0, dir: Boolean = false) =
        FileEntry("/x/$name", name, dir, if (dir) -1 else size, modified, name.startsWith("."), false)

    private val entries = listOf(
        file("b.txt", size = 30, modified = 3),
        file("a10.png", size = 10, modified = 1),
        file("a2.png", size = 20, modified = 2),
        file("zdir", dir = true),
        file(".hidden", size = 5),
    )

    @Test
    fun foldersFirstThenNaturalName() {
        val result = Arrange.apply(entries, SortConfig(showHidden = false), FilterKind.ALL, "")
        assertEquals(listOf("zdir", "a2.png", "a10.png", "b.txt"), result.map { it.name })
    }

    @Test
    fun hiddenFilesOnlyWhenRequested() {
        val shown = Arrange.apply(entries, SortConfig(showHidden = true), FilterKind.ALL, "")
        assertTrue(shown.any { it.name == ".hidden" })
        val hidden = Arrange.apply(entries, SortConfig(showHidden = false), FilterKind.ALL, "")
        assertFalse(hidden.any { it.name == ".hidden" })
    }

    @Test
    fun sortBySizeDescending() {
        val result = Arrange.apply(entries, SortConfig(SortField.SIZE, ascending = false, foldersFirst = false), FilterKind.ALL, "")
        assertEquals(listOf("b.txt", "a2.png", "a10.png"), result.filter { !it.isDirectory }.map { it.name })
    }

    @Test
    fun typeFilterKeepsFoldersAndMatchingFiles() {
        val result = Arrange.apply(entries, SortConfig(), FilterKind.IMAGES, "")
        assertEquals(listOf("zdir", "a2.png", "a10.png"), result.map { it.name })
    }

    @Test
    fun foldersFilterShowsOnlyFolders() {
        assertEquals(listOf("zdir"), Arrange.apply(entries, SortConfig(), FilterKind.FOLDERS, "").map { it.name })
    }

    @Test
    fun searchIsCaseInsensitiveSubstring() {
        assertEquals(listOf("a2.png", "a10.png"), Arrange.apply(entries, SortConfig(), FilterKind.ALL, "PNG").map { it.name })
    }
}

class FormattersTest {
    @Test
    fun bytesUsesBinaryUnitsAndHandlesUnknown() {
        assertEquals("\u2014", Formatters.bytes(-1))
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.5 KB", Formatters.bytes(1536, Locale.US))
        assertEquals("1.0 MB", Formatters.bytes(1024L * 1024, Locale.US))
    }

    @Test
    fun durationFormats() {
        assertEquals("00:05", Formatters.duration(5_000))
        assertEquals("1:01:05", Formatters.duration(3_665_000))
    }

    @Test
    fun pluralizes() {
        assertEquals("1 item", Formatters.plural(1, "item"))
        assertEquals("3 items", Formatters.plural(3, "item"))
    }
}
