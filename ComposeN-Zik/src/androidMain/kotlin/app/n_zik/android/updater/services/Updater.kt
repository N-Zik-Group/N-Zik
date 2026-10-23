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
import app.n_zik.android.updater.models.CheckUpdateState
import app.it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import app.it.fast4x.rimusic.ui.screens.settings.EnumValueSelectorSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.checkBetaUpdatesKey
import app.it.fast4x.rimusic.utils.updateCancelledKey
import app.it.fast4x.rimusic.utils.lastUpdateCheckKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import app.n_zik.android.updater.models.UpdateEndpoints
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
    /**
     * The APK selected for the current user. Set together with [githubRelease]
     * in `fetchUpdate` (only after a successful asset extraction), so [build]
     * is only valid while [githubRelease] is non-null.
     */
    lateinit var build: GithubRelease.Build
    var githubRelease: GithubRelease? by mutableStateOf(null)

    /** Extracts the build type from version string e.g., "1.0.0-f" returns "full", "1.0.0-beta32" returns "beta", "1.0.0-m32" returns "minified", "1.0.0-dev32-20261013" returns "dev" */
    fun extractBuildType(versionStr: String): String {
        return when {
            versionStr.endsWith(UpdaterConstants.SUFFIX_BETA) ||
                versionStr.endsWith(UpdaterConstants.SUFFIX_BETA32) -> UpdaterConstants.TYPE_BETA
            versionStr.endsWith(UpdaterConstants.SUFFIX_MINIFIED) ||
                versionStr.endsWith(UpdaterConstants.SUFFIX_MINIFIED32) -> UpdaterConstants.TYPE_MINIFIED
            versionStr.contains(UpdaterConstants.SUFFIX_DEV) -> UpdaterConstants.TYPE_DEV
            else -> UpdaterConstants.TYPE_FULL
        }
    }

    /** Extracts the version suffix from version string e.g., "1.0.0-f" returns "f", "1.0.0-beta32" returns "beta32", "1.0.0-dev-20261013" returns "dev" */
    fun extractVersionSuffix(versionStr: String): String {
        val parts = versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION).split("-")
        return if (parts.size > 1) parts[1] else ""
    }

    /** Returns true for the dev build family — both the "dev" and "dev32" flavors, which share the same dev releases and release tags. */
    fun isDevFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix == UpdaterConstants.SUFFIX_CHAR_DEV ||
            suffix == UpdaterConstants.SUFFIX_CHAR_DEV32
    }

    /**
     * Returns true for the minified build family — "minified" and "minified32".
     */
    fun isMinifiedFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED ||
            suffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED32
    }

    /**
     * Returns true for the beta build family — "beta" and "beta32".
     */
    fun isBetaFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix == UpdaterConstants.SUFFIX_CHAR_BETA ||
            suffix == UpdaterConstants.SUFFIX_CHAR_BETA32
    }

    /**
     * Returns the GitHub release tag matching the given version string.
     *
     * Dev tags carry a dated suffix that is part of the tag itself, so the
     * full version name is kept. Dev32 builds share the dev release, so their
     * suffix is mapped back to the dev tag. Beta keeps its full tag, beta32
     * maps to the shared beta tag, other flavors use the base version.
     * e.g., "8.4.0-dev-20261013" returns "v8.4.0-dev-20261013",
     * "8.4.0-dev32-20261013" returns "v8.4.0-dev-20261013",
     * "8.4.0-dev32" (undated, not producible by the build) returns "v8.4.0-dev",
     * "8.4.0-beta32" returns "v8.4.0-beta", "8.4.0-f" returns "v8.4.0"
     */
    fun releaseTag(versionStr: String): String {
        val clean = versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION)
        return when {
            isDevFamily(clean) -> {
                // Dated dev32 shares the dated dev tag; an undated dev32 is not
                // producible (the build always appends the date) but is mapped to
//
                // the undated shared dev tag instead of the non-existent
                // "-dev32" tag. Any other dev version is kept as-is.
                val tag = when {
                    clean.contains(UpdaterConstants.SUFFIX_DEV32 + "-") ->
                        clean.replaceFirst(UpdaterConstants.SUFFIX_DEV32 + "-", UpdaterConstants.SUFFIX_DEV + "-")
                    clean.endsWith(UpdaterConstants.SUFFIX_DEV32) -> clean.removeSuffix("32")
                    else -> clean
                }
                UpdaterConstants.PREFIX_VERSION + tag
            }
            clean.endsWith(UpdaterConstants.SUFFIX_BETA32) -> UpdaterConstants.PREFIX_VERSION +
                clean.removeSuffix("32")
            clean.endsWith(UpdaterConstants.SUFFIX_BETA) -> UpdaterConstants.PREFIX_VERSION + clean
            else -> UpdaterConstants.PREFIX_VERSION + clean.substringBefore("-")
        }
    }

    /**
     * Returns the APK asset name hosted on the GitHub release matching the
     * given version, e.g. "N-Zik-dev32.apk" for the dev32 flavor or
     * "N-Zik-full32.apk" for the full32 flavor.
     */
    fun assetName(appName: String, versionStr: String): String {
        val build = when (extractVersionSuffix(versionStr)) {
            UpdaterConstants.SUFFIX_CHAR_DEV32 -> UpdaterConstants.TYPE_DEV32
            UpdaterConstants.SUFFIX_CHAR_FULL32 -> UpdaterConstants.TYPE_FULL32
            UpdaterConstants.SUFFIX_CHAR_MINIFIED32 -> UpdaterConstants.TYPE_MINIFIED32
            UpdaterConstants.SUFFIX_CHAR_BETA32 -> UpdaterConstants.TYPE_BETA32
            else -> extractBuildType(versionStr)
        }
        return "$appName-$build.apk"
    }

    /**
     * The flavor label shared by the header badge, the About badge, the
     * updater screen and the new-update popup — one source of truth so the
     * same build reads the same everywhere (FULL / FULL 32, MINIFIED /
     * MINIFIED 32, BETA / BETA 32, DEV / DEV 32, plus FOSS and DEBUG).
     *
     * [resId] carries the localized label (0 when [literal] is used),
     * [append32] asks the caller to append the " 32" marker, and [literal]
     * is a fixed text ("FOSS", or an unknown suffix uppercased).
     */
    data class FlavorLabel(
        val resId: Int = 0,
        val append32: Boolean = false,
        val literal: String? = null
    )

    /**
     * Returns the flavor label for the given version name, or null when the
     * version carries no suffix (badges stay hidden, label consumers fall
     * back to the full label).
     *
     * The foss build type always yields the fixed "FOSS" text. Both version
     * names ("8.4.0-f32") and display versions ("v8.4.0-f32", the input the
     * updater screen and popup derive) work.
     */
    fun flavorLabel(versionName: String, isFoss: Boolean = false): FlavorLabel? {
        if (isFoss) return FlavorLabel(literal = "FOSS")
        val suffix = extractVersionSuffix(versionName)
        if (suffix.isEmpty()) return null
        return when (suffix) {
            UpdaterConstants.SUFFIX_CHAR_FULL -> FlavorLabel(R.string.full_title)
            UpdaterConstants.SUFFIX_CHAR_MINIFIED -> FlavorLabel(R.string.minified_title)
            UpdaterConstants.SUFFIX_CHAR_DEV -> FlavorLabel(R.string.dev_title)
            UpdaterConstants.SUFFIX_CHAR_BETA -> FlavorLabel(R.string.beta_title)
            UpdaterConstants.SUFFIX_CHAR_FULL32 -> FlavorLabel(R.string.full_title, append32 = true)
            UpdaterConstants.SUFFIX_CHAR_MINIFIED32 -> FlavorLabel(R.string.minified_title, append32 = true)
            UpdaterConstants.SUFFIX_CHAR_BETA32 -> FlavorLabel(R.string.beta_title, append32 = true)
            UpdaterConstants.SUFFIX_CHAR_DEV32 -> FlavorLabel(R.string.dev_title, append32 = true)
            "debug" -> FlavorLabel(R.string.debug_title)
            else -> FlavorLabel(literal = suffix.uppercase())
        }
    }

    /**
     * Returns the GitHub download URL of the APK matching the given version
     * (release tag + asset name).
     *
     * [UpdateEndpoints.RELEASE_DOWNLOAD_URL] already carries the "v" prefix, so the
     * tag is appended without it.
     */
    fun redownloadUrl(appName: String, versionStr: String): String {
        val tag = releaseTag(versionStr).removePrefix(UpdaterConstants.PREFIX_VERSION)
        return "${UpdateEndpoints.RELEASE_DOWNLOAD_URL}$tag/${assetName(appName, versionStr)}"
    }

    /** Returns the changelog URL for the given version: dev-family builds (dev + dev32) share a single "dev.txt", every other flavor uses its version code. */
    fun currentChangelogUrl(versionCode: Int, versionStr: String): String {
        return if (isDevFamily(versionStr)) {
            "${UpdateEndpoints.CHANGELOGS_URL}/dev.txt"
        } else {
            "${UpdateEndpoints.CHANGELOGS_URL}/$versionCode.txt"
        }
    }

    /** Returns the version suffix (-beta/-beta32, -m/-m32, -f/-f32) based on the selected build, keeping the 32-bit marker when the APK is 32-bit. */
    fun getBuildSuffix(): String {
        if (!::build.isInitialized) return UpdaterConstants.SUFFIX_FULL
        // Anchor on the extension: asset names are "…-<type>32.apk", a bare
        // "32" substring could otherwise match unrelated future names
        val is32Bit = build.name.endsWith("32.apk", ignoreCase = true)
        return when {
            build.name.contains(UpdaterConstants.TYPE_BETA, ignoreCase = true) ->
                if (is32Bit) UpdaterConstants.SUFFIX_BETA32 else UpdaterConstants.SUFFIX_BETA
            build.name.contains(UpdaterConstants.TYPE_MINIFIED, ignoreCase = true) ->
                if (is32Bit) UpdaterConstants.SUFFIX_MINIFIED32 else UpdaterConstants.SUFFIX_MINIFIED
            build.name.contains(UpdaterConstants.TYPE_DEV, ignoreCase = true) ->
                if (is32Bit) UpdaterConstants.SUFFIX_DEV32 else UpdaterConstants.SUFFIX_DEV
            else -> if (is32Bit) UpdaterConstants.SUFFIX_FULL32 else UpdaterConstants.SUFFIX_FULL
        }
    }

    /**
     * Returns the display version with suffix, avoiding duplication.
     * Release tags carry the base 64-bit suffix ("-beta", "-dev") but never
     * the 32-bit marker, so the base suffix is swapped for the 32-bit one
     * instead of appended (appending would duplicate it).
     * e.g., tagName="v8.4.0-beta", build=N-Zik-beta32.apk returns "v8.4.0-beta32"
     * e.g., tagName="v8.4.0-dev-20261013", build=N-Zik-dev32.apk returns "v8.4.0-dev32-20261013"
     * e.g., tagName="v8.4.0" returns "v8.4.0-f" (suffix added)
     */
    fun getDisplayVersion(): String {
        val tag = githubRelease?.tagName ?: return BuildConfig.VERSION_NAME
        val suffix = getBuildSuffix()
        val baseSuffix = when (suffix) {
            UpdaterConstants.SUFFIX_BETA32 -> UpdaterConstants.SUFFIX_BETA
            UpdaterConstants.SUFFIX_MINIFIED32 -> UpdaterConstants.SUFFIX_MINIFIED
            UpdaterConstants.SUFFIX_FULL32 -> UpdaterConstants.SUFFIX_FULL
            UpdaterConstants.SUFFIX_DEV32 -> UpdaterConstants.SUFFIX_DEV
            else -> suffix
        }
        return when {
            // Tag already carries the exact suffix
            tag.contains(suffix) -> tag
            // 32-bit flavor: the tag carries the base suffix -> swap in the 32 marker
            baseSuffix != suffix && tag.contains(baseSuffix) -> tag.replaceFirst(baseSuffix, suffix)
            // Plain tag (e.g. stable "v8.4.0") -> append the suffix
            else -> "$tag$suffix"
        }
    }

    /**
     * The APK build types a user on [currentVersion] can install, in
     * priority order, given their beta preference.
     *
     * Shared by [extractBuild] (asset selection) and [findBestRelease]
     * (releases must ship at least one of these assets). Empty for unknown
     * flavors — [extractBuild] then falls back to the base build type.
     * Visible for testing (see `UpdaterReleaseTagTest`).
     */
    internal fun targetBuildTypes(
        currentVersion: String,
        checkBetaUpdates: Boolean
    ): List<String> {
        val currentSuffix = extractVersionSuffix(currentVersion)
        return when {
            // Dev users: check only dev
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_DEV -> listOf(UpdaterConstants.TYPE_DEV)
            // Dev32 users: same dev releases, dev32 asset
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_DEV32 -> listOf(UpdaterConstants.TYPE_DEV32)
            // Full users with beta enabled: check beta and full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL && checkBetaUpdates -> listOf(UpdaterConstants.TYPE_BETA, UpdaterConstants.TYPE_FULL)
            // Full users with beta disabled: check only full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL && !checkBetaUpdates -> listOf(UpdaterConstants.TYPE_FULL)
            // Full32 users: same channels, 32-bit assets
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL32 && checkBetaUpdates -> listOf(UpdaterConstants.TYPE_BETA32, UpdaterConstants.TYPE_FULL32)
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL32 && !checkBetaUpdates -> listOf(UpdaterConstants.TYPE_FULL32)
            // Beta users with beta enabled: check beta and full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA && checkBetaUpdates -> listOf(UpdaterConstants.TYPE_BETA, UpdaterConstants.TYPE_FULL)
            // Beta users with beta disabled: check only full
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA && !checkBetaUpdates -> listOf(UpdaterConstants.TYPE_FULL)
            // Beta32 users: same channels, 32-bit assets
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA32 && checkBetaUpdates -> listOf(UpdaterConstants.TYPE_BETA32, UpdaterConstants.TYPE_FULL32)
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_BETA32 && !checkBetaUpdates -> listOf(UpdaterConstants.TYPE_FULL32)
            // Minified users: check only minified
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED -> listOf(UpdaterConstants.TYPE_MINIFIED)
            // Minified32 users: same channel, 32-bit asset
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED32 -> listOf(UpdaterConstants.TYPE_MINIFIED32)
            // Default: unknown flavor — extractBuild falls back to the base type
            else -> emptyList()
        }
    }

    /** Visible for testing: selects the APK build matching the current flavor from a release's assets (see `UpdaterReleaseTagTest`). */
    internal fun extractBuild(
        assets: List<GithubRelease.Build>,
        checkBetaUpdates: Boolean = false,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): GithubRelease.Build {
        val appName = BuildConfig.APP_NAME
        val currentBuildType = extractBuildType(currentVersion)
        val currentSuffix = extractVersionSuffix(currentVersion)

        // Determine which build types to look for based on current build and beta preferences
        val targetBuildTypes = targetBuildTypes(currentVersion, checkBetaUpdates)

        // Try to find the best matching build
        for (buildType in targetBuildTypes) {
            val fileName = "$appName-$buildType.apk"
            val foundBuild = assets.fastFirstOrNull { it.name == fileName }
            if (foundBuild != null) {
                return foundBuild
            }
        }

        // Fallback to the original logic — 32-aware, so a 32-bit user is
        // never pointed at a 64-bit APK (the base type name would be
//
        // un-installable on a 32-bit-only device)
        val fallbackType = when (currentSuffix) {
            UpdaterConstants.SUFFIX_CHAR_FULL32 -> UpdaterConstants.TYPE_FULL32
            UpdaterConstants.SUFFIX_CHAR_MINIFIED32 -> UpdaterConstants.TYPE_MINIFIED32
            UpdaterConstants.SUFFIX_CHAR_BETA32 -> UpdaterConstants.TYPE_BETA32
            UpdaterConstants.SUFFIX_CHAR_DEV32 -> UpdaterConstants.TYPE_DEV32
            else -> currentBuildType
        }
        val fileName = "$appName-$fallbackType.apk"
        val fallbackBuild = assets.fastFirstOrNull {
            it.name == fileName
        }
        
        if (fallbackBuild != null) {
            Timber.tag("Updater").w("No targeted build found, falling back to $fileName")
            return fallbackBuild
        } else {
            Timber.tag("Updater").w("No matching build found (tried target list + $fileName)")
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
    private suspend fun fetchUpdate(checkBetaUpdates: Boolean = false) = withContext(NzikDispatchers.DATA) {
        assert(Looper.myLooper() != Looper.getMainLooper()) {
            "Cannot run fetch update on main thread"
        }

        // Clear any previously found release so a failed fetch or a no-update
        // result never leaves a stale update visible to the UI
        this@Updater.githubRelease = null
        // Same for the previous release's changelog — a new release without a
        // changelog link must not keep showing the old one
        latestChangelog = null

        // Get all releases to find the best one
        val url = UpdateEndpoints.RELEASES_API_URL
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
            // Only publish the release once its APK resolved successfully, so a
            // failed extraction (e.g. a 32-bit user whose best release ships no
//
            // 32-bit asset) reports "no update" instead of a stale or
            // undeliverable one
            build = extractBuild(bestRelease.builds, checkBetaUpdates)
            this@Updater.githubRelease = bestRelease

            try {
                val isDevRelease = isDevFamily(bestRelease.tagName)
                
                if (isDevRelease) {
                    // Dev builds use a single generic changelog file (the
                    // version code is irrelevant for the dev family)
                    isFetchingChangelog = true
                    val downloadUrl = currentChangelogUrl(BuildConfig.VERSION_CODE, bestRelease.tagName)
                    val txtReq = Request.Builder().url(downloadUrl).build()
                    val txtRes = NetworkClientFactory.getClient().newCall(txtReq).execute()
                    if (txtRes.isSuccessful) {
                        latestChangelog = txtRes.body?.string()
                    }
                } else {
                    // Stable/beta: find the exact version code linked in the release body
                    val regex = """${UpdateEndpoints.CHANGELOGS_PATH}/(\d+)\.txt""".toRegex()
                    val match = regex.find(bestRelease.body)
                    
                    if (match != null) {
                        isFetchingChangelog = true
                        val versionCodeStr = match.groupValues[1]
                        val downloadUrl = "${UpdateEndpoints.CHANGELOGS_URL}/$versionCodeStr.txt"
                        
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

    fun fetchCurrentChangelog() = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
        try {
            isFetchingChangelog = true
            val downloadUrl = currentChangelogUrl(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
            
            val txtReq = Request.Builder().url(downloadUrl).build()
            val txtRes = NetworkClientFactory.getClient().newCall(txtReq).execute()
            if (txtRes.isSuccessful) {
                val fetchedChangelog = txtRes.body?.string()
                if (!fetchedChangelog.isNullOrBlank()) {
                    currentChangelog = fetchedChangelog
                    // Cache the changelog locally
                    appContext().preferences.edit()
                        .putString(CHANGELOG_CACHE_KEY, fetchedChangelog)
                        .putInt(CHANGELOG_VERSION_KEY, BuildConfig.VERSION_CODE)
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
    // Visible for testing (see `UpdaterReleaseTagTest`)
    internal fun findBestRelease(
        releases: List<GithubRelease>,
        checkBetaUpdates: Boolean,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): GithubRelease? {
        val currentSuffix = extractVersionSuffix(currentVersion)

        // Surface flavors the matching branches below do not know about
        // (e.g. debug), instead of silently defaulting to full releases
        val knownFlavor = currentSuffix.isEmpty() ||
            isDevFamily(currentVersion) ||
            isMinifiedFamily(currentVersion) ||
            isBetaFamily(currentVersion) ||
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL ||
            currentSuffix == UpdaterConstants.SUFFIX_CHAR_FULL32
        if (!knownFlavor) {
            Timber.tag("Updater").w("Unknown version suffix '$currentSuffix', defaulting to full releases")
        }

        // Assets this user can actually install (empty for unknown flavors —
        // the legacy release matching is kept for those)
        val installableAssets = targetBuildTypes(currentVersion, checkBetaUpdates)
            .map { "${BuildConfig.APP_NAME}-$it.apk" }

        // Filter releases based on current build type and beta preferences
        val eligibleReleases = releases.filter { release ->
            val releaseSuffix = extractVersionSuffix(release.tagName)

            val channelOk = when {
                // Dev users (dev + dev32): only accept dev releases
                isDevFamily(currentVersion) -> releaseSuffix.startsWith(UpdaterConstants.SUFFIX_CHAR_DEV)
                // Full users (full + full32) with beta enabled: accept both beta and full
                currentSuffix in listOf(UpdaterConstants.SUFFIX_CHAR_FULL, UpdaterConstants.SUFFIX_CHAR_FULL32) && checkBetaUpdates -> releaseSuffix == "" || releaseSuffix == UpdaterConstants.SUFFIX_BETA.removePrefix("-")
                // Full users (full + full32) with beta disabled: only accept full
                currentSuffix in listOf(UpdaterConstants.SUFFIX_CHAR_FULL, UpdaterConstants.SUFFIX_CHAR_FULL32) && !checkBetaUpdates -> releaseSuffix == ""
                // Beta users (beta + beta32) with beta enabled: accept both beta and full
                isBetaFamily(currentVersion) && checkBetaUpdates -> releaseSuffix == UpdaterConstants.SUFFIX_BETA.removePrefix("-") || releaseSuffix == ""
                // Beta users (beta + beta32) with beta disabled: only accept full
                isBetaFamily(currentVersion) && !checkBetaUpdates -> releaseSuffix == ""
                // Minified users (minified + minified32): only accept full releases (minified assets ship in full releases)
                isMinifiedFamily(currentVersion) -> releaseSuffix == ""
                // Default case: only accept full releases
                else -> releaseSuffix == ""
            }

            // Skip releases that ship none of the user's installable assets
            // (e.g. a 32-bit user facing a beta release published without the
//
            // matching 32-bit asset, or a docs-only release) — the user then
            // falls back to the best release that does ship one. Unknown
//
            // flavors have no installable list, so the legacy behavior (any
            // release passes) is kept for them.
            val shipsInstallableAsset = installableAssets.isEmpty() ||
                release.builds.any { it.name in installableAssets }

            channelOk && shipsInstallableAsset
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
            val dateBonus = if (isDevFamily(currentVersion)) {
                val dateStr = release.tagName.split("-").lastOrNull()?.toLongOrNull() ?: 0L
                dateStr  // e.g., 20260806 — enough to sort by date
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
    ) = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
        // Update the last check timestamp at the beginning
        appContext().preferences.edit()
            .putLong(lastUpdateCheckKey, System.currentTimeMillis())
            .apply()
            
        if (!isForced && (!BuildConfig.IS_AUTOUPDATE || NewUpdateAvailableDialog.isCancelled)) return@launch

        try {
            if (!::build.isInitialized || isForced) {
                fetchUpdate(checkBetaUpdates)
            }

            // Check if the new version is actually newer (githubRelease is only
            // set once a release AND a downloadable APK were found)
            val hasUpdate = githubRelease?.let {
                isVersionNewer(it.tagName, BuildConfig.VERSION_NAME)
            } ?: false
            
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
        var checkBetaUpdates by rememberPreference(checkBetaUpdatesKey, isBetaFamily(BuildConfig.VERSION_NAME))
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

