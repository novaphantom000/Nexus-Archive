package com.nexusarchive.android.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class FileOpsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val ops = FileOps(bufferSize = 4096)

    private fun write(dir: File, name: String, text: String): File =
        File(dir, name).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    @Test
    fun copiesFileKeepingOriginal() = runBlocking<Unit> {
        val src = write(tmp.root, "a.txt", "hello")
        val dest = tmp.newFolder("dest")
        val r = ops.copy(listOf(src), dest, ConflictResolution.KEEP_BOTH)
        assertEquals(1, r.completed)
        assertTrue(r.failures.isEmpty())
        assertEquals("hello", File(dest, "a.txt").readText())
        assertTrue(src.exists())
        assertEquals(listOf("a.txt"), dest.list()!!.toList())
    }

    @Test
    fun copiesFolderRecursively() = runBlocking<Unit> {
        write(tmp.root, "src/one.txt", "1")
        write(tmp.root, "src/sub/two.txt", "22")
        val dest = tmp.newFolder("dest")
        val r = ops.copy(listOf(File(tmp.root, "src")), dest, ConflictResolution.KEEP_BOTH)
        assertEquals(1, r.completed)
        assertEquals("1", File(dest, "src/one.txt").readText())
        assertEquals("22", File(dest, "src/sub/two.txt").readText())
    }

    @Test
    fun keepBothNumbersTheNewCopy() = runBlocking<Unit> {
        val src = write(tmp.root, "in/a.txt", "new")
        val dest = tmp.newFolder("dest")
        write(dest, "a.txt", "old")
        ops.copy(listOf(src), dest, ConflictResolution.KEEP_BOTH)
        assertEquals("old", File(dest, "a.txt").readText())
        assertEquals("new", File(dest, "a (1).txt").readText())
    }

    @Test
    fun replaceOverwritesExistingFile() = runBlocking<Unit> {
        val src = write(tmp.root, "in/a.txt", "new")
        val dest = tmp.newFolder("dest")
        write(dest, "a.txt", "old")
        ops.copy(listOf(src), dest, ConflictResolution.REPLACE)
        assertEquals("new", File(dest, "a.txt").readText())
        assertEquals(1, dest.list()!!.size)
    }

    @Test
    fun skipLeavesExistingAndReportsSkip() = runBlocking<Unit> {
        val src = write(tmp.root, "in/a.txt", "new")
        val dest = tmp.newFolder("dest")
        write(dest, "a.txt", "old")
        val r = ops.copy(listOf(src), dest, ConflictResolution.SKIP)
        assertEquals(0, r.completed)
        assertEquals(1, r.skipped)
        assertEquals("old", File(dest, "a.txt").readText())
    }

    @Test
    fun copyingIntoSameFolderDuplicates() = runBlocking<Unit> {
        val src = write(tmp.root, "a.txt", "x")
        val r = ops.copy(listOf(src), tmp.root, ConflictResolution.REPLACE)
        assertEquals(1, r.completed)
        assertEquals("x", File(tmp.root, "a (1).txt").readText())
        assertEquals("x", src.readText())
    }

    @Test
    fun folderCannotBeCopiedIntoItself() = runBlocking<Unit> {
        val folder = File(tmp.root, "f").apply { mkdirs() }
        val inner = File(folder, "inner").apply { mkdirs() }
        val r = ops.copy(listOf(folder), inner, ConflictResolution.KEEP_BOTH)
        assertEquals(0, r.completed)
        assertEquals(1, r.failures.size)
        assertEquals(0, inner.list()!!.size)
    }

    @Test
    fun moveRenamesWithinSameFilesystem() = runBlocking<Unit> {
        val src = write(tmp.root, "a.txt", "m")
        val dest = tmp.newFolder("dest")
        val r = ops.move(listOf(src), dest, ConflictResolution.KEEP_BOTH)
        assertEquals(1, r.completed)
        assertFalse(src.exists())
        assertEquals("m", File(dest, "a.txt").readText())
    }

    @Test
    fun moveWithSkipKeepsSourceWhenNameExists() = runBlocking<Unit> {
        val src = write(tmp.root, "in/a.txt", "src")
        val dest = tmp.newFolder("dest")
        write(dest, "a.txt", "existing")
        val r = ops.move(listOf(src), dest, ConflictResolution.SKIP)
        assertEquals(0, r.completed)
        assertEquals(1, r.skipped)
        assertTrue(src.exists())
        assertEquals("existing", File(dest, "a.txt").readText())
    }

    @Test
    fun moveWithReplaceMergesFolders() = runBlocking<Unit> {
        write(tmp.root, "in/photos/new.jpg", "n")
        val dest = tmp.newFolder("dest")
        write(dest, "photos/old.jpg", "o")
        val r = ops.move(listOf(File(tmp.root, "in/photos")), dest, ConflictResolution.REPLACE)
        assertEquals(1, r.completed)
        assertTrue(r.failures.isEmpty())
        assertEquals("n", File(dest, "photos/new.jpg").readText())
        assertEquals("o", File(dest, "photos/old.jpg").readText())
        assertFalse(File(tmp.root, "in/photos").exists())
    }

    @Test
    fun deleteRemovesTree() = runBlocking<Unit> {
        write(tmp.root, "d/x.txt", "x")
        write(tmp.root, "d/sub/y.txt", "y")
        val r = ops.delete(listOf(File(tmp.root, "d")))
        assertEquals(1, r.completed)
        assertFalse(File(tmp.root, "d").exists())
    }

    @Test
    fun deleteNeverFollowsSymlinks() = runBlocking<Unit> {
        val outside = tmp.newFolder("outside")
        val precious = write(outside, "precious.txt", "keep me")
        val dir = tmp.newFolder("d")
        val link = File(dir, "link")
        val created = try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        } catch (e: Exception) {
            false
        }
        assumeTrue("symlinks unsupported here", created)
        val r = ops.delete(listOf(dir))
        assertEquals(1, r.completed)
        assertFalse(dir.exists())
        assertTrue(precious.exists())
    }

    @Test
    fun copySkipsSymlinksAndReportsIt() = runBlocking<Unit> {
        val outside = tmp.newFolder("outside")
        write(outside, "secret.txt", "s")
        val src = tmp.newFolder("src")
        write(src, "real.txt", "r")
        val created = try {
            Files.createSymbolicLink(File(src, "link").toPath(), outside.toPath())
            true
        } catch (e: Exception) {
            false
        }
        assumeTrue("symlinks unsupported here", created)
        val dest = tmp.newFolder("dest")
        val r = ops.copy(listOf(src), dest, ConflictResolution.KEEP_BOTH)
        assertEquals(1, r.skipped)
        assertTrue(File(dest, "src/real.txt").exists())
        assertFalse(File(dest, "src/link").exists())
        assertFalse(File(dest, "src/secret.txt").exists())
    }

    @Test
    fun cancelledCopyLeavesNoPartialOrTempFiles() = runBlocking<Unit> {
        val src = File(tmp.root, "big.bin").apply { writeBytes(ByteArray(2_000_000)) }
        val dest = tmp.newFolder("dest")
        lateinit var job: Job
        val listener = object : ProgressListener {
            override fun onBytes(delta: Long) {
                job.cancel()
            }
        }
        job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            ops.copy(listOf(src), dest, ConflictResolution.KEEP_BOTH, listener)
        }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, dest.list()!!.size)
        assertTrue(src.exists())
    }

    @Test
    fun progressTotalsMatchRealBytes() = runBlocking<Unit> {
        write(tmp.root, "src/a.bin", "x".repeat(10_000))
        write(tmp.root, "src/b.bin", "y".repeat(5_000))
        val dest = tmp.newFolder("dest")
        var total = -1L
        var done = 0L
        val listener = object : ProgressListener {
            override fun onTotals(totalBytes: Long, totalItems: Long) {
                total = totalBytes
            }

            override fun onBytes(delta: Long) {
                done += delta
            }
        }
        ops.copy(listOf(File(tmp.root, "src")), dest, ConflictResolution.KEEP_BOTH, listener)
        assertEquals(15_000L, total)
        assertEquals(15_000L, done)
    }
}

class FileCommandsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun renameRejectsExistingAndInvalidNames() {
        val a = File(tmp.root, "a.txt").apply { writeText("a") }
        File(tmp.root, "b.txt").writeText("b")
        assertTrue(FileCommands.rename(a, "b.txt").isFailure)
        assertTrue(FileCommands.rename(a, "bad/name").isFailure)
        assertTrue(FileCommands.rename(a, "  ").isFailure)
        val ok = FileCommands.rename(a, "c.txt")
        assertTrue(ok.isSuccess)
        assertTrue(File(tmp.root, "c.txt").exists())
    }

    @Test
    fun createFolderAndFileRefuseDuplicates() {
        assertTrue(FileCommands.createFolder(tmp.root, "dir").isSuccess)
        assertTrue(FileCommands.createFolder(tmp.root, "dir").isFailure)
        assertTrue(FileCommands.createFile(tmp.root, "f.txt").isSuccess)
        assertTrue(FileCommands.createFile(tmp.root, "f.txt").isFailure)
        assertTrue(File(tmp.root, "f.txt").isFile)
    }
}

class FileStatsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun hashesMatchKnownVectors() = runBlocking<Unit> {
        val f = File(tmp.root, "abc.txt").apply { writeText("abc") }
        assertEquals("900150983cd24fb0d6963f7d28e17f72", FileStats.hash(f, "MD5"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FileStats.hash(f, "SHA-256"))
    }

    @Test
    fun directoryStatsCountsRecursively() = runBlocking<Unit> {
        File(tmp.root, "d/sub").mkdirs()
        File(tmp.root, "d/a.txt").writeText("12345")
        File(tmp.root, "d/sub/b.txt").writeText("123")
        val stats = FileStats.directoryStats(File(tmp.root, "d"))
        assertEquals(8L, stats.totalBytes)
        assertEquals(2L, stats.fileCount)
        assertEquals(1L, stats.folderCount)
    }
}
