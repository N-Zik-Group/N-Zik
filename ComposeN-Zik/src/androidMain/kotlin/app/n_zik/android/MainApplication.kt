package app.n_zik.android

import java.io.File

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Process
import android.os.StrictMode
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.metrolist.music.betterlyrics.BetterLyrics
import com.metrolist.music.discordrpc.DiscordRpc
import com.metrolist.music.discordrpc.GatewayWebSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.core.notifications.installAppNotificationChannels
import app.n_zik.android.BuildConfig
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.settings.applyUseLoginForSearch
import app.n_zik.android.core.backup.DatabaseBackup
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.backup.LibraryStartup
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.materializeImageCacheCustomMb
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.core.migration.DbCleanup
import app.n_zik.android.core.migration.MonthlyPlaylistCleanup
import app.n_zik.android.core.migration.NameConvergence
import app.n_zik.android.core.migration.RemovedSettingsMigration
import app.n_zik.android.core.migration.SameNameArtistDedup
import app.n_zik.android.core.network.client.applyProfileProxy
import app.n_zik.android.core.network.client.NetworkGate
import app.n_zik.android.core.network.client.Store
import app.n_zik.android.core.ondevice.SCAN_DEVICE_LIBRARY_ON_STARTUP_KEY
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.rescue.RescueProcess
import app.n_zik.android.core.rewind.scheduleRewindWork
import app.n_zik.android.core.settings.ensureDefaultColorPaletteMode
import app.n_zik.android.core.settings.ensurePlayerBehaviorDefaults
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.download.services.withProfileDownloadCommandOwner
import app.n_zik.android.extensions.audiobar.VisualizerCaptureCoordinator
import app.n_zik.android.extensions.musicbrainz.workers.MbBackfillWorker
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.modules.FfmpegModule
import app.n_zik.android.modules.ModuleDownloadCancelReceiver
import app.n_zik.android.modules.ModuleUpdateStatus
import app.n_zik.android.musicbrainz.MBCircuitBreakerPersistence
import app.n_zik.android.musicbrainz.MBLogger
import app.n_zik.android.musicbrainz.MBNetwork
import app.n_zik.android.musicbrainz.MusicBrainz
import app.n_zik.android.playback.services.InnerTubeXPlayer
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.debug.installStartupDiagnostics
import app.n_zik.android.utils.discordAvatarKey
import app.n_zik.android.utils.discordPersonalAccessTokenKey
import app.n_zik.android.utils.discordUsernameKey
import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.YouTubeArtistProvenance
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.isDiscordBrowsingEnabledKey
import app.n_zik.android.utils.isDiscordPresenceEnabledKey
import app.n_zik.android.utils.mbCircuitFailuresKey
import app.n_zik.android.utils.mbCircuitOpenUntilKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.readProfileIds
import app.n_zik.android.utils.regionOverrideKey
import app.n_zik.android.utils.saveProfileLastUsed
import app.n_zik.android.utils.useLoginForBrowseKey
import app.n_zik.android.utils.useYtLoginOnlyForBrowseKey
import app.n_zik.android.utils.ytAccountChannelHandleKey
import app.n_zik.android.utils.ytAccountEmailKey
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytAccountThumbnailKey
import app.n_zik.android.utils.ytAuthUserKey
import app.n_zik.android.utils.ytCookieExpiredKey
import app.n_zik.android.utils.ytCookieKey
import app.n_zik.android.utils.ytDataSyncIdKey
import app.n_zik.android.utils.ytVisitorDataKey
import app.n_zik.android.widget.WidgetAppearance
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YouTubeSession
import app.n_zik.innertube.hasYouTubeAuthCookie
import app.n_zik.innertube.models.ArtistConjunctions
import app.n_zik.innertube.utils.InnertubeLogger
import app.n_zik.kugou.KuGou
import app.n_zik.lastfm.LastFm
import app.n_zik.lrclib.LrcLib
import utils.VisualizerHelper

class MainApplication : Application(), SingletonImageLoader.Factory {
    override fun startService(service: Intent): ComponentName? =
        super.startService(withProfileDownloadCommandOwner(service))

    override fun startForegroundService(service: Intent): ComponentName? =
        super.startForegroundService(withProfileDownloadCommandOwner(service))


    internal val libraryStartup = LibraryStartup(
        recover = { DatabaseRestoreJournal.onProcessStart(this, mainProcess = isMainProcess()) },
        initialize = {
            if (isMainProcess()) initializeMainProcess()
            cleanRetiredLibraryFiles()
        },
    )

    internal suspend fun awaitLibraryReady() = libraryStartup.awaitReady()

    private fun cleanRetiredLibraryFiles() {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            DatabaseRestoreJournal.cleanRetiredFiles(filesDir, includeRescueBackups = true)
            runCatching { DatabaseRestoreJournal.cleanAbandonedImports(this@MainApplication) }
                .onFailure { Timber.tag("MainApplication").w(it, "Could not clean abandoned database imports") }
            runCatching { DatabaseBackup.cleanAbandonedExports(this@MainApplication) }
                .onFailure { Timber.tag("MainApplication").w(it, "Could not clean abandoned database exports") }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // RescueActivity runs in the :rescue process. Starting the full app there can make
        // that process claim WebView's data directory and crash the next main-process WebView.
        if (!isMainProcess()) {
            libraryStartup.start()
            return
        }

        Dependencies.init(this)

        // Consume fresh Rescue kill requests before library startup.
        // Clearing the request before exit prevents a restart loop.
        val staleKillRequest = runCatching { RescueProcess.discardStaleKillRequest(this) }.getOrDefault(false)
        if (!staleKillRequest && runCatching { RescueProcess.consumeKillRequest(this) }.getOrDefault(false)) {
            // killProcess is the public API for ending a process from inside (the framework's
            // exitProcess is @hide and not part of the SDK).
            Process.killProcess(Process.myPid())
            return
        }

        // Register the kill receiver before initialization so Rescue can stop a failed startup.
        RescueProcess.registerKillMainReceiver(this)

        // Register app shortcuts before library initialization so that the Rescue
        // shortcut exists even if initialization crashes below.
        refreshShortcutLanguage(ContextCompat.getContextForLanguage(this), refreshWidgets = false)

        libraryStartup.start()
    }

    private fun initializeMainProcess() {
        (readProfileIds() + DEFAULT_PROFILE_ID).forEach { id ->
            runCatching { materializeImageCacheCustomMb(profileDataPrefs(this, id)) }
                .onFailure { Timber.tag("MainApplication").w(it, "Could not normalize profile cache limits") }
        }
        // Publish library ownership after the lease is acquired, while the early kill
        // receiver stays available even when startup is still waiting.
        RescueProcess.startAliveMarkerUpdater(this)

        // Register cancellation early so a download can be stopped during startup.
        val moduleCancelReceiver = ModuleDownloadCancelReceiver()
        val moduleCancelFilter = IntentFilter(ModuleDownloadCancelReceiver.CANCEL_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(moduleCancelReceiver, moduleCancelFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(moduleCancelReceiver, moduleCancelFilter)
        }

        if (isDeviceLibraryEnabled() && preferences.getBoolean(SCAN_DEVICE_LIBRARY_ON_STARTUP_KEY, true)) {
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                scanDeviceLibrary()
            }
        }
        WidgetAppearance.observeIdlePreferences(this)
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            PickedImageEditor.cleanupOrphans(applicationContext)
        }

        // Seed System before composition because older preference reads default to Dark.
        ensureDefaultColorPaletteMode(preferences)
        ensurePlayerBehaviorDefaults(preferences)

        // The Profiles page uses this timestamp for its relative last-used label.
        runCatching {
            saveProfileLastUsed(getActiveProfile(this), System.currentTimeMillis())
        }.onFailure { t ->
            Timber.tag("MainApplication").w(t, "Could not record the active profile last use")
        }

        // Restore a fresh saved session without making it a startup dependency.
        runCatching {
            val listenTogetherClient = ListenTogetherClient(this)
            val listenTogetherManager = ListenTogetherManager(listenTogetherClient, this)
            listenTogetherManager.initialize()
        }.onFailure { t ->
            Timber.tag("MainApplication").e(t, "Listen Together init failed")
        }

        // Route InnertubeLogger (JVM module) to Timber (Android debug log)
        InnertubeLogger.addListener { tag, level, message, throwable ->
            when (level) {
                InnertubeLogger.Level.DEBUG -> Timber.tag(tag).d(throwable, "%s", message)
                InnertubeLogger.Level.INFO -> Timber.tag(tag).i(throwable, "%s", message)
                InnertubeLogger.Level.WARN -> Timber.tag(tag).w(throwable, "%s", message)
                InnertubeLogger.Level.ERROR -> Timber.tag(tag).e(throwable, "%s", message)
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

        ArtistConjunctions.conjunctions = listOf(R.string.common_artist_conjunction_and).mapNotNull { id ->
            runCatching { getString(id) }.getOrNull()
        }

        // The mini-player can request a snapshot before the full visualizer mounts.
        VisualizerHelper.snapshotProvider = VisualizerCaptureCoordinator::currentSnapshot

        // Same seam pattern (issue #606, Goal G5): modules/discordrpc is a forked submodule
        // and can't depend on NzikDispatchers either.
        DiscordRpc.backgroundDispatcher = NzikDispatchers.DATA

        migrateCredentialsToEncrypted()
        YouTubeArtistProvenance.observe(this)
        runCatching { RemovedSettingsMigration.run(preferences) }
            .onFailure { Timber.tag("MainApplication").w(it, "Removed settings migration failed") }
        // One-shot cleanup of the legacy `monthly:YYYYMM` playlists, abandoned with the
        // on-the-fly generation mechanism (flag-guarded, idempotent, background)
        runCatching { MonthlyPlaylistCleanup.run(this) }
            .onFailure { Timber.tag("MainApplication").w(it, "Monthly playlists cleanup failed") }
        // Run repair passes sequentially so each sees its predecessor's writes.
        // Clear prior results so Maintenance reports this boot's outcomes.
        MaintenanceStateStore.wipeAll(this)
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching { DbCleanup.runPass(this@MainApplication) }
                .onSuccess { result ->
                    // The persistence itself is isolated too: a failing write must not
                    // cancel the launch block and skip the two remaining passes
                    runCatching {
                        MaintenanceStateStore.saveDbCleanup(
                            this@MainApplication,
                            MaintenanceDbCleanupState(
                                System.currentTimeMillis(),
                                result.removed,
                                result.removedLinks,
                            ),
                        )
                    }.onFailure { Timber.tag("MainApplication").w(it, "Could not persist the artist link cleanup state") }
                }
                .onFailure { Timber.tag("MainApplication").e(it, "Artist link cleanup failed (retry on next launch)") }
            runCatching { SameNameArtistDedup.runPass(this@MainApplication) }
                .onSuccess { result ->
                    runCatching {
                        MaintenanceStateStore.saveDedup(
                            this@MainApplication,
                            MaintenanceDedupState(
                                timestamp = System.currentTimeMillis(),
                                groups = result.groups,
                                resolved = result.resolved,
                                songResolved = result.songResolved,
                                merged = result.merged,
                                flagged = result.flagged,
                                skipped = result.skipped,
                                deferred = result.deferred,
                                localKept = result.localKept,
                                records = result.records,
                            ),
                        )
                    }.onFailure { Timber.tag("MainApplication").w(it, "Could not persist the same-name dedup state") }
                }
                .onFailure { Timber.tag("MainApplication").e(it, "Same-name artist dedup failed (retry on next launch)") }
            runCatching { NameConvergence.runPass() }
                .onSuccess { summary ->
                    runCatching {
                        MaintenanceStateStore.saveConvergence(
                            this@MainApplication,
                            MaintenanceConvergenceState(System.currentTimeMillis(), summary),
                        )
                    }.onFailure { Timber.tag("MainApplication").w(it, "Could not persist the name convergence state") }
                }
                .onFailure { Timber.tag("MainApplication").e(it, "Name convergence failed (retry on next launch)") }
        }
        InnerTubeXPlayer.initialize(this)

        // Setup session BEFORE prewarm — ensures session is stable when IO thread starts
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            applyProfileProxy(this)
            val regionOverride = preferences.getString(regionOverrideKey, "")
            if (!regionOverride.isNullOrBlank()) {
                Innertube.regionOverrideActive = true
                Innertube.regionOverride = regionOverride
            }
            val useLoginForBrowse = preferences.getBoolean(useLoginForBrowseKey, true)
            val youtubeLoginEnabled = encryptedPreferences.getBoolean(enableYouTubeLoginKey, false)
            Innertube.useLoginForBrowse = false
            applyUseLoginForSearch(this, isLoginEnabled = false)
            
            // The JVM modules use this app-provided connectivity gate because they have no Android context.
            Innertube.offlineGate = NetworkGate::isOnline
            LrcLib.offlineGate = NetworkGate::isOnline
            MusicBrainz.offlineGate = NetworkGate::isOnline
            LastFm.offlineGate = NetworkGate::isOnline
            KuGou.offlineGate = NetworkGate::isOnline
            BetterLyrics.offlineGate = NetworkGate::isOnline
            GatewayWebSocket.offlineGate = NetworkGate::isOnline

            // Preserve the search preference across login changes. Guests already search anonymously.
            if (youtubeLoginEnabled) {
                Store.initSession(this@MainApplication)
            } else {
                Store.setSession(YouTubeSession(null, null, null, null))
            }
            val savedCookie = Innertube.cookie
            if (!savedCookie.isNullOrBlank()) {
                val hasAuthCookie = hasYouTubeAuthCookie(savedCookie)
                val wasExpired = preferences.getBoolean(ytCookieExpiredKey, false)
                if (!hasAuthCookie) {
                    Timber.tag("MainApplication").w("YouTube cookie has no authentication token; disabling authenticated browsing")
                    cookieStatus = CookieStatus.INVALID
                    Innertube.useLoginForBrowse = false
                    preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
                } else if (wasExpired) {
                    Timber.tag("MainApplication").w("YouTube cookie was previously marked expired — session is expired, disabling useLoginForBrowse")
                    cookieStatus = CookieStatus.EXPIRED
                    Innertube.useLoginForBrowse = false
                    preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
                } else {
                    Timber.tag("MainApplication").d("YouTube cookie loaded")
                    cookieStatus = CookieStatus.VALID
                    Innertube.useLoginForBrowse = useLoginForBrowse
                    applyUseLoginForSearch(this, isLoginEnabled = true)
                }
            } else {
                Timber.tag("MainApplication").w("No YouTube cookie — disabling useLoginForBrowse")
                cookieStatus = CookieStatus.NOT_LOGGED_IN
                Innertube.useLoginForBrowse = false
                if (youtubeLoginEnabled) preferences.edit().putBoolean(useLoginForBrowseKey, false).apply()
            }

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

        // Only detect updates at launch. Downloads start on first use or from Extensions.
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val moduleUpdate = withContext(NzikDispatchers.DATA) { FfmpegModule.checkForUpdate() }
            if (moduleUpdate is ModuleUpdateStatus.UpdateAvailable) {
                Toaster.i(R.string.modules_update_available)
            }
        }

        // Enrich artist/album MusicBrainz metadata in background (first run after 1h)
        MbBackfillWorker.schedule(this)

        scheduleRewindWork(this)

        installStartupDiagnostics()
    }

    private fun migrateCredentialsToEncrypted() {
        val keysToMigrateString = listOf(
            ytCookieKey, ytVisitorDataKey, ytDataSyncIdKey, ytAuthUserKey, ytAccountNameKey, ytAccountEmailKey,
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

    private var shortcutLocaleTags: String? = null
    private var launcherLocaleTags: String? = null

    internal fun refreshShortcutLanguage(context: Context, refreshWidgets: Boolean = true): Boolean {
        val localeTags = context.resources.configuration.locales.toLanguageTags()
        val widgetsNeedRefresh = refreshWidgets && launcherLocaleTags != localeTags
        if (widgetsNeedRefresh) launcherLocaleTags = localeTags
        if (shortcutLocaleTags != localeTags && app.n_zik.android.shortcuts.registerAppShortcuts(context)) {
            shortcutLocaleTags = localeTags
        }
        return widgetsNeedRefresh
    }

    private var notificationChannelLocaleTags: String? = null

    internal fun createNotificationChannels(strings: Resources = appTextResources()) {
        val localeTags = strings.configuration.locales.toLanguageTags()
        if (notificationChannelLocaleTags == localeTags) return
        installAppNotificationChannels(this, strings)
        notificationChannelLocaleTags = localeTags
        sendBroadcast(Intent(app.n_zik.android.bridge.BridgeServerService.ACTION_REFRESH_LOCALE).setPackage(packageName))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isMainProcess()) {
            createNotificationChannels()
            refreshShortcutLanguage(ContextCompat.getContextForLanguage(this), refreshWidgets = false)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return if (Dependencies.isInitialized) {
            ImageCacheFactory.LOADER
        } else {
            ImageCacheFactory.bootstrapLoader(context)
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

internal suspend fun Context.awaitLibraryReady() {
    (applicationContext as? MainApplication)?.awaitLibraryReady()
}

internal fun Context.requireLibraryAccess() {
    (applicationContext as? MainApplication)?.libraryStartup?.requireRecovered()
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


