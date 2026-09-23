package app.n_zik.android.core.rescue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Process coordination for the Rescue Center.
 *
 * When the Rescue Center opens, it asks the main app process to end itself, so that the
 * process guard ([RescueFiles.isMainProcessRunning]) becomes reachable without a manual
 * "Force stop" from Android settings. Two paths cooperate:
 *
 * 1. [requestKillMain] sends an internal broadcast: while the main process is alive it dies
 *    within a few seconds, even when its main thread is frozen (the main process registers
 *    its receiver on [KILL_REQUEST_HANDLER], a background thread — a manifest receiver would
 *    run on the main thread, which is exactly the thread stuck during an ANR).
 * 2. [requestKillMain] also writes the kill-request flag file: if the broadcast is lost (main
 *    process dead or fully frozen), the flag survives and the next main-process launch consumes
 *    it in [consumeKillRequest] and ends itself BEFORE any heavy initialization.
 *
 * The flag is removed BEFORE the kill, so a failed kill can never re-trigger itself on the
 * next launch (no kill loop). And [cancelKillRequest] discards the flag once the main process
 * is observed dead — but only while the process probe is trustworthy (below API 31, since
 * `getRunningAppProcesses` is restricted to the calling process from 31 on); from 31 the flag
 * stays until the receiver consumes it. Either way the flag must not leak into the next launch
 * and make a perfectly healthy app end itself at startup.
 *
 * Kept apart from [RescueFiles] (which is the Rescue Center's data-side file logic): this
 * object is the kill coordination shared by the main process (MainApplication) and the
 * `:rescue` process (RescueActivity / RescueScreen).
 */
object RescueProcess {

    private const val TAG = "RescueProcess"

    /** Name of the kill-request flag file, inside [Context.getFilesDir]. */
    internal const val KILL_REQUEST_FLAG_NAME = "rescue_kill_main.flag"

    /**
     * Action of the internal kill broadcast. Always sent with [Intent.setPackage], and the
     * receiver is registered with `RECEIVER_NOT_EXPORTED` (API 33+) — it is never visible
     * outside the app.
     */
    const val ACTION_KILL_MAIN = "app.n_zik.android.action.kill_main"

    /**
     * Intent extra carrying the per-request nonce.
     *
     * From API 33 on the kill receiver is registered non-exported, but on minSdk 24 through 32
     * it is exported and [ACTION_KILL_MAIN] is public — a third-party app could broadcast it.
     * The nonce (a random UUID per request) is shared only between the flag (written by the
     * `:rescue` process) and the intent; a broadcast that does not match the pending flag is
     * ignored, flag left intact.
     */
    internal const val EXTRA_KILL_NONCE = "app.n_zik.android.extra.kill_nonce"

    /**
     * Handler on which the main process registers its kill receiver, bound to a dedicated
     * background thread with its own Looper.
     *
     * A manifest-declared receiver (or one registered without a handler) is invoked on the
     * main thread — precisely the thread that is frozen during an ANR. A broadcast to a
     * context receiver reaches the app through a one-way binder call to its binder threads,
     * after which the framework posts the receive to the handler registered with
     * `registerReceiver`; registering this background handler makes `onReceive` run on that
     * background thread even while the main thread is stuck. (The public API has no
     * executor-based `registerReceiver` overload, so a `HandlerThread` looper is the
     * equivalent.) Daemon and process-lifetime like the rest of
     * [app.n_zik.android.utils.coroutines.NzikDispatchers]: never closed. Lazy so the
     * `:rescue` process, which only uses the flag side, never pays for the thread.
     */
    internal val KILL_REQUEST_HANDLER: Handler by lazy {
        HandlerThread("nzik-rescue-kill").apply {
            isDaemon = true
            start()
        }.let { Handler(it.looper) }
    }

    /**
     * Requests that the main app process end itself.
     *
     * Writes the kill-request flag FIRST, then sends the kill broadcast (immediate path,
     * delivered even while the main thread is frozen). The receiver verifies the broadcast's
     * nonce against the flag, so the flag must exist before the broadcast can be delivered.
     * The flag is also the safety net for a dead or fully frozen main process, consumed on the
     * next launch by [consumeKillRequest].
     *
     * Both steps are fire-and-forget: a failed broadcast (or flag write) is logged, never
     * thrown — the other path may still deliver the kill.
     */
    fun requestKillMain(context: Context) {
        val nonce = UUID.randomUUID().toString()
        Timber.tag(TAG).i("Requesting the main process to end itself (rescue pid=%d)", Process.myPid())
        val flagWritten = runCatching {
            writeKillRequestFlag(
                context.filesDir,
                "nonce=$nonce rescue pid=${Process.myPid()} at=${System.currentTimeMillis()}"
            )
        }.onFailure {
            Timber.tag(TAG).e(it, "Kill request flag not written; the broadcast would be unverifiable, skipping it")
        }.isSuccess
        if (!flagWritten) return
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_KILL_MAIN)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_KILL_NONCE, nonce)
            )
        }.onFailure {
            Timber.tag(TAG).e(it, "Kill broadcast not sent (the flag file still covers the request)")
        }
    }

    /**
     * Consumes the kill-request flag, if pending.
     *
     * Called by the main process at startup (before heavy initialization) and by its kill
     * receiver: in both cases the flag must be removed BEFORE the kill, so the request cannot
     * re-trigger a self-kill on the next launch (no kill loop).
     *
     * @return true when a kill request was pending and has been consumed — the caller must
     *   then end the process.
     */
    fun consumeKillRequest(context: Context): Boolean = consumeKillRequestFlag(context.filesDir)

    /** True while a kill-request flag is pending on disk. */
    fun hasKillRequest(context: Context): Boolean =
        File(context.filesDir, KILL_REQUEST_FLAG_NAME).exists()

    /**
     * The nonce stored in the pending flag, or null when there is no pending flag (or it is
     * unreadable / was not written by [requestKillMain]).
     */
    internal fun flagNonce(filesDir: File): String? {
        val flag = File(filesDir, KILL_REQUEST_FLAG_NAME)
        if (!flag.exists()) return null
        val payload = runCatching { flag.readText() }.getOrNull() ?: return null
        // A payload that does not start with the nonce token is a legacy (pre-nonce) flag:
        // it must never yield a "nonce" — otherwise a broadcast crafted with the first
        // token of a legacy payload could match and trigger the kill.
        if (!payload.startsWith("nonce=")) return null
        return payload.substringAfter("nonce=").substringBefore(' ').ifEmpty { null }
    }

    /**
     * Consumes the kill-request flag only when its nonce matches the one carried by the kill
     * broadcast — the sender verification for API < 33, where the receiver is exported: a
     * third-party app can broadcast [ACTION_KILL_MAIN] but cannot know the per-request nonce,
     * which only the flag and the broadcast share.
     *
     * A mismatch (or a missing flag) leaves the pending request untouched and returns false.
     */
    internal fun consumeKillRequestIfNonceMatches(filesDir: File, nonce: String): Boolean {
        if (flagNonce(filesDir) != nonce) {
            Timber.tag(TAG).i("Kill broadcast ignored (no pending flag matching the nonce)")
            return false
        }
        return consumeKillRequestFlag(filesDir)
    }

    /**
     * Discards the kill-request flag without ending anything.
     *
     * Called by the `:rescue` process once the main process is observed dead: the request is
     * then moot (a dead process cannot be killed), and the flag must not survive to the next
     * launch or that healthy launch would end itself at startup.
     */
    fun cancelKillRequest(context: Context) = cancelKillRequestFlag(context.filesDir)

    /**
     * Writes the kill-request flag into [filesDir], replacing a previous one.
     *
     * Atomic on POSIX: the payload is written to a temp file then renamed over the flag, so a
     * crash mid-write can never leave a truncated (or empty) flag that a later consumption
     * would treat as a valid pending request. The rename always ends with the FRESH payload
     * on disk (Windows cannot rename over an existing file: the stale flag is removed first
     * and the rename retried, with a direct write as the last resort) — a re-request must
     * never keep the previous nonce, or the hot kill would be silently refused.
     *
     * @param requestedBy free-form payload (kept out of the JVM-invisible [Process] API so
     *   the flag logic stays unit-testable); starts with `nonce=<uuid>` — the receiver's
     *   sender-verification key; the rest is written for the log at consumption time.
     */
    internal fun writeKillRequestFlag(filesDir: File, requestedBy: String): File {
        val flag = File(filesDir, KILL_REQUEST_FLAG_NAME)
        flag.parentFile?.mkdirs()
        val tmp = File(flag.path + ".tmp")
        tmp.writeText(requestedBy)
        if (tmp.renameTo(flag)) return flag
        // On Windows File.renameTo cannot replace an existing destination: remove the stale
        // flag and retry, so a re-request never silently keeps the previous (stale) nonce —
        // a stale nonce would make the hot kill be refused by the receiver.
        if (flag.delete() && tmp.renameTo(flag)) return flag
        tmp.delete()
        Timber.tag(TAG).w("Atomic flag write failed; falling back to a direct (non-atomic) write")
        flag.writeText(requestedBy)
        return flag
    }

    /**
     * File-based core of [consumeKillRequest]: reads the flag (for the log) and removes it
     * atomically.
     *
     * The flag is moved aside first (atomic on POSIX) and only then deleted: even if the
     * deletion fails, the original flag is already gone, so the request cannot re-trigger a
     * self-kill on the next launch (no kill loop). If even the move fails the flag is kept
     * and false is returned — a stale flag is preferable to a kill loop.
     *
     * @return true when a kill request was pending and has been consumed.
     */
    internal fun consumeKillRequestFlag(filesDir: File): Boolean {
        val flag = File(filesDir, KILL_REQUEST_FLAG_NAME)
        if (!flag.exists()) return false

        val requestedBy = runCatching { flag.readText() }.getOrNull()

        val consumed = File(flag.path + ".consumed")
        if (!flag.renameTo(consumed) && !flag.delete()) {
            Timber.tag(TAG).e("Kill request flag could not be consumed, keeping it; no self-kill this launch")
            return false
        }
        if (!consumed.delete()) {
            Timber.tag(TAG).e("Kill request parking file %s could not be deleted (a later cancel cleans it up)", consumed.name)
        }

        Timber.tag(TAG).i("Kill request consumed (%s); the process must end", requestedBy ?: "unreadable flag")
        return true
    }

    /**
     * File-based core of [cancelKillRequest]: removes the flag (and any leftover parking file)
     * without reporting anything to the caller.
     */
    internal fun cancelKillRequestFlag(filesDir: File) {
        val flag = File(filesDir, KILL_REQUEST_FLAG_NAME)
        val parking = File(flag.path + ".consumed")
        val tmp = File(flag.path + ".tmp")
        val left = listOfNotNull(
            flag.takeIf(File::exists),
            parking.takeIf(File::exists),
            tmp.takeIf(File::exists)
        )
        if (left.isEmpty()) return
        left.forEach { f ->
            if (f.delete()) {
                Timber.tag(TAG).d("Kill request discarded (%s removed: the main process is dead)", f.name)
            } else {
                Timber.tag(TAG).e("Could not discard the kill request flag %s", f.name)
            }
        }
    }

    /**
     * Registers the receiver that ends this (main) process when the Rescue Center requests it.
     *
     * It is registered on [KILL_REQUEST_HANDLER], a handler bound to a dedicated background
     * looper: a manifest receiver (or one without a handler) is invoked on the main thread —
     * precisely the thread that is frozen during an ANR — so the background looper receives
     * the broadcast while the main thread is stuck.
     *
     * Before consuming the request and dying, the broadcast's nonce is verified against the
     * pending flag: on API < 33 the receiver is exported, so the nonce is the only thing that
     * stops a third-party app from triggering the kill. The flag is consumed BEFORE the kill,
     * so the next launch does not end itself again (no kill loop).
     */
    fun registerKillMainReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val nonce = intent?.getStringExtra(EXTRA_KILL_NONCE)
                if (nonce.isNullOrEmpty()) {
                    Timber.tag(TAG).i("Kill broadcast ignored (no nonce)")
                    return
                }
                if (consumeKillRequestIfNonceMatches(context.filesDir, nonce)) {
                    Timber.tag(TAG).i("Kill request honored; ending the main process")
                    // killProcess is the public API for ending a process from inside (the
                    // framework's exitProcess is @hide and not part of the SDK).
                    Process.killProcess(Process.myPid())
                }
            }
        }
        // RECEIVER_NOT_EXPORTED only exists from API 33; below it the intra-app setPackage
        // intent plus the nonce are the only visibility/origin guarantee. The flags overload
        // needs API 26: on older devices the handler-only overload is used.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_KILL_MAIN),
                null,
                KILL_REQUEST_HANDLER,
                flags
            )
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_KILL_MAIN), null, KILL_REQUEST_HANDLER)
        }
    }
}
