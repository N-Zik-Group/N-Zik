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
import app.n_zik.android.components.settings.common.EnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
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

    /** Extracts the APK build type name (asset-name base) from the version string: "8.4.5-r64" returns "release64", "8.4.5-beta32" returns "beta32", "8.4.5-m32" returns "minified32", "8.4.5-dev64-20261013" returns "dev64"; unknown suffixes default to "release64" */
    fun extractBuildType(versionStr: String): String {
        return when (extractVersionSuffix(versionStr)) {
            UpdaterConstants.SUFFIX_CHAR_RELEASE64 -> UpdaterConstants.TYPE_RELEASE64
            UpdaterConstants.SUFFIX_CHAR_RELEASE32 -> UpdaterConstants.TYPE_RELEASE32
            UpdaterConstants.SUFFIX_CHAR_MINIFIED64 -> UpdaterConstants.TYPE_MINIFIED64
            UpdaterConstants.SUFFIX_CHAR_MINIFIED32 -> UpdaterConstants.TYPE_MINIFIED32
            UpdaterConstants.SUFFIX_CHAR_BETA64 -> UpdaterConstants.TYPE_BETA64
            UpdaterConstants.SUFFIX_CHAR_BETA32 -> UpdaterConstants.TYPE_BETA32
            UpdaterConstants.SUFFIX_CHAR_DEV64 -> UpdaterConstants.TYPE_DEV64
            UpdaterConstants.SUFFIX_CHAR_DEV32 -> UpdaterConstants.TYPE_DEV32
            else -> UpdaterConstants.TYPE_RELEASE64
        }
    }

    /** Extracts the version suffix from the version string: "8.4.5-r64" returns "r64", "8.4.5-beta32" returns "beta32", "8.4.5-dev64-20261013" returns "dev64" */
    fun extractVersionSuffix(versionStr: String): String {
        val parts = versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION).split("-")
        return if (parts.size > 1) parts[1] else ""
    }

    /** Returns true for the dev build family — the "dev64"/"dev32" version-name suffixes plus the shared dated release tag's "dev" suffix (tags keep the pre-rename form and are parsed through the same helpers). */
    fun isDevFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix == UpdaterConstants.SUFFIX_CHAR_DEV64 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_DEV32 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_DEV_TAG
    }

    /**
     * Returns true for the minified build family — "minified64" and "minified32".
     */
    fun isMinifiedFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED64 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED32
    }

    /**
     * Returns true for the beta build family — the "beta64"/"beta32"
     * version-name suffixes plus the beta release tag's "beta" suffix.
     */
    fun isBetaFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix == UpdaterConstants.SUFFIX_CHAR_BETA64 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_BETA32 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_BETA_TAG
    }

    /**
     * Returns true for the stable build family — the "r64"/"r32"/"m64"/"m32"
     * version-name suffixes, plus a missing suffix (a plain stable version).
     */
    fun isStableFamily(versionStr: String): Boolean {
        val suffix = extractVersionSuffix(versionStr)
        return suffix.isEmpty() ||
            suffix == UpdaterConstants.SUFFIX_CHAR_RELEASE64 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_RELEASE32 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED64 ||
            suffix == UpdaterConstants.SUFFIX_CHAR_MINIFIED32
    }

    /**
     * True when the updater knows how to serve the flavor (stable, dev or
     * beta family, or no suffix). Unrecognized suffixes are targeted at the
     * non-existent "-unknown" tag, so they end up with a clean "no update".
     */
    internal fun isKnownFlavor(versionStr: String): Boolean {
        return isStableFamily(versionStr) || isDevFamily(versionStr) || isBetaFamily(versionStr)
    }

    /**
     * Returns the GitHub release tag matching the given version string.
     *
     * Release tags are shared per channel and keep the pre-rename form:
     * stable "v8.4.5", beta "v8.4.5-beta", dev "v8.4.5-dev-<date>" — both the
     * 64-bit and the 32-bit APKs ride on the same release, so the per-ABI
     * suffix is mapped back to the shared channel tag.
     * e.g., "8.4.0-dev64-20261013" returns "v8.4.0-dev-20261013",
     * "8.4.0-dev32" (undated, not producible by the build) returns "v8.4.0-dev",
     * "8.4.0-beta64" / "8.4.0-beta32" return "v8.4.0-beta",
     * "8.4.0-r64" / "8.4.0-m32" return "v8.4.0",
     * "8.4.0-xyz" (unrecognized) returns the non-existent "v8.4.0-unknown"
     */
    fun releaseTag(versionStr: String): String {
        val clean = versionStr.removePrefix(UpdaterConstants.PREFIX_VERSION)
        return when {
            isDevFamily(clean) -> {
                // Both ABIs share the dated dev tag: the per-ABI suffix
                // ("dev64"/"dev32") maps back to the shared "dev" tag part
                val tag = clean.replaceFirst(
                    UpdaterConstants.SUFFIX_CHAR_DEV32,
                    UpdaterConstants.SUFFIX_CHAR_DEV_TAG
                ).replaceFirst(
                    UpdaterConstants.SUFFIX_CHAR_DEV64,
                    UpdaterConstants.SUFFIX_CHAR_DEV_TAG
                )
                UpdaterConstants.PREFIX_VERSION + tag
            }
            clean.endsWith(UpdaterConstants.SUFFIX_BETA64) ->
                UpdaterConstants.PREFIX_VERSION + clean.removeSuffix("64")
            clean.endsWith(UpdaterConstants.SUFFIX_BETA32) ->
                UpdaterConstants.PREFIX_VERSION + clean.removeSuffix("32")
            isStableFamily(clean) ->
                UpdaterConstants.PREFIX_VERSION + clean.substringBefore("-")
            // Unrecognized flavor: target a tag that does not exist, so the
            // updater reports a clean "no update" instead of offering a
            // stable APK to an unrecognized build
            else ->
                UpdaterConstants.PREFIX_VERSION +
                clean.substringBefore("-") +
                UpdaterConstants.SUFFIX_UNKNOWN
        }
    }

    /**
     * Returns the APK asset name hosted on the GitHub release matching the
     * given version, e.g. "N-Zik-release64.apk" for the release64 flavor or
     * "N-Zik-dev32.apk" for the dev32 flavor.
     */
    fun assetName(appName: String, versionStr: String): String {
        return "$appName-${extractBuildType(versionStr)}.apk"
    }

    /**
     * The flavor label shared by the header badge, the About badge, the
     * updater screen and the new-update popup — one source of truth so the
     * same build reads the same everywhere (RELEASE, RELEASE 32, MINIFIED,
     * MINIFIED 32, BETA, BETA 32, DEV, DEV 32, plus FOSS and DEBUG).
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
     * back to the release label).
     *
     * The foss build type always yields the fixed "FOSS" text. Both version
     * names ("8.4.0-r32") and display versions ("v8.4.0-r32", the input the
     * updater screen and popup derive) work.
     */
    fun flavorLabel(versionName: String, isFoss: Boolean = false): FlavorLabel? {
        if (isFoss) return FlavorLabel(literal = "FOSS")
        val suffix = extractVersionSuffix(versionName)
        if (suffix.isEmpty()) return null
        return when (suffix) {
            UpdaterConstants.SUFFIX_CHAR_RELEASE64 -> FlavorLabel(R.string.release_title)
            UpdaterConstants.SUFFIX_CHAR_MINIFIED64 -> FlavorLabel(R.string.minified_title)
            UpdaterConstants.SUFFIX_CHAR_DEV64 -> FlavorLabel(R.string.dev_title)
            UpdaterConstants.SUFFIX_CHAR_BETA64 -> FlavorLabel(R.string.beta_title)
            UpdaterConstants.SUFFIX_CHAR_RELEASE32 -> FlavorLabel(R.string.release_title, append32 = true)
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

    /** Returns the changelog URL for the given version: dev-family builds (dev64 + dev32) share a single "dev.txt", every other flavor uses its version code. */
    fun currentChangelogUrl(versionCode: Int, versionStr: String): String {
        return if (isDevFamily(versionStr)) {
            "${UpdateEndpoints.CHANGELOGS_URL}/dev.txt"
        } else {
            "${UpdateEndpoints.CHANGELOGS_URL}/$versionCode.txt"
        }
    }

    /**
     * Returns the version-name suffix ("-r64"/"-r32", "-m64"/"-m32",
     * "-beta64"/"-beta32", "-dev64"/"-dev32") for the selected build's APK
     * asset name. Pre-rename asset names (migration window, CI ships the
     * dual assets) map to their post-rename suffix.
     */
    fun getBuildSuffix(): String {
        if (!::build.isInitialized) return UpdaterConstants.SUFFIX_RELEASE64
        val name = build.name
        return when {
            name.contains(UpdaterConstants.TYPE_BETA64, ignoreCase = true) -> UpdaterConstants.SUFFIX_BETA64
            name.contains(UpdaterConstants.TYPE_BETA32, ignoreCase = true) -> UpdaterConstants.SUFFIX_BETA32
            name.contains(UpdaterConstants.TYPE_MINIFIED64, ignoreCase = true) -> UpdaterConstants.SUFFIX_MINIFIED64
            name.contains(UpdaterConstants.TYPE_MINIFIED32, ignoreCase = true) -> UpdaterConstants.SUFFIX_MINIFIED32
            name.contains(UpdaterConstants.TYPE_DEV64, ignoreCase = true) -> UpdaterConstants.SUFFIX_DEV64
            name.contains(UpdaterConstants.TYPE_DEV32, ignoreCase = true) -> UpdaterConstants.SUFFIX_DEV32
            name.contains(UpdaterConstants.TYPE_RELEASE64, ignoreCase = true) -> UpdaterConstants.SUFFIX_RELEASE64
            name.contains(UpdaterConstants.TYPE_RELEASE32, ignoreCase = true) -> UpdaterConstants.SUFFIX_RELEASE32
            // Migration window: pre-rename asset names (full→r64, full32→r32,
            // minified→m64)
            name.contains(UpdaterConstants.LEGACY_TYPE_MINIFIED64, ignoreCase = true) -> UpdaterConstants.SUFFIX_MINIFIED64
            name.contains(UpdaterConstants.LEGACY_TYPE_RELEASE32, ignoreCase = true) -> UpdaterConstants.SUFFIX_RELEASE32
            name.contains(UpdaterConstants.LEGACY_TYPE_RELEASE64, ignoreCase = true) -> UpdaterConstants.SUFFIX_RELEASE64
            else -> UpdaterConstants.SUFFIX_RELEASE64
        }
    }

    /**
     * Returns the display version with suffix, avoiding duplication.
     * Release tags are shared per channel ("v8.4.5", "v8.4.5-beta",
     * "v8.4.5-dev-<date>") and carry no per-ABI marker, so the ABI part of
     * the build's suffix is composed onto the tag.
     * e.g., tagName="v8.4.5-beta", build=N-Zik-beta32.apk returns "v8.4.5-beta32"
     * e.g., tagName="v8.4.5-dev-20261013", build=N-Zik-dev64.apk returns "v8.4.5-dev64-20261013"
     * e.g., tagName="v8.4.5", build=N-Zik-release32.apk returns "v8.4.5-r32"
     */
    fun getDisplayVersion(): String {
        val tag = githubRelease?.tagName ?: return BuildConfig.VERSION_NAME
        val suffix = getBuildSuffix()
        return when {
            // Beta tag: append only the ABI part ("v8.4.5-beta" + "-beta64" → "v8.4.5-beta64")
            tag.endsWith(UpdaterConstants.SUFFIX_BETA_TAG) ->
                tag + suffix.substringAfter(UpdaterConstants.SUFFIX_CHAR_BETA_TAG, "")
            // Dated dev tag: the ABI marker sits between "dev" and the date
            // ("v8.4.5-dev-20261013" + "-dev32" → "v8.4.5-dev32-20261013")
            tag.contains(UpdaterConstants.SUFFIX_DEV_TAG) ->
                tag.replaceFirst(UpdaterConstants.SUFFIX_DEV_TAG, "$suffix-")
            // Plain stable tag → append the full suffix
            else -> "$tag$suffix"
        }
    }

    /**
     * The APK build types a user on [currentVersion] can install, in
     * priority order, given their beta preference. Returns the post-rename
     * build type names (spec-ffmpeg-extension-module) — the pre-rename
     * equivalents are appended by [extractBuild]/[findBestRelease] via
     * [legacyBuildType] for the migration window.
     *
     * Shared by [extractBuild] (asset selection) and [findBestRelease]
     * (releases must ship at least one of these assets). Empty for unknown
     * flavors — they target the non-existent "-unknown" tag, so no release
     * is ever offered to them. Visible for testing (`UpdaterReleaseTagTest`).
     */
    internal fun targetBuildTypes(
        currentVersion: String,
        checkBetaUpdates: Boolean
    ): List<String> {
        return when (extractVersionSuffix(currentVersion)) {
            // Dev users: only their own ABI's dev asset (both ABIs share the releases)
            UpdaterConstants.SUFFIX_CHAR_DEV64 -> listOf(UpdaterConstants.TYPE_DEV64)
            UpdaterConstants.SUFFIX_CHAR_DEV32 -> listOf(UpdaterConstants.TYPE_DEV32)
            // Release users: the beta channel first when enabled, then their own release asset
            UpdaterConstants.SUFFIX_CHAR_RELEASE64 ->
                if (checkBetaUpdates) listOf(UpdaterConstants.TYPE_BETA64, UpdaterConstants.TYPE_RELEASE64)
                else listOf(UpdaterConstants.TYPE_RELEASE64)
            UpdaterConstants.SUFFIX_CHAR_RELEASE32 ->
                if (checkBetaUpdates) listOf(UpdaterConstants.TYPE_BETA32, UpdaterConstants.TYPE_RELEASE32)
                else listOf(UpdaterConstants.TYPE_RELEASE32)
            // Beta users: the beta channel first when enabled, otherwise the release channel
            UpdaterConstants.SUFFIX_CHAR_BETA64 ->
                if (checkBetaUpdates) listOf(UpdaterConstants.TYPE_BETA64, UpdaterConstants.TYPE_RELEASE64)
                else listOf(UpdaterConstants.TYPE_RELEASE64)
            UpdaterConstants.SUFFIX_CHAR_BETA32 ->
                if (checkBetaUpdates) listOf(UpdaterConstants.TYPE_BETA32, UpdaterConstants.TYPE_RELEASE32)
                else listOf(UpdaterConstants.TYPE_RELEASE32)
            // Minified users: only the minified assets
            UpdaterConstants.SUFFIX_CHAR_MINIFIED64 -> listOf(UpdaterConstants.TYPE_MINIFIED64)
            UpdaterConstants.SUFFIX_CHAR_MINIFIED32 -> listOf(UpdaterConstants.TYPE_MINIFIED32)
            // Unknown flavor — no targeted asset, and the "-unknown" tag
            // gate in findBestRelease keeps every release out
            else -> emptyList()
        }
    }

    /**
     * The pre-rename asset-name base for a post-rename build type
     * (spec-ffmpeg-extension-module): release64→full, release32→full32,
     * minified64→minified. All other bases (minified32/beta64/beta32/dev64/
     * dev32) map to themselves. Used to accept both naming schemes while CI
     * ships the dual assets.
     */
    private fun legacyBuildType(buildType: String): String = when (buildType) {
        UpdaterConstants.TYPE_RELEASE64 -> UpdaterConstants.LEGACY_TYPE_RELEASE64
        UpdaterConstants.TYPE_RELEASE32 -> UpdaterConstants.LEGACY_TYPE_RELEASE32
        UpdaterConstants.TYPE_MINIFIED64 -> UpdaterConstants.LEGACY_TYPE_MINIFIED64
        else -> buildType
    }

    /** The asset-name bases to look for a user: post-rename first, then pre-rename (migration window). */
    private fun candidateBuildTypes(
        currentVersion: String,
        checkBetaUpdates: Boolean
    ): List<String> {
        val targets = targetBuildTypes(currentVersion, checkBetaUpdates)
        return (targets + targets.map(::legacyBuildType)).distinct()
    }

    /**
     * Visible for testing: selects the APK build matching the current flavor
     * from a release's assets (see `UpdaterReleaseTagTest`). Post-rename asset
     * names are tried first, then their pre-rename equivalents — the list
     * stays 32-aware, so a 32-bit user is never pointed at a 64-bit APK
     * (un-installable on a 32-bit-only device). Unknown flavors have no
     * matching asset and report "no update".
     */
    internal fun extractBuild(
        assets: List<GithubRelease.Build>,
        checkBetaUpdates: Boolean = false,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): GithubRelease.Build {
        val appName = BuildConfig.APP_NAME

        // Unknown flavors target the non-existent "-unknown" tag — there is no
        // base asset to fall back to, so a clean "no update" is the correct
        // outcome (a missing suffix, i.e. foss, still falls back below)
        if (!isKnownFlavor(currentVersion)) {
            Timber.tag("Updater").w("No matching build found for $currentVersion")
            throw NoSuchFileException("")
        }

        // Known flavors: post-rename names, then pre-rename (migration window)
        for (buildType in candidateBuildTypes(currentVersion, checkBetaUpdates)) {
            val fileName = "$appName-$buildType.apk"
            val foundBuild = assets.fastFirstOrNull { it.name == fileName }
            if (foundBuild != null) {
                return foundBuild
            }
        }

        // Known flavors whose targeted assets are missing (e.g. a beta user
        // with beta disabled facing a beta-only release): fall back to the
        // asset of their own build type — new name, then pre-rename
        val base = extractBuildType(currentVersion)
        for (fileName in listOf(base, legacyBuildType(base)).distinct().map { "$appName-$it.apk" }) {
            val fallbackBuild = assets.fastFirstOrNull { it.name == fileName }
            if (fallbackBuild != null) {
                Timber.tag("Updater").w("No targeted build found, falling back to $fileName")
                return fallbackBuild
            }
        }

        Timber.tag("Updater").w("No matching build found for $currentVersion")
        throw NoSuchFileException("")
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
     * Turns `v1.0.0` to `1.0.0`, `1.0.0-m64` to `1.0.0`
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

        // Unrecognized flavors (e.g. debug) target the non-existent
        // "-unknown" tag in the channel check below, so the updater reports
        // a clean "no update" instead of silently defaulting to stable
        if (!isKnownFlavor(currentVersion)) {
            Timber.tag("Updater").d("Unknown version suffix '$currentSuffix' — targeting the unknown tag, no update is offered")
        }

        // Assets this user can actually install (empty for unknown flavors,
        // which the "-unknown" tag gate below keeps out anyway). Both the
        // post-rename and the pre-rename asset names are accepted during the
        // migration window when CI ships the dual assets
        // (spec-ffmpeg-extension-module)
        val installableAssets = candidateBuildTypes(currentVersion, checkBetaUpdates)
            .map { "${BuildConfig.APP_NAME}-$it.apk" }

        // Filter releases based on current build type and beta preferences
        val eligibleReleases = releases.filter { release ->
            val releaseSuffix = extractVersionSuffix(release.tagName)

            val channelOk = when {
                // Dev users (dev64 + dev32): only accept the dev channel
                isDevFamily(currentVersion) -> releaseSuffix.startsWith(UpdaterConstants.SUFFIX_CHAR_DEV_TAG)
                // Release users with beta enabled: accept both beta and stable tags
                currentSuffix in listOf(UpdaterConstants.SUFFIX_CHAR_RELEASE64, UpdaterConstants.SUFFIX_CHAR_RELEASE32) && checkBetaUpdates ->
                    releaseSuffix == "" || releaseSuffix == UpdaterConstants.SUFFIX_CHAR_BETA_TAG
                // Release users with beta disabled: only stable tags
                currentSuffix in listOf(UpdaterConstants.SUFFIX_CHAR_RELEASE64, UpdaterConstants.SUFFIX_CHAR_RELEASE32) && !checkBetaUpdates ->
                    releaseSuffix == ""
                // Beta users with beta enabled: accept both beta and stable tags
                isBetaFamily(currentVersion) && checkBetaUpdates ->
                    releaseSuffix == UpdaterConstants.SUFFIX_CHAR_BETA_TAG || releaseSuffix == ""
                // Beta users with beta disabled: only stable tags
                isBetaFamily(currentVersion) && !checkBetaUpdates -> releaseSuffix == ""
                // Stable users (minified and plain stable): only stable tags
                isStableFamily(currentVersion) -> releaseSuffix == ""
                // Unrecognized flavor: only the non-existent "-unknown" tag —
                // no release matches, so the updater reports a clean "no update"
                else -> releaseSuffix == UpdaterConstants.SUFFIX_CHAR_UNKNOWN
            }

            // Skip releases that ship none of the user's installable assets
            // (e.g. a 32-bit user facing a beta release published without the
//
            // matching 32-bit asset, or a docs-only release) — the user then
            // falls back to the best release that does ship one. Unknown
//
            // flavors have no installable list, so the asset filter passes
            // for them (the "-unknown" tag gate above already excludes every
            // real release).
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

    /**
     * Builds without auto-update (debug/foss) never run an update check and never fetch the
     * beta channel: the stored settings (possibly written by an updater build on the same
     * device) are forced off so they cannot leak into a manual check or the displayed state.
     */
    internal fun effectiveUpdaterSettings(
        storedState: CheckUpdateState,
        storedBeta: Boolean,
        autoUpdateEnabled: Boolean
    ): Pair<CheckUpdateState, Boolean> =
        if (autoUpdateEnabled) storedState to storedBeta else CheckUpdateState.Disabled to false

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

