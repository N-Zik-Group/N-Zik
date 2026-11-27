package app.n_zik.android.utils.logging

import android.util.Log
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Covers the asynchronous [FileLoggingTree] (issue #606, Goal M14) row by row of the spec's
 * I/O matrix: queueing off the caller thread and in order, non-blocking `log()`, priority
 * filter, size rule, burst without loss, write failure then recovery without loss, crash flush
 * and close. The consumer runs on a private single-thread executor; holding that thread with a
 * latch ("gate") freezes the consumer so the caller-side contract can be observed.
 */
class FileLoggingTreeTest {

    @TempDir
    lateinit var dir: File

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "file-log-test-consumer").apply { isDaemon = true }
    }
    private val gate = CountDownLatch(1)
    private val trees = mutableListOf<FileLoggingTree>()

    private lateinit var logFile: File

    @BeforeEach
    fun setUp() {
        logFile = File(dir, "N-Zik_log.txt")
    }

    @AfterEach
    fun tearDown() {
        gate.countDown()
        trees.forEach { it.close() }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    /** Occupies the consumer thread until [releaseConsumer], so nothing can be written. */
    private fun holdConsumer() {
        executor.execute { gate.await() }
    }

    private fun releaseConsumer() = gate.countDown()

    private fun newTree(
        file: File = logFile,
        maxLogSize: Long = 100L * 1024 * 1024,
        maxPendingSize: Long = 8L * 1024 * 1024,
    ) = FileLoggingTree(file, maxLogSize, executor.asCoroutineDispatcher(), maxPendingSize)
        .also { trees += it }

    private fun FileLoggingTree.logD(tag: String, message: String) = log(Log.DEBUG, tag, message, null)

    private fun lines(): List<String> = if (logFile.exists()) logFile.readLines() else emptyList()

    @Test
    fun `disabling logging drops queued lines and leaves the deleted file absent`() {
        holdConsumer()
        val enabled = java.util.concurrent.atomic.AtomicBoolean(true)
        val tree = FileLoggingTree(logFile, dispatcher = executor.asCoroutineDispatcher(), enabled = enabled::get)
            .also { trees += it }
        tree.logD("test", "queued")
        enabled.set(false)
        tree.logD("test", "after disable")
        releaseConsumer()
        assertTrue(tree.flushBlocking(1000))
        assertFalse(logFile.exists())
    }

    // --- formatLogLine ---------------------------------------------------------------------

    @Test
    fun `formatLogLine keeps the legacy format for every priority`() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val expected = mapOf(
            Log.VERBOSE to "VERBOSE",
            Log.DEBUG to "DEBUG",
            Log.INFO to "INFO",
            Log.WARN to "WARN",
            Log.ERROR to "ERROR",
            Log.ASSERT to "ASSERT",
        )
        expected.forEach { (priority, label) ->
            assertEquals(
                "1970-01-01 00:00:01:234 $label: MyTag - hello world\n",
                formatLogLine(format, 1234L, priority, "MyTag", "hello world"),
            )
        }
    }

    @Test
    fun `formatLogLine writes a null tag as the text null like the legacy tree`() {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        assertEquals(
            "1970-01-01 00:00:00:000 INFO: null - m\n",
            formatLogLine(format, 0L, Log.INFO, null, "m"),
        )
    }

    // --- trimLogFile -----------------------------------------------------------------------

    @Test
    fun `trimLogFile keeps the last 75 percent once the threshold is reached`() {
        val text = (0 until 50).joinToString("") { "line-%02d\n".format(it) } // 50 x 8 = 400 chars
        logFile.writeText(text)

        trimLogFile(logFile, 400)

        assertEquals(text.substring(text.length / 4), logFile.readText())
    }

    @Test
    fun `trimLogFile leaves a file below the threshold untouched`() {
        logFile.writeText("small\n")

        trimLogFile(logFile, 400)

        assertEquals("small\n", logFile.readText())
    }

    @Test
    fun `trimLogFile ignores a missing file`() {
        trimLogFile(logFile, 10)

        assertFalse(logFile.exists())
    }

    // --- log(): off the caller thread, in order --------------------------------------------

    @Test
    fun `log returns without touching the disk and the line is written afterwards`() {
        holdConsumer()
        val tree = newTree()

        tree.logD("T", "first")

        assertFalse(logFile.exists(), "no file I/O may happen on the calling thread")
        releaseConsumer()
        assertTrue(tree.flushBlocking(5000))
        assertEquals(1, lines().size)
        assertTrue(lines().single().endsWith(" DEBUG: T - first"))
    }

    @Test
    fun `lines are written in call order in the legacy format`() {
        val tree = newTree()

        tree.log(Log.DEBUG, "A", "one", null)
        tree.log(Log.INFO, "B", "two", null)
        tree.log(Log.ERROR, "C", "three", null)
        assertTrue(tree.flushBlocking(5000))

        val written = lines()
        assertEquals(3, written.size)
        val datePrefix = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}:\d{3} """)
        written.forEach { assertTrue(datePrefix.containsMatchIn(it), "unexpected prefix: $it") }
        assertTrue(written[0].endsWith(" DEBUG: A - one"))
        assertTrue(written[1].endsWith(" INFO: B - two"))
        assertTrue(written[2].endsWith(" ERROR: C - three"))
    }

    @Test
    fun `log never blocks the caller while the consumer is stuck`() {
        holdConsumer()
        val tree = newTree()
        val done = CountDownLatch(1)

        Thread {
            repeat(20_000) { tree.logD("T", "line $it") }
            done.countDown()
        }.start()

        assertTrue(done.await(10, TimeUnit.SECONDS), "log() blocked the calling thread")
        releaseConsumer()
        assertTrue(tree.flushBlocking(10_000))
        assertEquals(20_000, lines().size)
    }

    // --- priority --------------------------------------------------------------------------

    @Test
    fun `verbose is ignored and debug and above are written`() {
        val tree = newTree()

        tree.log(Log.VERBOSE, "T", "verbose", null)
        tree.log(Log.DEBUG, "T", "debug", null)
        tree.log(Log.INFO, "T", "info", null)
        tree.log(Log.WARN, "T", "warn", null)
        tree.log(Log.ERROR, "T", "error", null)
        tree.log(Log.ASSERT, "T", "assert", null)
        assertTrue(tree.flushBlocking(5000))

        val written = lines()
        assertEquals(5, written.size)
        assertTrue(written.none { it.contains("verbose") })
        assertTrue(written.last().endsWith(" ASSERT: T - assert"))
    }

    // --- size rule -------------------------------------------------------------------------

    @Test
    fun `a batch that reaches the max size leaves the last 75 percent of the file`() {
        holdConsumer() // freeze so the 30 lines below form a single batch
        val tree = newTree(maxLogSize = 1000)

        repeat(30) { tree.logD("T", "line-%02d".format(it)) }
        releaseConsumer()
        assertTrue(tree.flushBlocking(5000))

        // Every line has a fixed length: 23 (date) + " DEBUG: T - " (12) + 7 + "\n".
        val lineLength = 23 + 12 + 7 + 1
        val total = 30 * lineLength
        assertTrue(total >= 1000)
        assertEquals((total - total / 4).toLong(), logFile.length())
        assertTrue(logFile.readText().endsWith(" DEBUG: T - line-29\n"))
    }

    @Test
    fun `an existing file is appended to, not overwritten`() {
        logFile.writeText("previous run\n")
        val tree = newTree()

        tree.logD("T", "new")
        assertTrue(tree.flushBlocking(5000))

        assertEquals(2, lines().size)
        assertEquals("previous run", lines().first())
    }

    // --- burst -----------------------------------------------------------------------------

    @Test
    fun `a burst far larger than what is written per batch loses no line and keeps the order`() {
        val tree = newTree()
        val total = 20_000

        repeat(total) { tree.logD("T", "n=$it") }
        assertTrue(tree.flushBlocking(20_000))

        val written = lines()
        assertEquals(total, written.size)
        written.forEachIndexed { index, line ->
            assertTrue(line.endsWith(" DEBUG: T - n=$index"), "line $index out of order: $line")
        }
    }

    @Test
    fun `concurrent loggers lose nothing and each keeps its own order`() {
        holdConsumer()
        val tree = newTree()
        val threads = 4
        val perThread = 2_000
        val start = CountDownLatch(1)
        val workers = (0 until threads).map { id ->
            Thread {
                start.await()
                repeat(perThread) { tree.logD("W$id", "n=$it") }
            }.also { it.start() }
        }

        start.countDown()
        workers.forEach { it.join(10_000) }
        releaseConsumer()
        assertTrue(tree.flushBlocking(20_000))

        val written = lines()
        assertEquals(threads * perThread, written.size)
        for (id in 0 until threads) {
            val mine = written.filter { it.contains(" DEBUG: W$id - ") }
                .map { it.substringAfter("n=").toInt() }
            assertEquals((0 until perThread).toList(), mine, "worker $id lost or reordered lines")
        }
    }

    // --- write failure ---------------------------------------------------------------------

    @Test
    fun `a failed write is kept and retried with the next batch, in order, then everything is written`() {
        val missingDir = File(dir, "later")
        val file = File(missingDir, "N-Zik_log.txt")
        val tree = newTree(file = file)

        tree.logD("T", "a")
        tree.logD("T", "b")
        assertFalse(tree.flushBlocking(300), "the write cannot succeed while the folder is missing")

        assertTrue(missingDir.mkdirs())
        tree.logD("T", "c")
        assertTrue(tree.flushBlocking(5000))

        val written = file.readLines()
        assertEquals(3, written.size)
        assertTrue(written[0].endsWith(" DEBUG: T - a"))
        assertTrue(written[1].endsWith(" DEBUG: T - b"))
        assertTrue(written[2].endsWith(" DEBUG: T - c"))
    }

    @Test
    fun `the consumer survives a write failure`() {
        val missingDir = File(dir, "later")
        val file = File(missingDir, "N-Zik_log.txt")
        val tree = newTree(file = file)

        repeat(3) { tree.logD("T", "lost-for-now $it") }
        assertFalse(tree.flushBlocking(200))
        missingDir.mkdirs()

        assertTrue(tree.flushBlocking(5000), "the consumer died after the failed write")
        assertEquals(3, file.readLines().size)
    }

    @Test
    fun `a persistent failure beyond the pending cap abandons only the oldest waiting lines`() {
        val missingDir = File(dir, "later")
        val file = File(missingDir, "N-Zik_log.txt")
        holdConsumer()
        val tree = newTree(file = file, maxPendingSize = 1_000)

        repeat(200) { tree.logD("T", "old-%03d".format(it)) } // ~40 chars each, far above the cap
        releaseConsumer()
        assertFalse(tree.flushBlocking(300))

        missingDir.mkdirs()
        tree.logD("T", "newest")
        assertTrue(tree.flushBlocking(5000))

        val written = file.readLines()
        assertTrue(written.last().endsWith(" DEBUG: T - newest"))
        assertTrue(written.none { it.endsWith("old-000") }, "the oldest line should have been abandoned")
        assertTrue(written.any { it.endsWith("old-199") }, "the most recent waiting lines must be kept")
        assertTrue(written.size < 201)
    }

    // --- flushBlocking ---------------------------------------------------------------------

    @Test
    fun `flushBlocking returns true once everything queued is on disk`() {
        val tree = newTree()

        repeat(100) { tree.logD("T", "l$it") }

        assertTrue(tree.flushBlocking(5000))
        assertEquals(100, lines().size)
    }

    @Test
    fun `flushBlocking gives up after the timeout when the consumer is stuck`() {
        holdConsumer()
        val tree = newTree()
        tree.logD("T", "queued")

        val startNs = System.nanoTime()
        val flushed = tree.flushBlocking(150)
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        assertFalse(flushed)
        assertTrue(elapsedMs >= 100, "returned too early: $elapsedMs ms")
        assertTrue(elapsedMs < 3000, "did not honour the timeout: $elapsedMs ms")
        assertFalse(logFile.exists())
    }

    // --- re-open per batch -----------------------------------------------------------------

    @Test
    fun `the file is re-opened for every batch so a deletion from outside is survived`() {
        val tree = newTree()

        tree.logD("T", "first")
        assertTrue(tree.flushBlocking(5000))
        assertTrue(logFile.delete())
        tree.logD("T", "second")
        assertTrue(tree.flushBlocking(5000))

        assertTrue(logFile.exists())
        val written = lines()
        assertEquals(1, written.size)
        assertTrue(written.single().endsWith(" DEBUG: T - second"))
    }

    // --- crash handler ---------------------------------------------------------------------

    @Test
    fun `flushThenDelegate has the queued lines on disk when the delegate runs`() {
        val tree = newTree()
        tree.logD("T", "before crash")
        var linesSeenByDelegate: List<String>? = null
        var received: Throwable? = null
        val crash = RuntimeException("boom")

        flushThenDelegate(tree, 5000) { _, throwable ->
            linesSeenByDelegate = lines()
            received = throwable
        }.uncaughtException(Thread.currentThread(), crash)

        assertEquals(1, linesSeenByDelegate?.size)
        assertTrue(linesSeenByDelegate!!.single().endsWith(" DEBUG: T - before crash"))
        assertEquals(crash, received)
    }

    @Test
    fun `flushThenDelegate still calls the delegate when the flush times out`() {
        holdConsumer()
        val tree = newTree()
        tree.logD("T", "stuck")
        var called = false

        val startNs = System.nanoTime()
        flushThenDelegate(tree, 100) { _, _ -> called = true }
            .uncaughtException(Thread.currentThread(), RuntimeException("boom"))
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        assertTrue(called, "the delegate must run even if the flush timed out")
        assertTrue(elapsedMs < 3000, "did not honour the timeout: $elapsedMs ms")
    }

    // --- close -----------------------------------------------------------------------------

    @Test
    fun `close writes the lines kept after a failed batch`() {
        val missingDir = File(dir, "later")
        val file = File(missingDir, "N-Zik_log.txt")
        val tree = newTree(file = file)

        tree.logD("T", "kept")
        assertFalse(tree.flushBlocking(300), "the write cannot succeed while the folder is missing")
        assertTrue(missingDir.mkdirs())
        tree.close()

        assertTrue(tree.flushBlocking(5000), "the consumer did not finish after close()")
        val written = file.readLines()
        assertEquals(1, written.size)
        assertTrue(written.single().endsWith(" DEBUG: T - kept"))
    }

    @Test
    fun `close lets the consumer drain what is queued then stop`() {
        holdConsumer()
        val tree = newTree()
        tree.logD("T", "one")
        tree.logD("T", "two")
        tree.logD("T", "three")

        tree.close()
        releaseConsumer()

        assertTrue(tree.flushBlocking(5000), "the consumer did not finish after close()")
        assertEquals(3, lines().size)

        tree.logD("T", "after close")
        assertTrue(tree.flushBlocking(1000))
        assertEquals(3, lines().size, "a line logged after close() must not be written")
    }
}
