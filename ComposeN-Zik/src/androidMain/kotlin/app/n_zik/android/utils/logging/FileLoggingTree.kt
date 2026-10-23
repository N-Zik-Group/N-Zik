package app.n_zik.android.utils.logging

import android.util.Log
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val DEFAULT_MAX_LOG_SIZE = 5L * 1024 * 1024
private const val DEFAULT_MAX_PENDING_SIZE = 8L * 1024 * 1024
private const val MAX_ENTRIES_PER_BATCH = 512
private const val LOG_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss:SSS"
private const val FALLBACK_TAG = "NZikFileLog"

/**
 * Timber tree that appends every DEBUG-and-above line to [logFile] without any disk I/O on the
 * thread that logs (issue #606, Goal M14).
 *
 * [log] only queues an entry into an unbounded channel: it never blocks and never drops a
 * line. A single consumer on [dispatcher] drains the queue in batches, formats the lines,
 * opens the file in append mode, writes the batch, closes it, then applies the size rule
 * ([trimLogFile]) — re-opened for every batch and never kept open, because the Rescue Center
 * and the "debug log" setting delete the file from the outside while the tree is planted.
 *
 * A batch that cannot be written stays in memory and is retried, in order, with the next
 * batch; the only loss is a persistent write failure, beyond [maxPendingSize] characters
 * waiting, where the oldest waiting lines are abandoned. The writer never calls Timber (that
 * would loop back into this tree). Kept a [Timber.DebugTree] so the tag is still inferred
 * from the call stack, on the caller thread, before [log] is reached.
 */
class FileLoggingTree(
    private val logFile: File,
    private val maxLogSize: Long = DEFAULT_MAX_LOG_SIZE,
    dispatcher: CoroutineDispatcher = NzikDispatchers.DATA,
    private val maxPendingSize: Long = DEFAULT_MAX_PENDING_SIZE,
) : Timber.DebugTree() {

    private sealed interface Item

    private class LogEntry(
        val timeMs: Long,
        val priority: Int,
        val tag: String?,
        val message: String,
    ) : Item

    private class FlushMarker(val latch: CountDownLatch) : Item

    private val queue = Channel<Item>(Channel.UNLIMITED)

    private val consumerDone = CountDownLatch(1)

    // SimpleDateFormat is not thread-safe: only the single consumer ever touches it.
    private val dateFormat = SimpleDateFormat(LOG_DATE_PATTERN, Locale.getDefault())

    init {
        NzikDispatchers.fireAndForget(dispatcher).launch { consume() }
    }

    /**
     * Public (widened from the protected Timber signature) so the queueing contract can be
     * exercised directly. Queues the line and returns immediately; safe from any thread. A line
     * logged after [close] is ignored.
     */
    public override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.DEBUG) return
        queue.trySend(LogEntry(System.currentTimeMillis(), priority, tag, message))
    }

    /**
     * Blocks the calling thread until everything queued before this call is on disk, or until
     * [timeoutMs] elapsed, whichever comes first. Meant for the crash handler; deliberately not
     * a `runBlocking`.
     *
     * @return `true` when the queue was processed in time, `false` on timeout
     */
    fun flushBlocking(timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        if (queue.trySend(FlushMarker(latch)).isFailure) {
            // Closed: the consumer is finishing (or finished) what was queued before close().
            return consumerDone.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Stops accepting lines; the consumer writes what is already queued, then ends. */
    fun close() {
        queue.close()
    }

    private suspend fun consume() {
        val lines = ArrayDeque<String>()
        val latches = ArrayList<CountDownLatch>()
        var pendingSize = 0L
        try {
            for (first in queue) {
                var item: Item? = first
                var taken = 0
                while (item != null) {
                    when (item) {
                        is LogEntry -> {
                            val line = formatLogLine(dateFormat, item.timeMs, item.priority, item.tag, item.message)
                            lines.addLast(line)
                            pendingSize += line.length
                        }
                        is FlushMarker -> latches.add(item.latch)
                    }
                    item = if (++taken < MAX_ENTRIES_PER_BATCH) queue.tryReceive().getOrNull() else null
                }

                if (writeBatch(lines)) {
                    lines.clear()
                    pendingSize = 0L
                    trimQuietly()
                    latches.forEach { it.countDown() }
                    latches.clear()
                } else {
                    // Keep the batch for the next attempt; only a persistent failure reaches the cap.
                    while (pendingSize > maxPendingSize && lines.isNotEmpty()) {
                        pendingSize -= lines.removeFirst().length
                    }
                }
            }
            // close() ended the loop: lines kept after a failed batch get one last attempt.
            if (lines.isNotEmpty() && writeBatch(lines)) trimQuietly()
        } finally {
            // If the consumer ends abnormally the channel would stay open with nobody reading.
            queue.close()
            latches.forEach { it.countDown() }
            consumerDone.countDown()
        }
    }

    private var writeFailing = false

    /** @return `true` when every line of [lines] reached the file (or there was nothing to write) */
    private fun writeBatch(lines: List<String>): Boolean {
        if (lines.isEmpty()) return true
        return try {
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            val bytes = lines.joinToString(separator = "").toByteArray(Charsets.UTF_8)
            FileOutputStream(logFile, true).use { it.write(bytes) }
            writeFailing = false
            true
        } catch (e: Exception) {
            if (!writeFailing) {
                writeFailing = true
                // Never Timber here: this tree is planted and would log the failure into itself.
                runCatching { Log.e(FALLBACK_TAG, "Log write failed, will retry with the next batch", e) }
            }
            false
        }
    }

    private fun trimQuietly() {
        // The lines are already on disk: a failed trim must not trigger a retry (duplicates).
        runCatching { trimLogFile(logFile, maxLogSize) }
    }
}

/**
 * Crash handler that first gives [tree] up to [timeoutMs] to write what is queued, then always
 * calls [delegate] (also when the flush throws or times out).
 */
internal fun flushThenDelegate(
    tree: FileLoggingTree,
    timeoutMs: Long = 2000,
    delegate: Thread.UncaughtExceptionHandler,
): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
    runCatching { tree.flushBlocking(timeoutMs) }
    delegate.uncaughtException(thread, throwable)
}

/** Human-readable label of an Android log priority, as written in the log file. */
private fun priorityLabel(priority: Int): String = when (priority) {
    Log.VERBOSE -> "VERBOSE"
    Log.DEBUG -> "DEBUG"
    Log.INFO -> "INFO"
    Log.WARN -> "WARN"
    Log.ERROR -> "ERROR"
    Log.ASSERT -> "ASSERT"
    else -> ""
}

/**
 * Builds one log-file line: `<date> <PRIORITY>: <tag> - <message>\n`. A null [tag] is written as
 * the text "null", exactly like the legacy tree did. [format] must not be shared between threads.
 */
internal fun formatLogLine(
    format: SimpleDateFormat,
    timeMs: Long,
    priority: Int,
    tag: String?,
    message: String,
): String = StringBuilder()
    .append(format.format(Date(timeMs))).append(" ")
    .append(priorityLabel(priority)).append(": ")
    .append(tag).append(" - ")
    .append(message).append('\n')
    .toString()

/**
 * When [file] reached [maxSize], rewrites it with its last 75 % (drops the first 25 %, starting
 * at `length / 4`, which may cut a line in half). Below [maxSize], or for a missing file, does
 * nothing.
 *
 * @throws java.io.IOException when the file cannot be read or rewritten
 */
internal fun trimLogFile(file: File, maxSize: Long) {
    if (file.length() < maxSize) return

    val startIndex = file.length() / 4
    val tail = ByteArrayOutputStream()
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(startIndex)
        val buffer = ByteArray(4096)
        while (true) {
            val read = raf.read(buffer)
            if (read < 0) break
            tail.write(buffer, 0, read)
        }
    }
    FileOutputStream(file).use { tail.writeTo(it) }
}
