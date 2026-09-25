package app.n_zik.android

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import java.io.File
import timber.log.Timber

import app.it.fast4x.rimusic.utils.CaptureCrash
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.isValidIP
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.proxyUsernameKey
import app.it.fast4x.rimusic.utils.proxyPasswordKey
import app.it.fast4x.rimusic.utils.regionOverrideKey
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.it.fast4x.rimusic.utils.useYtLoginOnlyForBrowseKey
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytCookieExpiredKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.migration.DbCleanup
import app.n_zik.android.core.migration.MonthlyPlaylistCleanup
import app.n_zik.android.core.migration.RemovedSettingsMigration
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.network.client.Store
import app.n_zik.android.core.rescue.RescueProcess
import app.n_zik.android.extensions.audiobar.VisualizerCaptureCoordinator
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.logging.FileLoggingTree
import app.n_zik.android.utils.logging.flushThenDelegate
import me.knighthat.invidious.Invidious
import com.metrolist.music.discordrpc.DiscordRpc
import utils.VisualizerHelper
import app.n_zik.android.BuildConfig
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.InnerTubeXPlayer
import it.fast4x.innertube.utils.ProxyPreferenceItem
import it.fast4x.innertube.utils.ProxyPreferences
import java.net.Proxy
import app.n_zik.android.playback.services.prewarmPoToken
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.utils.InnertubeLogger
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.invidious.utils.InvidiousLogger
import app.n_zik.android.extensions.musicbrainz.workers.MbBackfillWorker
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.components.ui.screens.rewind.RewindYearlyReminderWorker
import app.n_zik.android.core.rewind.RewindMonthlyPlaylistWorker
import app.n_zik.android.core.rewind.RewindYearlyPlaylistWorker
import app.n_zik.android.musicbrainz.MBCircuitBreakerPersistence
import app.n_zik.android.musicbrainz.MBLogger
import app.n_zik.android.musicbrainz.MBNetwork
import app.n_zik.android.musicbrainz.MusicBrainz
import app.it.fast4x.rimusic.utils.mbCircuitOpenUntilKey
import app.it.fast4x.rimusic.utils.mbCircuitFailuresKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // RescueActivity runs in the :rescue process. Starting the full app there can make
        // that process claim WebView's data directory and crash the next main-process WebView.
        if (!isMainProcess()) return

        // Rescue Center safety net: the Rescue writes a kill-request flag when its hot kill
        // (broadcast) may be lost (main process dead or fully frozen). Consume it BEFORE any
        // heavy initialization and end this process. The flag was consumed before the kill,
        // so the next launch cannot self-kill again (no kill loop). If the flag cannot even be
        // read at boot, do not kill: a stale flag is preferable to a broken launch loop.
        // A leftover flag older than the max age (left by an older app version, which
        // recorded the kill request on every Rescue Center open even while the process was
        // alive) is discarded WITHOUT killing — only a fresh request justifies it.
        if (runCatching { RescueProcess.discardStaleKillRequest(this) }.getOrDefault(false)) {
            return
        }

        if (runCatching { RescueProcess.consumeKillRequest(this) }.getOrDefault(false)) {
            // killProcess is the public API for ending a process from inside (the framework's
            // exitProcess is @hide and not part of the SDK).
            Process.killProcess(Process.myPid())
            return
        }

        // Same Rescue Center: register the receiver that ends this process on demand. Early,
        // BEFORE Dependencies.init, so the receiver exists even if initialization crashes below
        // (same rationale as the shortcuts registration just below).
        RescueProcess.registerKillMainReceiver(this)

        // Same Rescue Center: start the alive-marker updater — the only reliable liveness
        // signal the `:rescue` process can see from API 31 on (the process probe is then
        // restricted to the calling process). Background thread: the marker stays fresh even
        // while the main thread is frozen, and it is never stopped (it lives with the process).
        RescueProcess.startAliveMarkerUpdater(this)

        // Register app shortcuts early, BEFORE Dependencies.init, so that the Rescue
        // shortcut exists even if initialization crashes below.
        app.n_zik.android.shortcuts.registerAppShortcuts(this)

        Dependencies.init(this)

        // Route InnertubeLogger (JVM module) to Timber (Android debug log)
        InnertubeLogger.addListener { tag, level, message, throwable ->
            when (level) {
                InnertubeLogger.Level.DEBUG -> Timber.tag(tag).d(throwable, "%s", message)
                InnertubeLogger.Level.INFO -> Timber.tag(tag).i(throwable, "%s", message)
                InnertubeLogger.Level.WARN -> Timber.tag(tag).w(throwable, "%s", message)
                InnertubeLogger.Level.ERROR -> Timber.tag(tag).e(throwable, "%s", message)
            }
        }

        // Route InvidiousLogger (JVM module) to Timber (Android debug log)
        InvidiousLogger.addListener { tag, level, message, throwable ->
            when (level) {
                InvidiousLogger.Level.DEBUG -> Timber.tag(tag).d(throwable, "%s", message)
                InvidiousLogger.Level.INFO -> Timber.tag(tag).i(throwable, "%s", message)
                InvidiousLogger.Level.WARN -> Timber.tag(tag).w(throwable, "%s", message)
                InvidiousLogger.Level.ERROR -> Timber.tag(tag).e(throwable, "%s", message)
            }
        }

        // Route MBLogger (JVM module) to Timber (Android debug log)
        MusicBrainz.appVersion = BuildConfig.VERSION_NAME
        // Persist the circuit breaker's cooldown across app restarts — otherwise
        // force-quitting the app during a MusicBrainz outage would silently reset it.
        MBCircuitBreakerPersistence.load = { preferences.getLong(mbCircuitOpenUntilKey, 0L) }
        MBCircuitBreakerPersistence.save = { value -> preferences.edit().putLong(mbCircuitOpenUntilKey, value).apply() }
        MBCircuitBreakerPersistence.loadFailures = { preferences.getInt(mbCircuitFailuresKey, 0) }
        MBCircuitBreakerPersistence.saveFailures = { value -> preferences.edit().putInt(mbCircuitFailuresKey, value).apply() }
        MBLogger.addListener { tag, level, message, throwable ->
            when (level) {
                MBLogger.Level.DEBUG -> Timber.tag(tag).d(throwable, "%s", message)
                MBLogger.Level.INFO -> Timber.tag(tag).i(throwable, "%s", message)
                MBLogger.Level.WARN -> Timber.tag(tag).w(throwable, "%s", message)
                MBLogger.Level.ERROR -> Timber.tag(tag).e(throwable, "%s", message)
            }
        }

        ArtistConjunctions.conjunctions = listOf(R.string.and).mapNotNull { id ->
            runCatching { getString(id) }.getOrNull()
        }

        // Wire the visualizer capture seam (issue #606) here, not in a composable: SeekBarVisualizer
        // (mini-player) can mount before the full NextVisualizer screen ever composes, and this must
        // be set before ANY VisualizerHelper.getFft()/getWave() call — nextvisualizer can't depend
        // on coroutines, so it exposes this plain function-type injection point instead.
        VisualizerHelper.snapshotProvider = VisualizerCaptureCoordinator::currentSnapshot

        // Same seam pattern (issue #606, Goal D Lot 1): extensions/innertube is a pure kotlin("jvm")
        // module and can't depend on NzikDispatchers either.
        Invidious.backgroundDispatcher = NzikDispatchers.DATA

        // Same seam pattern (issue #606, Goal G5): modules/discordrpc is a forked submodule
        // and can't depend on NzikDispatchers either.
        DiscordRpc.backgroundDispatcher = NzikDispatchers.DATA

        migrateCredentialsToEncrypted()
        runCatching { RemovedSettingsMigration.run(preferences) }
            .onFailure { Timber.tag("MainApplication").w(it, "Removed settings migration failed") }
        // One-shot cleanup of the legacy `monthly:YYYYMM` playlists, abandoned with the
        // on-the-fly generation mechanism (flag-guarded, idempotent, background)
        runCatching { MonthlyPlaylistCleanup.run(this) }
            .onFailure { Timber.tag("MainApplication").w(it, "Monthly playlists cleanup failed") }
        // Cleanup of polluted artist↔song links accumulated by the old add-only
        // mapping (idempotent, background) — re-runs on every launch, so it also
        // heals links that reappear through database imports
        runCatching { DbCleanup.run(this) }
            .onFailure { Timber.tag("MainApplication").w(it, "Artist link cleanup failed") }
        InnerTubeXPlayer.initialize(this)

        // Setup session BEFORE prewarm — ensures session is stable when IO thread starts
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            var proxy: Proxy? = null
            if (preferences.getBoolean(isProxyEnabledKey, false)) {
                val hostName = preferences.getString(proxyHostnameKey, null)
                val proxyPort = preferences.getInt(proxyPortKey, 8080)
                val proxyMode = preferences.getEnum(proxyModeKey, Proxy.Type.HTTP)
                val proxyUsername = preferences.getString(proxyUsernameKey, "")
                val proxyPassword = preferences.getString(proxyPasswordKey, "")
                if (isValidIP(hostName)) {
                    hostName?.let { hName ->
                        ProxyPreferences.preference = ProxyPreferenceItem(hName, proxyPort, proxyMode)
                        proxy = ProxyPreferences.preference?.let { pref -> it.fast4x.innertube.utils.getProxy(pref) }
                        if (!proxyUsername.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                            Innertube.proxyAuth = "$proxyUsername:$proxyPassword"
                        }
                    }
                } else {
                    Timber.w("Proxy preference is null or invalid, running without proxy")
                }
            } else {
                Timber.w("Proxy preference is null, running without proxy")
            }
            val regionOverride = preferences.getString(regionOverrideKey, "")
            if (!regionOverride.isNullOrBlank()) {
                Innertube.regionOverrideActive = true
                Innertube.regionOverride = regionOverride
            }
            val useLoginForBrowse = preferences.getBoolean(useLoginForBrowseKey, true)
            Innertube.useLoginForBrowse = useLoginForBrowse
            
            NetworkClientFactory.configure(
                proxy = proxy,
                cacheDir = externalCacheDir ?: cacheDir
            )
            Innertube.proxy = proxy
            MBNetwork.proxy = proxy
            
            val savedCookie = encryptedPreferences.getString(ytCookieKey, "")
            if (!savedCookie.isNullOrBlank()) {
                Innertube.cookie = savedCookie
                Innertube.visitorData = encryptedPreferences.getString(ytVisitorDataKey, null)?.takeIf { it.isNotBlank() }
                Innertube.dataSyncId = encryptedPreferences.getString(ytDataSyncIdKey, null)?.takeIf { it.isNotBlank() }

                val hasSAPISID = savedCookie.contains("SAPISID")
                val hasLoginInfo = savedCookie.contains("LOGIN_INFO")
                val wasExpired = preferences.getBoolean(ytCookieExpiredKey, false)
                if (!hasSAPISID && !hasLoginInfo) {
                    Timber.tag("MainApplication").w("YouTube cookie present but missing SAPISID/LOGIN_INFO — session may be expired, disabling useLoginForBrowse")
                    cookieStatus = CookieStatus.INVALID
                    Innertube.useLoginForBrowse = false
                    preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
                } else if (wasExpired) {
                    Timber.tag("MainApplication").w("YouTube cookie was previously marked expired — session is expired, disabling useLoginForBrowse")
                    cookieStatus = CookieStatus.EXPIRED
                    Innertube.useLoginForBrowse = false
                    preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
                } else {
                    Timber.tag("MainApplication").d("YouTube cookie loaded (SAPISID=$hasSAPISID, LOGIN_INFO=$hasLoginInfo)")
                    cookieStatus = CookieStatus.VALID
                }
            } else {
                Timber.tag("MainApplication").w("No YouTube cookie — disabling useLoginForBrowse")
                cookieStatus = CookieStatus.NOT_LOGGED_IN
                Innertube.useLoginForBrowse = false
                preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
            }

            // Initialize Store session (like Metrolist's DataStore pattern)
            Store.initSession(this@MainApplication)
            Store.prefetchCookie()

        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }

        // Prewarm InnerTubeX in background — wait for visitorData like Metrolist
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            try {
                // Wait up to 12s for visitorData (like Metrolist)
                var waitedMs = 0
                while (Innertube.visitorData.isNullOrBlank() && waitedMs < 12_000) {
                    delay(500)
                    waitedMs += 500
                }
                InnerTubeXPlayer.prewarm()
                Timber.tag("MainApplication").d("InnerTubeX prewarm completed")
            } catch (e: CancellationException) {
                Timber.tag("MainApplication").w("InnerTubeX prewarm cancelled (session changed)")
            } catch (e: Exception) {
                Timber.tag("MainApplication").w(e, "InnerTubeX prewarm failed")
            }
        }

        createNotificationChannels()

        // Enrich artist/album MusicBrainz metadata in background (first run after 1h)
        MbBackfillWorker.schedule(this)

        // Monthly rewind reminder (next 1st of the month, WorkManager, self-rescheduling)
        RewindReminderWorker.schedule(this)

        // Yearly rewind reminder (next 1st of January, WorkManager, self-rescheduling)
        RewindYearlyReminderWorker.schedule(this)

        // Rewind monthly playlist (next 1st of the month, WorkManager, self-rescheduling)
        RewindMonthlyPlaylistWorker.schedule(this)

        // Rewind yearly playlist (next 1st of January, WorkManager, self-rescheduling)
        RewindYearlyPlaylistWorker.schedule(this)

        /**** LOG *********/
        val logEnabled = preferences.getBoolean(logDebugEnabledKey, false)
        
        // Always create logs directory and set up crash handler
        val dir = filesDir.resolve("logs").also {
            if (it.exists()) return@also
            it.mkdir()
        }
        
        // Always set up crash handler regardless of debug mode
        val crashCapture = CaptureCrash(dir.absolutePath, this)
        Thread.setDefaultUncaughtExceptionHandler(crashCapture)
        
        if (logEnabled) {
            val fileLoggingTree = FileLoggingTree(File(dir, "N-Zik_log.txt"))
            Timber.plant(fileLoggingTree)
            // The tree writes asynchronously: give the queued lines up to 2 s to reach the disk
            // before CaptureCrash writes the crash log and kills the process.
            Thread.setDefaultUncaughtExceptionHandler(flushThenDelegate(fileLoggingTree, 2000, crashCapture))
            Timber.tag("MainApplication").d("Log enabled at ${dir.absolutePath}")
        } else {
            Timber.uprootAll()
            Timber.plant(Timber.DebugTree())
        }

        // Startup banner: device info + app version
        Timber.tag("Startup").i("=".repeat(50))
        Timber.tag("Startup").i("N-Zik v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) [${BuildConfig.APPLICATION_ID}]")
        Timber.tag("Startup").i("Manufacturer: ${Build.MANUFACTURER}")
        Timber.tag("Startup").i("Device: ${Build.MODEL} (${Build.DEVICE})")
        Timber.tag("Startup").i("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        Timber.tag("Startup").i("Board: ${Build.BOARD} | Hardware: ${Build.HARDWARE}")
        Timber.tag("Startup").i("Build: ${Build.FINGERPRINT}")
        Timber.tag("Startup").i("=".repeat(50))
        /**** LOG *********/
    }

    private fun migrateCredentialsToEncrypted() {
        val keysToMigrateString = listOf(
            ytCookieKey, ytVisitorDataKey, ytDataSyncIdKey, ytAccountNameKey, ytAccountEmailKey,
            ytAccountChannelHandleKey, ytAccountThumbnailKey, discordPersonalAccessTokenKey,
            discordAvatarKey, discordUsernameKey
        )
        val keysToMigrateBoolean = listOf(
            enableYouTubeLoginKey, enableYouTubeSyncKey, useYtLoginOnlyForBrowseKey,
            isDiscordPresenceEnabledKey, isDiscordBrowsingEnabledKey
        )

        val edit = preferences.edit()
        val encryptedEdit = encryptedPreferences.edit()
        var migrated = false

        for (key in keysToMigrateString) {
            if (preferences.contains(key)) {
                val value = preferences.getString(key, null)
                if (value != null) {
                    encryptedEdit.putString(key, value)
                    edit.remove(key)
                    migrated = true
                }
            }
        }
        
        for (key in keysToMigrateBoolean) {
            if (preferences.contains(key)) {
                val value = preferences.getBoolean(key, false)
                encryptedEdit.putBoolean(key, value)
                edit.remove(key)
                migrated = true
            }
        }

        if (migrated) {
            encryptedEdit.commit()
            edit.commit()
            Timber.tag("MainApplication").i("Migrated credentials to encryptedPreferences")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel for music player
            val playerChannel = NotificationChannel(
                PlayerServiceModern.NotificationChannelId,
                applicationContext.getString(R.string.player),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.player)
                setShowBadge(false)
            }

            // Channel for sleep timer
            val sleepTimerChannel = NotificationChannel(
                PlayerServiceModern.SleepTimerNotificationChannelId,
                applicationContext.getString(R.string.sleep_timer),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.sleep_timer)
                setShowBadge(false)
            }

            // Channel for downloads
            val downloadChannel = NotificationChannel(
                MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.download),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.download)
                setShowBadge(false)
            }

            // Channel for sync
            val syncChannel = NotificationChannel(
                "sync_channel_id",
                applicationContext.getString(R.string.sync),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.sync_notifications)
                setShowBadge(false)
            }

            // Channel for the monthly rewind reminder
            val rewindChannel = NotificationChannel(
                RewindReminderWorker.CHANNEL_ID,
                applicationContext.getString(R.string.rw_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.rw_channel)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(playerChannel, sleepTimerChannel, downloadChannel, syncChannel, rewindChannel)
            )
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return if (Dependencies.isInitialized) {
            ImageCacheFactory.LOADER
        } else {
            ImageLoader.Builder(context).build()
        }
    }

    enum class CookieStatus { NOT_LOGGED_IN, VALID, INVALID, EXPIRED }

    companion object {
        var cookieStatus: CookieStatus = CookieStatus.NOT_LOGGED_IN
            internal set
    }

    private fun isMainProcess(): Boolean {
        val processName =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                runCatching {
                    File("/proc/self/cmdline").readText().substringBefore('\u0000')
                }.getOrNull()?.takeIf(String::isNotBlank)
                    ?: run {
                        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        activityManager.runningAppProcesses
                            ?.firstOrNull { it.pid == Process.myPid() }
                            ?.processName
                    }
            }
        return processName == packageName
    }
}

object Dependencies {
    lateinit var application: MainApplication
        private set

    val isInitialized: Boolean
        get() = ::application.isInitialized

    internal fun init(application: MainApplication) {

        this.application = application
    }
}


