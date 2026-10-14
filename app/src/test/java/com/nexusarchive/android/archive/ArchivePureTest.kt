package com.nexusarchive.android.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchivePureTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // ---- ArchivePaths ----

    @Test
    fun normalizeAcceptsOrdinaryRelativePaths() {
        assertEquals("a/b.txt", ArchivePaths.normalize("a/b.txt"))
        assertEquals("a/b.txt", ArchivePaths.normalize("./a//b.txt"))
        assertEquals("dir", ArchivePaths.normalize("dir/"))
        assertEquals("a/b", ArchivePaths.normalize("a\\b"))
    }

    @Test
    fun normalizeTreatsRootMarkersAsEmpty() {
        assertEquals("", ArchivePaths.normalize("./"))
        assertEquals("", ArchivePaths.normalize("."))
    }

    @Test
    fun normalizeRejectsUnsafeNames() {
        assertNull(ArchivePaths.normalize("../evil.txt"))
        assertNull(ArchivePaths.normalize("a/../../evil.txt"))
        assertNull(ArchivePaths.normalize("/etc/passwd"))
        assertNull(ArchivePaths.normalize("C:\\Windows\\x"))
        assertNull(ArchivePaths.normalize("a\\..\\..\\x"))
        assertNull(ArchivePaths.normalize("bad\u0000name"))
    }

    @Test
    fun resolveStaysInsideDestination() {
        val dest = tmp.newFolder("dest")
        val ok = ArchivePaths.resolve(dest, "sub/file.txt")
        assertNotNull(ok)
        assertTrue(ok!!.canonicalPath.startsWith(dest.canonicalPath + File.separator))
    }

    // ---- ArchiveFormats ----

    private fun header(vararg bytes: Int): ByteArray {
        val h = ByteArray(512)
        for (i in bytes.indices) h[i] = bytes[i].toByte()
        return h
    }

    @Test
    fun detectsFormatsByMagicBytes() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormats.detect(header(0x50, 0x4B, 0x03, 0x04), 512, "x.zip"))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormats.detect(header(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C), 512, "x.7z"))
        assertEquals(ArchiveFormat.GZ, ArchiveFormats.detect(header(0x1F, 0x8B), 512, "x.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormats.detect(header(0x1F, 0x8B), 512, "x.tar.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormats.detect(header(0x1F, 0x8B), 512, "x.tgz"))
        assertEquals(ArchiveFormat.BZ2, ArchiveFormats.detect(header(0x42, 0x5A, 0x68), 512, "x.bz2"))
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormats.detect(header(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00), 512, "x.tar.xz"))
    }

    @Test
    fun unknownContentIsNotAnArchive() {
        assertNull(ArchiveFormats.detect(header(0x01, 0x02, 0x03), 512, "notes.txt"))
    }

    @Test
    fun unsupportedFormatsAreNamedHonestly() {
        assertEquals("RAR archives aren't supported in this version.", ArchiveFormats.unsupportedReason("a.rar"))
        assertNull(ArchiveFormats.unsupportedReason("a.zip"))
    }

    @Test
    fun baseNameStripsDoubleExtensions() {
        assertEquals("photos", ArchiveFormats.baseName("photos.tar.gz"))
        assertEquals("a", ArchiveFormats.baseName("a.zip"))
        assertEquals("noext", ArchiveFormats.baseName("noext"))
    }

    @Test
    fun capabilityRegistryDoesNotOverpromise() {
        assertTrue(ArchiveFormats.capabilities(ArchiveFormat.ZIP).passwordCreate)
        assertTrue(!ArchiveFormats.capabilities(ArchiveFormat.SEVEN_Z).passwordCreate)
        assertTrue(!ArchiveFormats.capabilities(ArchiveFormat.DEB).create)
        assertTrue(ArchiveFormats.creatable.none { !ArchiveFormats.capabilities(it).create })
    }

    // ---- ArchiveTree ----

    private fun entry(path: String, dir: Boolean = false, size: Long = 1) =
        ArchiveEntryInfo(path, dir, size, -1, 0, false)

    @Test
    fun treeShowsImpliedFoldersAndSortsFoldersFirst() {
        val entries = listOf(entry("b.txt"), entry("docs/a.txt"), entry("docs/sub/c.txt"), entry("A.txt"))
        val root = ArchiveTree.children(entries, "")
        assertEquals(listOf("docs", "A.txt", "b.txt"), root.map { it.name })
        assertTrue(root[0].isDirectory)
        val docs = ArchiveTree.children(entries, "docs")
        assertEquals(listOf("sub", "a.txt"), docs.map { it.name })
    }

    @Test
    fun selectionCoversFolderContents() {
        assertTrue(ArchiveTree.isSelected("docs/a.txt", setOf("docs")))
        assertTrue(ArchiveTree.isSelected("docs", setOf("docs")))
        assertTrue(!ArchiveTree.isSelected("docs2/a.txt", setOf("docs")))
    }

    @Test
    fun sortKeepsFoldersFirstAndHonoursDirection() {
        val nodes = listOf(
            TreeNode("b.txt", "b.txt", false, 5, 0, false),
            TreeNode("a.txt", "a.txt", false, 10, 0, false),
            TreeNode("z", "z", true, -1, 0, false),
        )
        assertEquals(listOf("z", "a.txt", "b.txt"), ArchiveTree.sorted(nodes, ArchiveSortField.NAME, true).map { it.name })
        assertEquals(listOf("z", "b.txt", "a.txt"), ArchiveTree.sorted(nodes, ArchiveSortField.NAME, false).map { it.name })
        assertEquals(listOf("z", "b.txt", "a.txt"), ArchiveTree.sorted(nodes, ArchiveSortField.SIZE, true).map { it.name })
    }

    @Test
    fun searchFindsNamesAnywhereInTheArchive() {
        val entries = listOf(entry("docs/Report.txt"), entry("docs/sub/report-final.pdf"), entry("other.bin"))
        assertEquals(listOf("Report.txt", "report-final.pdf"), ArchiveTree.search(entries, "report").map { it.name })
        assertTrue(ArchiveTree.search(entries, "  ").isEmpty())
    }
}
