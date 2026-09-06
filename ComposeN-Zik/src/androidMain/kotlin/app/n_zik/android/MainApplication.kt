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
import app.it.fast4x.rimusic.utils.FileLoggingTree
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
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.network.client.Store
import app.n_zik.android.BuildConfig
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.InnerTubeXPlayer
import it.fast4x.innertube.utils.ProxyPreferenceItem
import it.fast4x.innertube.utils.ProxyPreferences
import java.net.Proxy
import app.n_zik.android.playback.services.prewarmPoToken
import it.fast4x.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // CrashActivity runs in a separate process. Starting the full app there can make that
        // process claim WebView's data directory and crash the next main-process WebView.
        if (!isMainProcess()) return

        Dependencies.init(this)
        migrateCredentialsToEncrypted()
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
            
            val savedCookie = encryptedPreferences.getString(ytCookieKey, "")
            if (!savedCookie.isNullOrBlank()) {
                Innertube.cookie = savedCookie
                Innertube.visitorData = encryptedPreferences.getString(ytVisitorDataKey, null)?.takeIf { it.isNotBlank() }
                Innertube.dataSyncId = encryptedPreferences.getString(ytDataSyncIdKey, null)?.takeIf { it.isNotBlank() }

                val hasSAPISID = savedCookie.contains("SAPISID")
                val hasLoginInfo = savedCookie.contains("LOGIN_INFO")
                val wasExpired = preferences.getBoolean(ytCookieExpiredKey, false)
                if (!hasSAPISID && !hasLoginInfo) {
                    Timber.tag("MainApplication").w("YouTube cookie present but missing SAPISID/LOGIN_INFO — session may be expired")
                    cookieStatus = CookieStatus.INVALID
                } else if (wasExpired) {
                    Timber.tag("MainApplication").w("YouTube cookie was previously marked expired — session is expired")
                    cookieStatus = CookieStatus.EXPIRED
                } else {
                    Timber.tag("MainApplication").d("YouTube cookie loaded (SAPISID=$hasSAPISID, LOGIN_INFO=$hasLoginInfo)")
                    cookieStatus = CookieStatus.VALID
                }
            } else {
                cookieStatus = CookieStatus.NOT_LOGGED_IN
            }

            // Initialize Store session (like Metrolist's DataStore pattern)
            Store.initSession(this@MainApplication)

        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }

        // Prewarm InnerTubeX in background — wait for visitorData like Metrolist
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
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

        /**** LOG *********/
        val logEnabled = preferences.getBoolean(logDebugEnabledKey, false)
        
        // Always create logs directory and set up crash handler
        val dir = filesDir.resolve("logs").also {
            if (it.exists()) return@also
            it.mkdir()
        }
        
        // Always set up crash handler regardless of debug mode
        Thread.setDefaultUncaughtExceptionHandler(CaptureCrash(dir.absolutePath, this))
        
        if (logEnabled) {
            Timber.plant(FileLoggingTree(File(dir, "N-Zik_log.txt")))
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

            notificationManager.createNotificationChannels(listOf(playerChannel, sleepTimerChannel, downloadChannel, syncChannel))
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


