package app.it.fast4x.rimusic.utils

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import app.n_zik.android.utils.coroutines.NzikDispatchers
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

// https://stackoverflow.com/q/53502244/16885569
// I found four ways to make the system not kill the stopped foreground service: e.g. when

// the player is paused:
// 1 - Use the solution below - hacky;

// 2 - Do not call stopForeground but provide a button to dismiss the notification - bad UX;
// 3 - Lower the targetSdk (e.g. to 23) - security concerns;

// 4 - Host the service in a separate process - overkill and pathetic.
abstract class InvincibleService : Service() {

    protected abstract val isInvincibilityEnabled: Boolean

    protected abstract val notificationId: Int

    private var invincibility: Invincibility? = null

    private val isAllowedToStartForegroundServices: Boolean
        get() = !isAtLeastAndroid12 || isIgnoringBatteryOptimizations

    override fun onBind(intent: Intent?): Binder? {
        invincibility?.stop()
        invincibility = null
        return null
    }

    override fun onRebind(intent: Intent?) {
        invincibility?.stop()
        invincibility = null
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (isInvincibilityEnabled && isAllowedToStartForegroundServices) {
            invincibility = Invincibility()
        }
        return true
    }

    override fun onDestroy() {
        invincibility?.stop()
        invincibility = null
        super.onDestroy()
    }

    protected fun makeInvincible(isInvincible: Boolean = true) {
        if (isInvincible) {
            invincibility?.start()
        } else {
            invincibility?.stop()
        }
    }

    protected abstract fun shouldBeInvincible(): Boolean

    protected abstract fun notification(): Notification?

    private inner class Invincibility : BroadcastReceiver() {
        private var isStarted = false
        private val intervalMs = 30_000L
        private val tickScope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
        private var tickJob: Job? = null

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // Equivalent of the former `handler.post(this)`: run a tick now, then
                    // keep ticking. Restarting the loop (instead of posting one more

                    // callback) also fixes a former quirk where a still-pending delayed
                    // tick could double the tick chain.
                    tickJob?.cancel()
                    tickJob = tickScope.launch { runTickLoop() }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // Equivalent of the former `handler.removeCallbacks(this)`
                    tickJob?.cancel()
                    notification()?.let { notification ->
                        runCatching {
                            ServiceCompat.startForeground(
                                this@InvincibleService,
                                notificationId,
                                notification,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                                } else {
                                    0
                                }
                            )
                        }.onFailure {
                            Timber.tag("InvincibleService").e("Failed startForeground onReceive ${it.stackTraceToString()}")
                        }
                    }
                }
            }
        }

        @Synchronized
        fun start() {
            if (!isStarted) {
                isStarted = true
                // First tick after one interval (equivalent of the former
                // `handler.postDelayed(this, intervalMs)`), then every interval.
                tickJob = tickScope.launch {
                    delay(intervalMs)
                    runTickLoop()
                }
                registerReceiver(this, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                })
            }
        }

        @Synchronized
        fun stop() {
            if (isStarted) {
                // Cancel the tick loop only: the scope itself stays usable so a later
                // start() can launch a new tick (same reusability as the former Handler).
                tickJob?.cancel()
                unregisterReceiver(this)
                isStarted = false
            }
        }

        private suspend fun runTickLoop() {
            while (currentCoroutineContext().isActive) {
                // A failure in the subclass hooks (shouldBeInvincible/notification) must not
                // silently kill the keep-alive loop: log it and keep ticking.
                runCatching { tick() }.onFailure {
                    Timber.tag("InvincibleService").e(it, "Keep-alive tick failed")
                }
                delay(intervalMs)
            }
        }

        private fun tick() {
            if (shouldBeInvincible() && isAllowedToStartForegroundServices) {
                notification()?.let { notification ->
                    runCatching {
                        ServiceCompat.startForeground(
                            this@InvincibleService,
                            notificationId,
                            notification,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            } else {
                                0
                            }
                        )
                    }.onFailure {
                        Timber.tag("InvincibleService").e("Failed startForeground run ${it.stackTraceToString()}")
                    }
                    runCatching {
                        stopForeground(false)
                    }.onFailure {
                        Timber.tag("InvincibleService").e("Failed stopForeground run ${it.stackTraceToString()}")
                    }
                }
            }
        }
    }
}
