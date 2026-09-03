package app.n_zik.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Dependencies.init(this)
        migrateCredentialsToEncrypted()
        InnerTubeXPlayer.initialize(this)

        // Prewarm InnerTubeX in background to reduce first-play latency
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1500)
            runCatching { InnerTubeXPlayer.prewarm() }
                .onFailure { Timber.tag("MainApplication").w(it, "InnerTubeX prewarm skipped") }
        }

        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            var proxy: Proxy? = null
            if (preferences.getBoolean(isProxyEnabledKey, false)) {
                val hostName = preferences.getString(proxyHostnameKey, null)
                val proxyPort = preferences.getInt(proxyPortKey, 8080)
                val proxyMode = preferences.getEnum(proxyModeKey, Proxy.Type.HTTP)
                if (isValidIP(hostName)) {
                    hostName?.let { hName ->
                        ProxyPreferences.preference = ProxyPreferenceItem(hName, proxyPort, proxyMode)
                        proxy = ProxyPreferences.preference?.let { pref -> it.fast4x.innertube.utils.getProxy(pref) }
                    }
                } else {
                    Timber.w("Proxy preference is null or invalid, running without proxy")
                }
            } else {
                Timber.w("Proxy preference is null, running without proxy")
            }
            NetworkClientFactory.configure(
                proxy = proxy,
                cacheDir = externalCacheDir ?: cacheDir
            )
            Innertube.proxy = proxy
            
            // Initialize YouTube session identifiers from Datastore
            val savedCookie = encryptedPreferences.getString(ytCookieKey, "")
            if (!savedCookie.isNullOrBlank()) {
                Innertube.cookie = savedCookie
                Innertube.visitorData = encryptedPreferences.getString(ytVisitorDataKey, "") ?: ""
                Innertube.dataSyncId = encryptedPreferences.getString(ytDataSyncIdKey, "")

                // Validate cookie on startup — detect expired/invalid session
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

        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
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


