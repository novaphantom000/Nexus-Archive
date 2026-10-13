package com.nexusarchive.android.operations

import com.nexusarchive.android.core.ConflictResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OperationManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager() = OperationManager(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private suspend fun OperationManager.awaitIdle(): List<OperationState> =
        withTimeout(15_000) { operations.first { list -> list.isNotEmpty() && list.none { it.isActive } } }

    @Test
    fun copyReportsRealCountersAndSucceeds() = runBlocking<Unit> {
        val src = File(tmp.root, "a.bin").apply { writeBytes(ByteArray(100_000) { 1 }) }
        val dest = tmp.newFolder("dest")
        val m = manager()
        m.submitCopy(listOf(src.path), dest.path, ConflictResolution.KEEP_BOTH)
        val op = m.awaitIdle().single()
        assertEquals(OperationStatus.SUCCEEDED, op.status)
        assertEquals(100_000L, op.bytesDone)
        assertEquals(100_000L, op.bytesTotal)
        assertEquals(1.0f, op.fraction ?: -1f, 0.0001f)
        assertEquals(100_000L, File(dest, "a.bin").length())
        assertNotNull(op.finishedAtMs)
    }

    @Test
    fun partialFailureIsReportedNotHidden() = runBlocking<Unit> {
        val good = File(tmp.root, "good.txt").apply { writeText("g") }
        val missing = File(tmp.root, "missing.txt")
        val dest = tmp.newFolder("dest")
        val m = manager()
        m.submitCopy(listOf(good.path, missing.path), dest.path, ConflictResolution.KEEP_BOTH)
        val op = m.awaitIdle().single()
        assertEquals(OperationStatus.COMPLETED_WITH_ERRORS, op.status)
        assertEquals(1, op.failures.size)
        assertTrue(File(dest, "good.txt").exists())
    }

    @Test
    fun deleteOperationRemovesFiles() = runBlocking<Unit> {
        val f = File(tmp.root, "gone.txt").apply { writeText("x") }
        val m = manager()
        m.submitDelete(listOf(f.path))
        val op = m.awaitIdle().single()
        assertEquals(OperationStatus.SUCCEEDED, op.status)
        assertFalse(f.exists())
        assertEquals(OperationType.DELETE, op.type)
        assertNull(op.bytesPerSecond(System.currentTimeMillis()))
    }

    @Test
    fun clearFinishedKeepsOnlyActive() = runBlocking<Unit> {
        val f = File(tmp.root, "x.txt").apply { writeText("x") }
        val m = manager()
        m.submitDelete(listOf(f.path))
        m.awaitIdle()
        m.clearFinished()
        assertTrue(m.operations.value.isEmpty())
    }
}
