package com.nexusarchive.android.operations

import com.nexusarchive.android.core.ConflictResolution
import com.nexusarchive.android.core.FileOps
import com.nexusarchive.android.core.Formatters
import com.nexusarchive.android.core.OpOutcome
import com.nexusarchive.android.core.ProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Runs file operations in the background, at most [maxConcurrent] at a time, and publishes
 * progress sampled from the engine's real counters.
 *
 * Limitation (documented in the README): the work lives in the app process only. There is no
 * foreground service yet, so Android may stop a long operation if the app is killed in the background.
 */
class OperationManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val fileOps: FileOps = FileOps(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    maxConcurrent: Int = 2,
) {
    private val _operations = MutableStateFlow<List<OperationState>>(emptyList())
    val operations: StateFlow<List<OperationState>> = _operations.asStateFlow()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    /** Emits whenever an operation finishes (in any way), so open folder views can refresh. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val gate = Semaphore(maxConcurrent)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val nextId = AtomicLong(1)
    private val gates = ConcurrentHashMap<Long, PauseGate>()

    /**
     * Pausing blocks the worker thread at its next progress report (every chunk or item), so the operation
     * stops where it is and carries on from the same place on resume. Cancelling always releases the gate first.
     */
    private class PauseGate {
        private val lock = ReentrantLock()
        private val changed = lock.newCondition()

        @Volatile var paused = false
            private set

        fun set(value: Boolean) {
            lock.withLock {
                paused = value
                changed.signalAll()
            }
        }

        fun awaitIfPaused() {
            if (!paused) return
            lock.withLock {
                while (paused) {
                    try {
                        changed.await(250, TimeUnit.MILLISECONDS)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        }
    }

    private class Tracker(val gate: PauseGate) : ProgressListener {
        val bytesDone = AtomicLong()
        val itemsDone = AtomicLong()

        @Volatile var bytesTotal = -1L

        @Volatile var itemsTotal = -1L

        @Volatile var current: String? = null

        override fun onTotals(totalBytes: Long, totalItems: Long) {
            bytesTotal = totalBytes
            itemsTotal = totalItems
        }

        override fun onBytes(delta: Long) {
            gate.awaitIfPaused()
            bytesDone.addAndGet(delta)
        }

        override fun onItems(delta: Long) {
            gate.awaitIfPaused()
            itemsDone.addAndGet(delta)
        }

        override fun onCurrent(name: String) {
            gate.awaitIfPaused()
            current = name
        }
    }

    fun submitCopy(sources: List<String>, destination: String, resolution: ConflictResolution): Long =
        launchOperation(OperationType.COPY, "Copy ${describe(sources)} to ${File(destination).name}", sources, destination) { tracker ->
            fileOps.copy(sources.map { File(it) }, File(destination), resolution, tracker)
        }

    fun submitMove(sources: List<String>, destination: String, resolution: ConflictResolution): Long =
        launchOperation(OperationType.MOVE, "Move ${describe(sources)} to ${File(destination).name}", sources, destination) { tracker ->
            fileOps.move(sources.map { File(it) }, File(destination), resolution, tracker)
        }

    fun submitDelete(targets: List<String>): Long =
        launchOperation(OperationType.DELETE, "Delete ${describe(targets)}", targets, null) { tracker ->
            fileOps.delete(targets.map { File(it) }, tracker)
        }

    /** Runs any other cancellable job (archive creation, extraction, testing) with the same real progress reporting. */
    fun submitCustom(
        type: OperationType,
        title: String,
        sources: List<String>,
        destination: String?,
        body: suspend (ProgressListener) -> OpOutcome,
    ): Long = launchOperation(type, title, sources, destination, body)

    fun cancel(id: Long) {
        gates[id]?.set(false)
        jobs[id]?.cancel()
    }

    /** Pauses at the next chunk or item. Works for every operation type because they all report progress. */
    fun pause(id: Long) {
        val gate = gates[id] ?: return
        gate.set(true)
        update(id) { if (it.isActive) it.copy(paused = true) else it }
    }

    fun resume(id: Long) {
        gates[id]?.set(false)
        update(id) { it.copy(paused = false) }
    }

    fun clearFinished() {
        _operations.update { list -> list.filter { it.isActive } }
    }

    private fun launchOperation(
        type: OperationType,
        title: String,
        sources: List<String>,
        destination: String?,
        body: suspend (ProgressListener) -> OpOutcome,
    ): Long {
        val id = nextId.getAndIncrement()
        val initial = OperationState(
            id = id,
            type = type,
            title = title,
            sources = sources,
            destination = destination,
            status = OperationStatus.QUEUED,
            startedAtMs = clock(),
        )
        _operations.update { listOf(initial) + it }
        val pauseGate = PauseGate()
        gates[id] = pauseGate

        val job = scope.launch {
            try {
                gate.withPermit {
                    val tracker = Tracker(pauseGate)
                    update(id) { it.copy(status = OperationStatus.PREPARING, startedAtMs = clock()) }
                    val ticker = launch {
                        while (isActive) {
                            publish(id, tracker)
                            delay(150)
                        }
                    }
                    val outcome = try {
                        body(tracker)
                    } finally {
                        withContext(NonCancellable) { ticker.cancelAndJoin() }
                    }
                    finish(id, tracker, outcome)
                }
            } catch (e: CancellationException) {
                update(id) { it.copy(status = OperationStatus.CANCELLED, finishedAtMs = clock(), currentItem = null, message = "Cancelled") }
                _changes.tryEmit(Unit)
                throw e
            } catch (t: Throwable) {
                update(id) {
                    it.copy(
                        status = OperationStatus.FAILED,
                        finishedAtMs = clock(),
                        currentItem = null,
                        message = t.message ?: "The operation failed unexpectedly.",
                    )
                }
                _changes.tryEmit(Unit)
            }
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            gates.remove(id)
        }
        return id
    }

    private fun publish(id: Long, t: Tracker) {
        update(id) { s ->
            if (!s.isActive) {
                s
            } else {
                s.copy(
                    status = if (t.itemsTotal >= 0 || t.bytesTotal >= 0) OperationStatus.RUNNING else s.status,
                    bytesDone = t.bytesDone.get(),
                    bytesTotal = t.bytesTotal,
                    itemsDone = t.itemsDone.get(),
                    itemsTotal = t.itemsTotal,
                    currentItem = t.current,
                )
            }
        }
    }

    private fun finish(id: Long, t: Tracker, outcome: OpOutcome) {
        update(id) { s ->
            val failed = outcome.failures.size
            val status = when {
                failed == 0 -> OperationStatus.SUCCEEDED
                outcome.completed == 0 -> OperationStatus.FAILED
                else -> OperationStatus.COMPLETED_WITH_ERRORS
            }
            s.copy(
                status = status,
                finishedAtMs = clock(),
                bytesDone = t.bytesDone.get(),
                bytesTotal = t.bytesTotal,
                itemsDone = t.itemsDone.get(),
                itemsTotal = t.itemsTotal,
                currentItem = null,
                failures = outcome.failures,
                skippedCount = outcome.skipped,
                message = summarize(s.type, outcome),
            )
        }
        _changes.tryEmit(Unit)
    }

    private fun update(id: Long, transform: (OperationState) -> OperationState) {
        _operations.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun describe(paths: List<String>): String =
        if (paths.size == 1) "\"${File(paths[0]).name}\"" else Formatters.plural(paths.size, "item")

    private fun summarize(type: OperationType, outcome: OpOutcome): String {
        val verb = when (type) {
            OperationType.COPY -> "Copied"
            OperationType.MOVE -> "Moved"
            OperationType.DELETE -> "Deleted"
            OperationType.COMPRESS -> "Added"
            OperationType.EXTRACT -> "Extracted"
            OperationType.TEST -> "Read completely"
        }
        val parts = ArrayList<String>()
        if (outcome.completed > 0) parts.add("$verb ${Formatters.plural(outcome.completed, "item")}")
        if (outcome.skipped > 0) parts.add("${outcome.skipped} skipped")
        if (outcome.failures.isNotEmpty()) parts.add("${outcome.failures.size} failed")
        if (type == OperationType.TEST && outcome.failures.isEmpty()) {
            return "Read ${Formatters.plural(outcome.completed, "item")} completely; no read or checksum errors were detected"
        }
        return if (parts.isEmpty()) "Nothing to do" else parts.joinToString(", ")
    }
}
