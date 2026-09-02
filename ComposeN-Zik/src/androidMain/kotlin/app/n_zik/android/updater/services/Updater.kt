package app.n_zik.android.updater.services

import app.n_zik.android.updater.models.GithubRelease
import app.n_zik.android.updater.models.UpdaterConstants
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.enums.CheckUpdateState
import app.it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import app.it.fast4x.rimusic.ui.screens.settings.EnumValueSelectorSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.updateCancelledKey
import app.it.fast4x.rimusic.utils.lastUpdateCheckKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import app.kreate.android.me.knighthat.utils.Repository
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.core.network.client.NetworkClientFactory
import okhttp3.Request
import timber.log.Timber
import java.net.UnknownHostException
import java.nio.file.NoSuchFileException
import kotlin.math.pow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.n_zik.android.components.dialog.updater.NewUpdateAvailableDialog

object Updater {
    var isCheckingForUpdate by mutableStateOf(false)
    var isFetchingChangelog by mutableStateOf(false)
    var latestChangelog: String? by mutableStateOf(null)
    var currentChangelog: String? by mutableStateOf(null)
    var latestVersionCode: Int? = null
    private lateinit var tagName: String
    lateinit var build: GithubRelease.Build
    var githubRelease: GithubRelease? by mutableStateOf(null)

    /**
     * Extracts the build type from version string
     * e.g., "1.0.0-f" returns "full", "1.0.0-b" returns "beta"
     */
    fun extractBuildType(versionStr: String): String {
        return when {
            versionStr.endsWith(UpdaterConstants.SUFFIX_BETA) -> UpdaterConstants.TYPE_BETA
            versionStr.endsWith(UpdaterConstants.SUFFIX_MINIFIED) -> UpdaterConstants.TYPE_MINIFIED
            versionStr.contains(UpdaterConstants.SUFFIX_DEV) -> UpdaterConstants.TYPE_DEV
            else -> UpdaterConstants.TYPE_FULL
        }
    }

    /**
     * Extracts the version suffix from version string
     * e.g., "1.0.0-f" returns "f", "1.0.0-b" returns "b"
     */
    fun extractVersionSuffix(versionStr: String): String {
        val parts = versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION).split("-")
        return if (parts.size > 1) parts[1] else ""
    }

    /**
     * Returns the string resource ID for the build type label
     * based on the selected build's APK filename.
     * e.g., "N-Zik-beta.apk" -> R.string.beta_title
     */
    fun getBuildTypeStringRes(): Int {
        if (!::build.isInitialized) return R.string.stable_title
        return when {
            build.name.contains(UpdaterConstants.TYPE_BETA, ignoreCase = true) -> R.string.beta_title
            build.name.contains(UpdaterConstants.TYPE_MINIFIED, ignoreCase = true) -> R.string.minified_title
            build.name.contains(UpdaterConstants.TYPE_DEV, ignoreCase = true) -> R.string.dev_title
            else -> R.string.stable_title
        }
    }

    /**
     * Returns the version suffix (-b, -m, -f) based on the selected build.
     */
    fun getBuildSuffix(): String {
        if (!::build.isInitialized) return UpdaterConstants.SUFFIX_FULL
        return when {
            build.name.contains(UpdaterConstants.TYPE_BETA, ignoreCase = true) -> UpdaterConstants.SUFFIX_BETA
            build.name.contains(UpdaterConstants.TYPE_MINIFIED, ignoreCase = true) -> UpdaterConstants.SUFFIX_MINIFIED
            build.name.contains(UpdaterConstants.TYPE_DEV, ignoreCase = true) -> UpdaterConstants.SUFFIX_DEV
            else -> UpdaterConstants.SUFFIX_FULL
        }
    }

    /**
     * Returns the display version with suffix, avoiding duplication.
     * e.g., tagName="v4.1.3-b" returns "v4.1.3-b" (not "v4.1.3-b-b")
     * e.g., tagName="v4.1.3" returns "v4.1.3-b" (suffix added)
     */
    fun getDisplayVersion(): String {
        val tag = githubRelease?.tagName ?: return BuildConfig.VERSION_NAME
        val suffix = getBuildSuffix()
        // Avoid duplicating the suffix if tagName already contains it
        return if (tag.endsWith(suffix)) tag else "$tag$suffix"
    }

    private fun extractBuild(assets: List<GithubRelease.Build>, checkBetaUpdates: Boolean = false): GithubRelease.Build {
        val appName = BuildConfig.APP_NAME
        val currentBuildType = extractBuildType(BuildConfig.VERSION_NAME)
        val currentSuffix = extractVersionSuffix(BuildConfig.VERSION_NAME)

        // Determine which build types to look for based on current build and beta preferences
        val targetBuildTypes = when {
            // Dev users: check only dev
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_DEV -> listOf(UpdaterConstants.TYPE_DEV)
            // Full users with beta enabled: check beta and full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL && checkBetaUpdates -> listOf(UpdaterConstants.TYPE_BETA, UpdaterConstants.TYPE_FULL)
            // Full users with beta disabled: check only full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL && !checkBetaUpdates -> listOf(UpdaterConstants.TYPE_FULL)
            // Beta users with beta enabled: check beta and full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA && checkBetaUpdates -> listOf(UpdaterConstants.TYPE_BETA, UpdaterConstants.TYPE_FULL)
            // Beta users with beta disabled: check only full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA && !checkBetaUpdates -> listOf(UpdaterConstants.TYPE_FULL)
            // Minified users: check only minified
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED -> listOf(UpdaterConstants.TYPE_MINIFIED)
            // Default: empty list to trigger fallback error
            else -> emptyList()
        }

        // Try to find the best matching build
        for (buildType in targetBuildTypes) {
            val fileName = "$appName-$buildType.apk"
            val foundBuild = assets.fastFirstOrNull { it.name == fileName }
            if (foundBuild != null) {
                return foundBuild
            }
        }

        // Fallback to the original logic
        val fileName = "$appName-$currentBuildType.apk"
        val fallbackBuild = assets.fastFirstOrNull {
            it.name == fileName
        }
        
        if (fallbackBuild != null) {
            return fallbackBuild
        } else {
            throw NoSuchFileException("")
        }
    }

    /**
     * Compares two version strings and returns true if version1 is newer than version2
     */
    fun isVersionNewer(version1: String, version2: String): Boolean {
        val v1 = version1.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
        val v2 = version2.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
        
        val v1Parts = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val v1Part = v1Parts.getOrNull(i) ?: 0
            val v2Part = v2Parts.getOrNull(i) ?: 0
            
            when {
                v1Part > v2Part -> return true
                v1Part < v2Part -> return false
            }
        }
        
        // Base versions are equal — compare date suffix for dev builds
        // Dev tag format: "v7.3.2-dev-20260806", dev versionName: "7.3.2-dev"
        val date1 = version1.removePrefix(UpdaterConstants.PREFIX_VERSION)
            .split("-").getOrNull(2)?.toIntOrNull() ?: 0
        val date2 = version2.removePrefix(UpdaterConstants.PREFIX_VERSION)
            .split("-").getOrNull(2)?.toIntOrNull() ?: 0
        
        return date1 > date2
    }

    /**
     * Turns `v1.0.0` to `1.0.0`, `1.0.0-m` to `1.0.0`
     */
    private fun trimVersion(versionStr: String): String {
        return versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
    }

    /**
     * Sends out requests to Github for latest version.
     *
     * Results are downloaded, filtered, and saved to [build]
     *
     * > **NOTE**: This is a blocking process, it should never run on UI thread
     */
    private suspend fun fetchUpdate(checkBetaUpdates: Boolean = false) = withContext(Dispatchers.IO) {
        assert(Looper.myLooper() != Looper.getMainLooper()) {
            "Cannot run fetch update on main thread"
        }

        // Get all releases to find the best one
        val url = "${Repository.GITHUB_API}/repos/${Repository.REPO}/releases"
        val request = Request.Builder().url(url).build()
        val response = NetworkClientFactory.getClient().newCall(request).execute()

        if (!response.isSuccessful) {
            Toaster.e(response.message)
            return@withContext
        }

        val resBody = response.body?.string()
        if (resBody.isNullOrBlank()) {
            Toaster.i(R.string.info_no_update_available)
            return@withContext
        }

        val json = Json {
            ignoreUnknownKeys = true
        }
        val releases = json.decodeFromString<List<GithubRelease>>(resBody)
        
        // Find the best release based on current version and beta preferences
        val bestRelease = findBestRelease(releases, checkBetaUpdates)
        
        if (bestRelease != null) {
            this@Updater.githubRelease = bestRelease
            build = extractBuild(bestRelease.builds, checkBetaUpdates)
            tagName = bestRelease.tagName

            try {
                val isDevRelease = bestRelease.tagName.contains(UpdaterConstants.SUFFIX_CHAR_DEV)
                
                if (isDevRelease) {
                    // Dev builds use a single generic changelog file
                    isFetchingChangelog = true
                    val downloadUrl = "${Repository.CHANGELOGS_URL}/dev.txt"
                    val txtReq = Request.Builder().url(downloadUrl).build()
                    val txtRes = NetworkClientFactory.getClient().newCall(txtReq).execute()
                    if (txtRes.isSuccessful) {
                        latestChangelog = txtRes.body?.string()
                    }
                } else {
                    // Stable/beta: find the exact version code linked in the release body
                    val regex = """${Repository.CHANGELOGS_PATH}/(\d+)\.txt""".toRegex()
                    val match = regex.find(bestRelease.body)
                    
                    if (match != null) {
                        isFetchingChangelog = true
                        val versionCodeStr = match.groupValues[1]
                        latestVersionCode = versionCodeStr.toIntOrNull()
                        val downloadUrl = "${Repository.CHANGELOGS_URL}/$versionCodeStr.txt"
                        
                        val txtReq = Request.Builder().url(downloadUrl).build()
                        val txtRes = NetworkClientFactory.getClient().newCall(txtReq).execute()
                        if (txtRes.isSuccessful) {
                            latestChangelog = txtRes.body?.string()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("Updater").e(e, "Error fetching changelog")
            } finally {
                isFetchingChangelog = false
            }
        } else {
            throw NoSuchFileException("")
        }
    }

    private const val CHANGELOG_CACHE_KEY = "cached_changelog"
    private const val CHANGELOG_VERSION_KEY = "cached_changelog_version"

    fun fetchCurrentChangelog() = CoroutineScope(Dispatchers.IO).launch {
        try {
            isFetchingChangelog = true
            val versionCode = BuildConfig.VERSION_CODE
            val isDev = extractVersionSuffix(BuildConfig.VERSION_NAME) == UpdaterConstants.SUFFIX_CHAR_DEV
            val downloadUrl = if (isDev) {
                "${Repository.CHANGELOGS_URL}/dev.txt"
            } else {
                "${Repository.CHANGELOGS_URL}/$versionCode.txt"
            }
            
            val txtReq = Request.Builder().url(downloadUrl).build()
            val txtRes = NetworkClientFactory.getClient().newCall(txtReq).execute()
            if (txtRes.isSuccessful) {
                val fetchedChangelog = txtRes.body?.string()
                if (!fetchedChangelog.isNullOrBlank()) {
                    currentChangelog = fetchedChangelog
                    // Cache the changelog locally
                    appContext().preferences.edit()
                        .putString(CHANGELOG_CACHE_KEY, fetchedChangelog)
                        .putInt(CHANGELOG_VERSION_KEY, versionCode)
                        .apply()
                }
            }
        } catch (e: Exception) {
            Timber.tag("Updater").e(e, "Error fetching current changelog")
            // If network fails, try to load from cache
            if (currentChangelog.isNullOrBlank()) {
                loadCachedChangelog()
            }
        } finally {
            isFetchingChangelog = false
        }
    }

    /**
     * Loads changelog from local cache if available
     */
    fun loadCachedChangelog() {
        val prefs = appContext().preferences
        val cachedVersion = prefs.getInt(CHANGELOG_VERSION_KEY, -1)
        val cachedChangelog = prefs.getString(CHANGELOG_CACHE_KEY, null)
        
        if (cachedVersion == BuildConfig.VERSION_CODE && !cachedChangelog.isNullOrBlank()) {
            currentChangelog = cachedChangelog
        }
    }

    /**
     * Finds the best release based on current version and beta preferences
     */
    private fun findBestRelease(releases: List<GithubRelease>, checkBetaUpdates: Boolean): GithubRelease? {
        val currentVersion = BuildConfig.VERSION_NAME
        val currentSuffix = extractVersionSuffix(currentVersion)
        
        // Filter releases based on current build type and beta preferences
        val eligibleReleases = releases.filter { release ->
            val releaseSuffix = extractVersionSuffix(release.tagName)
            
            when {
                // Dev users: only accept dev releases
                currentSuffix == UpdaterConstants.SUFFIX_CHAR_DEV -> releaseSuffix.startsWith(UpdaterConstants.SUFFIX_CHAR_DEV)
                // Full users with beta enabled: accept both beta and full
                currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL && checkBetaUpdates -> releaseSuffix == "" || releaseSuffix == UpdaterConstants.SUFFIX_BETA.removePrefix("-")
                // Full users with beta disabled: only accept full
                currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL && !checkBetaUpdates -> releaseSuffix == ""
                // Beta users with beta enabled: accept both beta and full
                currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA && checkBetaUpdates -> releaseSuffix == UpdaterConstants.SUFFIX_BETA.removePrefix("-") || releaseSuffix == ""
                // Beta users with beta disabled: only accept full
                currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA && !checkBetaUpdates -> releaseSuffix == ""
                // Minified users: only accept minified
                currentSuffix == UpdaterConstants.SUFFIX_MINIFIED.removePrefix("-") -> releaseSuffix == ""
                // Default case: only accept full releases
                else -> releaseSuffix == ""
            }
        }
        
        if (eligibleReleases.isEmpty()) return null
        
        // Find the release with the highest version number
        // Find the maximum number of version parts to normalize all versions
        val maxParts = eligibleReleases.maxOf { release ->
            val version = release.tagName.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
            version.split(".").size
        }
        
        val bestRelease = eligibleReleases.maxByOrNull { release ->
            val version = release.tagName.removePrefix(UpdaterConstants.PREFIX_VERSION).substringBefore("-")
            val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
            
            // Pad the parts array to have the same length as maxParts
            val normalizedParts = parts.toMutableList()
            while (normalizedParts.size < maxParts) {
                normalizedParts.add(0)
            }
            
            // Base comparable version number (e.g., 1.2.3 -> 1002003)
            val baseScore = normalizedParts.foldIndexed(0L) { index, acc, part ->
                acc + (part * (1000.0.pow(maxParts - 1 - index)).toLong())
            }
            
            // For dev releases, add the date as a fractional bonus
            // Dev tag format: "v7.3.2-dev-20260806"
            val dateBonus = if (currentSuffix == UpdaterConstants.SUFFIX_CHAR_DEV) {
                val dateStr = release.tagName.split("-").lastOrNull()?.toLongOrNull() ?: 0L
                dateStr  // e.g., 20260806 — enough to排序 by date
            } else {
                0L
            }
            
            baseScore * 100_000_000L + dateBonus
        }
        
        return bestRelease
    }

    fun checkForUpdate(
        isForced: Boolean = false,
        checkBetaUpdates: Boolean = false,
        showDialog: Boolean = true
    ) = CoroutineScope(Dispatchers.IO).launch {
        // Update the last check timestamp at the beginning
        appContext().preferences.edit()
            .putLong(lastUpdateCheckKey, System.currentTimeMillis())
            .apply()
            
        if (!isForced && (!BuildConfig.IS_AUTOUPDATE || NewUpdateAvailableDialog.isCancelled)) return@launch

        try {
            if (!::build.isInitialized || isForced) {
                fetchUpdate(checkBetaUpdates)
            }

            // Check if the new version is actually newer
            val hasUpdate = if (::tagName.isInitialized) {
                isVersionNewer(tagName, BuildConfig.VERSION_NAME)
            } else {
                false
            }
            
            if (showDialog) {
                NewUpdateAvailableDialog.isActive = hasUpdate
            }
            
            if (!hasUpdate) {
                if (isForced) {
                    Toaster.i(R.string.info_no_update_available)
                }
                NewUpdateAvailableDialog.isCancelled = true
                // Also reset the cancelled state in SharedPreferences when no update is available
                appContext().preferences.edit()
                    .putBoolean(updateCancelledKey, false)
                    .apply()
            } else {
                if (isForced) {
                    Toaster.i(R.string.update_available)
                }
                // If there's an update available, reset the cancelled state
                NewUpdateAvailableDialog.isCancelled = false
            }
        } catch (e: Exception) {
            val message = when (e) {
                is UnknownHostException -> appContext().getString(R.string.error_no_internet)
                is NoSuchFileException -> appContext().getString(R.string.info_no_update_available)
                else -> e.message ?: appContext().getString(R.string.error_unknown)
            }
            
            // Use appropriate toast type based on exception
            when (e) {
                is NoSuchFileException -> Toaster.i(message) // Blue for no update available
                else -> Toaster.e(message) // Red for other errors
            }

            NewUpdateAvailableDialog.isCancelled = true
        }
    }

    @Composable
    fun SettingEntry() {
        var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Enabled)
        var checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, extractVersionSuffix(BuildConfig.VERSION_NAME) == UpdaterConstants.SUFFIX_CHAR_BETA)
        if (!BuildConfig.IS_AUTOUPDATE)
            checkUpdateState = CheckUpdateState.Disabled

        Row(Modifier.fillMaxWidth()) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.enable_check_for_update),
                selectedValue = checkUpdateState,
                onValueSelected = { checkUpdateState = it },
                valueText = { it.text },
                isEnabled = BuildConfig.IS_AUTOUPDATE,
                modifier = Modifier.weight(1f)
            )

            AnimatedVisibility(
                visible = checkUpdateState != CheckUpdateState.Disabled && BuildConfig.IS_AUTOUPDATE,
                // Slide in from right + fade in effect.
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(initialAlpha = 0f),
                // Slide out from left + fade out effect.
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(targetAlpha = 0f)
            ) {
                SecondaryTextButton(
                    text = stringResource(R.string.info_check_update_now),
                    onClick = { checkForUpdate(true, checkBetaUpdates) },
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }

        SettingsDescription(
            stringResource(
                if (BuildConfig.IS_AUTOUPDATE)
                    R.string.when_enabled_a_new_version_is_checked_and_notified_during_startup
                else
                    R.string.description_app_not_installed_by_apk
            )
        )
    }
}

