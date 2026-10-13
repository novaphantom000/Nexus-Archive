package com.nexusarchive.android.archive

import com.nexusarchive.android.core.ConflictResolution
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Round-trip tests that run the real engine on real files. */
class ArchiveEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val engine = ArchiveEngine()

    private fun sampleTree(): File {
        val root = tmp.newFolder("src")
        File(root, "hello.txt").writeText("hello world")
        File(root, "big.bin").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
        File(root, "sub").mkdirs()
        File(root, "sub/inner.txt").writeText("inner")
        return root
    }

    private fun assertSameTree(expectedRoot: File, actualRoot: File) {
        for (rel in listOf("hello.txt", "big.bin", "sub/inner.txt")) {
            assertArrayEquals(rel, File(expectedRoot, rel).readBytes(), File(actualRoot, rel).readBytes())
        }
    }

    private fun roundTrip(format: ArchiveFormat, level: CompressionLevel = CompressionLevel.NORMAL) = runBlocking<Unit> {
        val src = sampleTree()
        val out = File(tmp.root, "out" + ArchiveFormats.extensionFor(format))
        val created = engine.create(listOf(src), out, format, level, null)
        assertTrue(out.isFile)
        assertTrue(created.failures.isEmpty())
        assertFalse(tmp.root.listFiles()!!.any { it.name.endsWith(".nexus-part") })

        assertEquals(format, ArchiveFormats.detect(out))
        val listing = engine.list(out)
        assertTrue(listing.entries.any { it.path == "src/hello.txt" })

        val tested = engine.test(out, null)
        assertTrue(tested.failures.isEmpty())

        val dest = tmp.newFolder("dest-" + format.name)
        val result = engine.extract(out, dest, null, null, ConflictResolution.KEEP_BOTH)
        assertTrue(result.failures.isEmpty())
        assertSameTree(src, File(dest, "src"))
    }

    @Test fun zipRoundTrip() = roundTrip(ArchiveFormat.ZIP)

    @Test fun zipStoreRoundTrip() = roundTrip(ArchiveFormat.ZIP, CompressionLevel.STORE)

    @Test fun sevenZRoundTrip() = roundTrip(ArchiveFormat.SEVEN_Z)

    @Test fun tarRoundTrip() = roundTrip(ArchiveFormat.TAR)

    @Test fun tarGzRoundTrip() = roundTrip(ArchiveFormat.TAR_GZ)

    @Test fun tarBz2RoundTrip() = roundTrip(ArchiveFormat.TAR_BZ2)

    @Test fun tarXzRoundTrip() = roundTrip(ArchiveFormat.TAR_XZ)

    @Test
    fun singleFileGzipRoundTrip() = runBlocking<Unit> {
        val f = File(tmp.root, "note.txt").apply { writeText("single stream") }
        val out = File(tmp.root, "note.txt.gz")
        engine.create(listOf(f), out, ArchiveFormat.GZ, CompressionLevel.NORMAL, null)
        val dest = tmp.newFolder("gz-dest")
        engine.extract(out, dest, null, null, ConflictResolution.KEEP_BOTH)
        assertEquals("single stream", File(dest, "note.txt").readText())
    }

    @Test
    fun passwordProtectedZipNeedsTheRightPassword() = runBlocking<Unit> {
        val src = sampleTree()
        val out = File(tmp.root, "secret.zip")
        engine.create(listOf(src), out, ArchiveFormat.ZIP, CompressionLevel.NORMAL, "correct horse".toCharArray())

        val listing = engine.list(out)
        assertTrue(listing.encrypted)

        try {
            engine.extract(out, tmp.newFolder("d1"), null, null, ConflictResolution.KEEP_BOTH)
            fail("Expected a password error")
        } catch (e: ArchiveException) {
            assertEquals(ArchiveException.Kind.PASSWORD_REQUIRED, e.kind)
        }

        assertFalse(engine.checkZipPassword(out, "wrong".toCharArray()))
        assertTrue(engine.checkZipPassword(out, "correct horse".toCharArray()))

        try {
            engine.extract(out, tmp.newFolder("d2"), null, "wrong".toCharArray(), ConflictResolution.KEEP_BOTH)
            fail("Expected a wrong-password error")
        } catch (e: ArchiveException) {
            assertEquals(ArchiveException.Kind.WRONG_PASSWORD, e.kind)
        }

        val good = tmp.newFolder("d3")
        engine.extract(out, good, null, "correct horse".toCharArray(), ConflictResolution.KEEP_BOTH)
        assertSameTree(src, File(good, "src"))
    }

    @Test
    fun zipSlipEntriesAreSkippedAndNothingEscapes() = runBlocking<Unit> {
        val evil = File(tmp.root, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("../escaped.txt"))
            z.write("nope".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("/abs.txt"))
            z.write("nope".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("fine.txt"))
            z.write("ok".toByteArray())
            z.closeEntry()
        }
        val parent = tmp.newFolder("parent")
        val dest = File(parent, "dest")
        val result = engine.extract(evil, dest, null, null, ConflictResolution.KEEP_BOTH)
        assertEquals(2, result.skipped)
        assertFalse(File(parent, "escaped.txt").exists())
        assertFalse(File(dest, "abs.txt").exists())
        assertEquals("ok", File(dest, "fine.txt").readText())
    }

    @Test
    fun conflictPoliciesAreRespected() = runBlocking<Unit> {
        val f = File(tmp.root, "a.txt").apply { writeText("new") }
        val out = File(tmp.root, "a.zip")
        engine.create(listOf(f), out, ArchiveFormat.ZIP, CompressionLevel.NORMAL, null)
        val dest = tmp.newFolder("conf")
        File(dest, "a.txt").writeText("old")

        engine.extract(out, dest, null, null, ConflictResolution.SKIP)
        assertEquals("old", File(dest, "a.txt").readText())

        engine.extract(out, dest, null, null, ConflictResolution.KEEP_BOTH)
        assertEquals("old", File(dest, "a.txt").readText())
        assertEquals("new", File(dest, "a (1).txt").readText())

        engine.extract(out, dest, null, null, ConflictResolution.REPLACE)
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test
    fun selectedEntriesOnlyAreExtracted() = runBlocking<Unit> {
        val src = sampleTree()
        val out = File(tmp.root, "sel.zip")
        engine.create(listOf(src), out, ArchiveFormat.ZIP, CompressionLevel.NORMAL, null)
        val dest = tmp.newFolder("sel-dest")
        engine.extract(out, dest, setOf("src/sub"), null, ConflictResolution.KEEP_BOTH)
        assertTrue(File(dest, "src/sub/inner.txt").isFile)
        assertFalse(File(dest, "src/hello.txt").exists())
    }

    @Test
    fun damagedZipIsReportedNotCrashed() = runBlocking<Unit> {
        val bad = File(tmp.root, "bad.zip")
        bad.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(100) { 7 })
        try {
            engine.list(bad)
            fail("Expected an error")
        } catch (e: ArchiveException) {
            assertTrue(e.kind == ArchiveException.Kind.CORRUPT || e.kind == ArchiveException.Kind.UNSUPPORTED)
        }
    }

    @Test
    fun creationRefusesUnsupportedCombinations() = runBlocking<Unit> {
        val f = File(tmp.root, "a.txt").apply { writeText("x") }
        try {
            engine.create(listOf(f), File(tmp.root, "a.7z"), ArchiveFormat.SEVEN_Z, CompressionLevel.NORMAL, "pw".toCharArray())
            fail("7z passwords are not supported")
        } catch (e: ArchiveException) {
            assertEquals(ArchiveException.Kind.UNSUPPORTED, e.kind)
        }
        assertFalse(File(tmp.root, "a.7z").exists())
    }
}
