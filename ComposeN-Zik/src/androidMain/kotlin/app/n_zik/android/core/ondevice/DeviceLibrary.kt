package app.n_zik.android.core.ondevice

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.core.profiles.profileDataPrefs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.components.ui.screens.home.onDevice.localSongRefreshRequests
import app.n_zik.android.components.ui.screens.home.onDevice.withLocalFolderProfile
import app.n_zik.android.core.utils.readDeviceLibrary
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.isAtLeastAndroid13
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import timber.log.Timber

internal const val DEVICE_LIBRARY_ENABLED_KEY = "deviceLibraryEnabled"

internal fun Context.isDeviceLibraryEnabled(profile: String = getActiveProfile(this)): Boolean =
    profileDataPrefs(this, profile).getBoolean(DEVICE_LIBRARY_ENABLED_KEY, false)

internal fun Context.observeDeviceLibraryEnabled(profile: String) = callbackFlow {
    val prefs = profileDataPrefs(this@observeDeviceLibraryEnabled, profile)
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == DEVICE_LIBRARY_ENABLED_KEY) trySend(prefs.getBoolean(DEVICE_LIBRARY_ENABLED_KEY, false))
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    trySend(prefs.getBoolean(DEVICE_LIBRARY_ENABLED_KEY, false))
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()

@Composable
internal fun deviceLibraryEnabled(): Boolean {
    val context = LocalContext.current
    val profile = getActiveProfile(context)
    return remember(context, profile) { context.observeDeviceLibraryEnabled(profile) }
        .collectAsStateWithLifecycle(context.isDeviceLibraryEnabled(profile)).value
}

internal val deviceAudioPermission: String
    get() = if (isAtLeastAndroid13) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

internal fun Context.hasDeviceAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, deviceAudioPermission) == PackageManager.PERMISSION_GRANTED

internal const val SCAN_DEVICE_LIBRARY_ON_STARTUP_KEY = "scanDeviceLibraryOnStartup"

internal const val DEVICE_ALBUM_PREFIX = "LOCAL_ALBUM_DEVICE_"
internal const val DEVICE_ARTIST_PREFIX = "LOCAL_ARTIST_DEVICE_"

internal fun isDeviceAlbumId(id: String): Boolean = id.startsWith(DEVICE_ALBUM_PREFIX)
internal fun isDeviceArtistId(id: String): Boolean =
    id.startsWith(DEVICE_ARTIST_PREFIX) || id.startsWith(LOCAL_KEY_PREFIX)

internal data class DeviceLibrarySnapshot(
    val songIds: Set<String> = emptySet(),
    val albumSongIds: Map<String, Set<String>> = emptyMap(),
    val artistSongIds: Map<String, Set<String>> = emptyMap(),
    val folders: Set<String> = emptySet(),
) {
    val albumIds: Set<String> get() = albumSongIds.keys
    val artistIds: Set<String> get() = artistSongIds.keys
}

internal object DeviceLibraryResults {
    private val snapshots = MutableStateFlow<Map<String, DeviceLibrarySnapshot>>(emptyMap())

    fun current(profile: String): DeviceLibrarySnapshot? = snapshots.value[profile]

    fun forProfile(profile: String) = snapshots.map { it[profile] }.distinctUntilChanged()

    fun publish(profile: String, snapshot: DeviceLibrarySnapshot) {
        snapshots.update { it + (profile to snapshot) }
    }
}

internal suspend fun Context.scanDeviceLibrary(refresh: Boolean = false): DeviceLibrarySnapshot = withContext(NzikDispatchers.DATA) {
    if (!isDeviceLibraryEnabled()) return@withContext DeviceLibrarySnapshot()
    if (!hasDeviceAudioPermission()) {
        return@withContext DeviceLibrarySnapshot()
    }

    val profile = getActiveProfile(this@scanDeviceLibrary)
    try {
        if (refresh) localSongRefreshRequests.request(profile)
        withLocalFolderProfile(profile).readDeviceLibrary().snapshot
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.tag("DeviceLibrary").w(error, "Could not read on-device groups")
        DeviceLibrarySnapshot()
    }
}

internal fun Context.observeDeviceLibrary() = flow {
    val profile = getActiveProfile(this@observeDeviceLibrary)
    localSongRefreshRequests.forProfile(profile).collect {
        emit(scanDeviceLibrary())
    }
}.distinctUntilChanged()
