package app.n_zik.android.updater.services

import app.n_zik.android.updater.models.UpdateEndpoints
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.updater.models.GithubRelease
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.NoSuchFileException

/**
 * Pins the version → release mapping (release tag, asset name, build type label,
 * download URL) for every build flavor, plus the dev/dev32 release selection.
 *
 * Dev releases are published under dated tags ("v8.4.0-dev-20261013") shared by
 * both the dev and dev32 flavors ("N-Zik-dev.apk" / "N-Zik-dev32.apk"); a single
 * version string may no longer be re-derived differently in each consumer.
 */
class UpdaterReleaseTagTest {

    @BeforeEach
    fun resetUpdaterState() {
        Updater.githubRelease = null
        Updater.latestChangelog = null
        Updater.currentChangelog = null
        // lateinit cannot be reset directly — clear the backing field so
        // `::build.isInitialized` goes back to false between tests
        val buildField = Updater::class.java.getDeclaredField("build")
        buildField.isAccessible = true
        buildField.set(Updater, null)
    }

    // region releaseTag

    @Test
    fun `releaseTag keeps the dated tag for dev builds`() {
        assertEquals("v8.4.0-dev-20261013", Updater.releaseTag("8.4.0-dev-20261013"))
    }

    @Test
    fun `releaseTag maps dev32 to the shared dev tag`() {
        assertEquals("v8.4.0-dev-20261013", Updater.releaseTag("8.4.0-dev32-20261013"))
    }

    @Test
    fun `releaseTag maps an undated dev32 version to the undated dev tag instead of a nonexistent tag`() {
        // Not producible by the build (the dev32 suffix always carries a date),
        // but must not yield the non-existent "v8.4.0-dev32" tag
        assertEquals("v8.4.0-dev", Updater.releaseTag("8.4.0-dev32"))
    }

    @Test
    fun `releaseTag keeps the full tag for beta builds`() {
        assertEquals("v8.4.0-beta", Updater.releaseTag("8.4.0-beta"))
    }

    @Test
    fun `releaseTag uses the base version for full builds`() {
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-f"))
    }

    @Test
    fun `releaseTag uses the base version for minified builds`() {
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-m"))
    }

    @Test
    fun `releaseTag uses the base version for plain builds`() {
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0"))
    }

    @Test
    fun `releaseTag maps beta32 to the shared beta tag`() {
        assertEquals("v8.4.0-beta", Updater.releaseTag("8.4.0-beta32"))
    }

    @Test
    fun `releaseTag uses the base version for 32-bit stable builds`() {
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-f32"))
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-m32"))
    }

    // endregion

    // region assetName

    @Test
    fun `assetName resolves the dev apk for dev builds`() {
        assertEquals("N-Zik-dev.apk", Updater.assetName("N-Zik", "8.4.0-dev-20261013"))
    }

    @Test
    fun `assetName resolves the dev32 apk for dev32 builds`() {
        assertEquals("N-Zik-dev32.apk", Updater.assetName("N-Zik", "8.4.0-dev32-20261013"))
    }

    @Test
    fun `assetName keeps the flavor apk for stable flavors`() {
        assertEquals("N-Zik-beta.apk", Updater.assetName("N-Zik", "8.4.0-beta"))
        assertEquals("N-Zik-minified.apk", Updater.assetName("N-Zik", "8.4.0-m"))
        assertEquals("N-Zik-full.apk", Updater.assetName("N-Zik", "8.4.0-f"))
        assertEquals("N-Zik-full.apk", Updater.assetName("N-Zik", "8.4.0"))
    }

    @Test
    fun `assetName resolves the 32-bit apk for 32-bit flavors`() {
        assertEquals("N-Zik-full32.apk", Updater.assetName("N-Zik", "8.4.0-f32"))
        assertEquals("N-Zik-minified32.apk", Updater.assetName("N-Zik", "8.4.0-m32"))
        assertEquals("N-Zik-beta32.apk", Updater.assetName("N-Zik", "8.4.0-beta32"))
    }

    // endregion

    // region redownloadUrl

    @Test
    fun `redownloadUrl points to the dev asset under the dated dev tag`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-dev-20261013/N-Zik-dev.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-dev-20261013")
        )
    }

    @Test
    fun `redownloadUrl points to the dev32 asset under the dev tag`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-dev-20261013/N-Zik-dev32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-dev32-20261013")
        )
    }

    @Test
    fun `redownloadUrl keeps the stable url for full builds`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0/N-Zik-full.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-f")
        )
    }

    @Test
    fun `redownloadUrl points to the 32-bit assets of their release`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0/N-Zik-full32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-f32")
        )
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0/N-Zik-minified32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-m32")
        )
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-beta/N-Zik-beta32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-beta32")
        )
    }

    // endregion

    // region flavorLabel

    @Test
    fun `flavorLabel maps the 64-bit flavors to their labels without the 32 marker`() {
        val f = Updater.flavorLabel("8.4.0-f")
        assertEquals(R.string.full_title, f?.resId)
        assertFalse(f?.append32 == true)
        assertNull(f?.literal)
        assertEquals(R.string.minified_title, Updater.flavorLabel("8.4.0-m")?.resId)
        assertEquals(R.string.beta_title, Updater.flavorLabel("8.4.0-beta")?.resId)
        assertEquals(R.string.dev_title, Updater.flavorLabel("8.4.0-dev-20261013")?.resId)
        assertEquals(R.string.debug_title, Updater.flavorLabel("8.4.0-debug")?.resId)
    }

    @Test
    fun `flavorLabel maps the 32-bit flavors to their label plus the 32 marker`() {
        val f32 = Updater.flavorLabel("8.4.0-f32")
        assertEquals(R.string.full_title, f32?.resId)
        assertTrue(f32?.append32 == true)
        assertEquals(R.string.minified_title, Updater.flavorLabel("8.4.0-m32")?.resId)
        assertTrue(Updater.flavorLabel("8.4.0-m32")?.append32 == true)
        assertEquals(R.string.beta_title, Updater.flavorLabel("8.4.0-beta32")?.resId)
        assertTrue(Updater.flavorLabel("8.4.0-beta32")?.append32 == true)
        assertEquals(R.string.dev_title, Updater.flavorLabel("8.4.0-dev32-20261013")?.resId)
        assertTrue(Updater.flavorLabel("8.4.0-dev32-20261013")?.append32 == true)
    }

    @Test
    fun `flavorLabel works on the display versions used by the updater screen and popup`() {
        assertEquals(R.string.full_title, Updater.flavorLabel("v8.5.0-f")?.resId)
        assertTrue(Updater.flavorLabel("v8.5.0-f32")?.append32 == true)
        assertTrue(Updater.flavorLabel("v8.5.0-beta32")?.append32 == true)
        assertEquals(R.string.dev_title, Updater.flavorLabel("v8.5.0-dev32-20261013")?.resId)
        assertEquals(R.string.beta_title, Updater.flavorLabel("v8.5.0-beta")?.resId)
    }

    @Test
    fun `flavorLabel returns the FOSS literal for the foss build type regardless of the suffix`() {
        assertEquals("FOSS", Updater.flavorLabel("8.4.0-f", isFoss = true)?.literal)
        assertEquals("FOSS", Updater.flavorLabel("8.4.0", isFoss = true)?.literal)
        assertNull(Updater.flavorLabel("8.4.0-f")?.literal)
    }

    @Test
    fun `flavorLabel returns null for a version without suffix so badges stay hidden`() {
        assertNull(Updater.flavorLabel("8.4.0"))
    }

    @Test
    fun `flavorLabel uppercases an unknown suffix instead of falling back silently`() {
        assertEquals("GAMMA", Updater.flavorLabel("8.4.0-gamma")?.literal)
    }

    // endregion

    // region isDevFamily

    @Test
    fun `isDevFamily detects only the dev flavors`() {
        assertTrue(Updater.isDevFamily("8.4.0-dev-20261013"))
        assertTrue(Updater.isDevFamily("8.4.0-dev32-20261013"))
        assertFalse(Updater.isDevFamily("8.4.0-beta"))
        assertFalse(Updater.isDevFamily("8.4.0-f"))
        assertFalse(Updater.isDevFamily("8.4.0-m"))
        assertFalse(Updater.isDevFamily("8.4.0"))
    }

    @Test
    fun `family helpers detect each channel`() {
        assertTrue(Updater.isMinifiedFamily("8.4.0-m"))
        assertTrue(Updater.isMinifiedFamily("8.4.0-m32"))
        assertFalse(Updater.isMinifiedFamily("8.4.0-f32"))

        assertTrue(Updater.isBetaFamily("8.4.0-beta"))
        assertTrue(Updater.isBetaFamily("8.4.0-beta32"))
        assertFalse(Updater.isBetaFamily("8.4.0-dev-20261013"))
        assertFalse(Updater.isBetaFamily("8.4.0-f"))
    }

    // endregion

    // region getBuildSuffix

    @Test
    fun `getBuildSuffix keeps the 32-bit marker for the dev family`() {
        Updater.build = build("N-Zik-dev32.apk")
        assertEquals("-dev32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-dev.apk")
        assertEquals("-dev", Updater.getBuildSuffix())
    }

    @Test
    fun `getBuildSuffix keeps the 32-bit marker for the other families`() {
        Updater.build = build("N-Zik-full32.apk")
        assertEquals("-f32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-minified32.apk")
        assertEquals("-m32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-beta32.apk")
        assertEquals("-beta32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-full.apk")
        assertEquals("-f", Updater.getBuildSuffix())
    }

    // endregion

    // region getDisplayVersion

    @Test
    fun `getDisplayVersion does not duplicate the suffix on dated dev tags`() {
        val build = build("N-Zik-dev.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0-dev-20261013", build)

        assertEquals("v8.4.0-dev-20261013", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion still appends the suffix on plain tags`() {
        val build = build("N-Zik-beta.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0", build)

        assertEquals("v8.4.0-beta", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion keeps the 32-bit marker on the suffix`() {
        val build = build("N-Zik-full32.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0", build)

        assertEquals("v8.4.0-f32", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion swaps the base suffix for the 32-bit one on beta tags`() {
        val build = build("N-Zik-beta32.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0-beta", build)

        assertEquals("v8.4.0-beta32", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion swaps the base suffix for the 32-bit one on dated dev tags`() {
        val build = build("N-Zik-dev32.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0-dev-20261013", build)

        assertEquals("v8.4.0-dev32-20261013", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion keeps the 32-bit marker on the minified suffix`() {
        val build = build("N-Zik-minified32.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0", build)

        assertEquals("v8.4.0-m32", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion falls back to the current version without a release`() {
        assertNull(Updater.githubRelease)

        assertEquals(BuildConfig.VERSION_NAME, Updater.getDisplayVersion())
    }

    // endregion

    // region findBestRelease

    @Test
    fun `findBestRelease picks the newest dev release for a dev user`() {
        // Dev releases ship both dev assets (ground truth: shared dev tag)
        val older = release("v8.4.0-dev-20261012", build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))
        val newest = release("v8.4.0-dev-20261013", build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))
        val stable = release("v8.4.0")

        val best = Updater.findBestRelease(
            listOf(stable, newest, older),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev-20261012"
        )

        assertEquals("v8.4.0-dev-20261013", best?.tagName)
    }

    @Test
    fun `findBestRelease picks the newest dev release for a dev32 user`() {
        // Dev releases ship both dev assets (ground truth: shared dev tag)
        val older = release("v8.4.0-dev-20261012", build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))
        val newest = release("v8.4.0-dev-20261013", build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))
        val stable = release("v8.4.0")

        val best = Updater.findBestRelease(
            listOf(stable, older, newest),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev32-20261012"
        )

        assertEquals("v8.4.0-dev-20261013", best?.tagName)
    }

    @Test
    fun `findBestRelease keeps the minified32 user on full releases only`() {
        // Full releases ship the minified32 asset (ground truth)
        val stable = release("v8.4.0", build("N-Zik-minified32.apk"))
        val beta = release("v8.4.0-beta")
        val olderStable = release("v8.3.5", build("N-Zik-minified32.apk"))

        val best = Updater.findBestRelease(
            listOf(beta, stable, olderStable),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-m32"
        )

        assertEquals("v8.4.0", best?.tagName)
    }

    @Test
    fun `findBestRelease gives a full32 user the beta release when beta is enabled`() {
        // Ground truth: the beta release ships the beta32 asset, the full one the full32 asset
        val stable = release("v8.4.0", build("N-Zik-full32.apk"))
        val beta = release("v8.4.1-beta", build("N-Zik-beta32.apk"))

        assertEquals(
            "v8.4.1-beta",
            Updater.findBestRelease(listOf(stable, beta), checkBetaUpdates = true, currentVersion = "8.4.0-f32")?.tagName
        )
        assertEquals(
            "v8.4.0",
            Updater.findBestRelease(listOf(stable, beta), checkBetaUpdates = false, currentVersion = "8.4.0-f32")?.tagName
        )
    }

    @Test
    fun `findBestRelease gives a beta32 user the beta release`() {
        // Ground truth: the beta release ships the beta32 asset, the full one the full32 asset
        val stable = release("v8.4.0", build("N-Zik-full32.apk"))
        val beta = release("v8.4.1-beta", build("N-Zik-beta32.apk"))

        val best = Updater.findBestRelease(
            listOf(stable, beta),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-beta32"
        )

        assertEquals("v8.4.1-beta", best?.tagName)
    }

    @Test
    fun `findBestRelease ignores the beta flag for dev family users`() {
        val beta = release("v8.5.0-beta", build("N-Zik-beta.apk"))
        val dev = release("v8.5.0-dev-20261013", build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))

        val best = Updater.findBestRelease(
            listOf(beta, dev),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-dev-20261012"
        )

        assertEquals("v8.5.0-dev-20261013", best?.tagName)
    }

    @Test
    fun `findBestRelease skips a beta release that ships no 32-bit asset for a 32-bit user`() {
        // A beta release published without the 32-bit asset must be skipped —
        // the user falls back to the best stable release that ships full32
        val betaOnly64 = release("v8.5.0-beta", build("N-Zik-beta.apk"))
        val stable = release("v8.5.0", build("N-Zik-full.apk"), build("N-Zik-full32.apk"))

        val best = Updater.findBestRelease(
            listOf(betaOnly64, stable),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-f32"
        )

        assertEquals("v8.5.0", best?.tagName)
    }

    @Test
    fun `findBestRelease returns null when no dev release ships the dev32 asset`() {
        val devOnly64 = release("v8.5.0-dev-20261013", build("N-Zik-dev.apk"))

        val best = Updater.findBestRelease(
            listOf(devOnly64),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev32-20261012"
        )

        assertNull(best)
    }

    @Test
    fun `findBestRelease ignores stable and beta releases for a dev user`() {
        val stable = release("v8.4.0")
        val beta = release("v8.4.0-beta")
        val dev = release("v8.3.9-dev-20260101", build("N-Zik-dev.apk"))

        val best = Updater.findBestRelease(
            listOf(stable, beta, dev),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev-20260102"
        )

        assertEquals("v8.3.9-dev-20260101", best?.tagName)
    }

    @Test
    fun `findBestRelease skips an asset-less release for a known flavor`() {
        // A docs-only (asset-less) release must not win for a user with a
        // known flavor — the best release that actually ships an installable

        // APK is selected instead
        val assetlessNewest = release("v8.5.0")
        val stable = release("v8.4.0", build("N-Zik-full.apk"))

        val best = Updater.findBestRelease(
            listOf(assetlessNewest, stable),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-f"
        )

        assertEquals("v8.4.0", best?.tagName)
    }

    @Test
    fun `findBestRelease keeps the asset-less pass for an unknown flavor`() {
        // Unknown flavors have no installable asset list — the legacy
        // behavior (any release passes the asset filter) is kept for them
        val assetless = release("v8.5.0")

        val best = Updater.findBestRelease(
            listOf(assetless),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-debug"
        )

        assertEquals("v8.5.0", best?.tagName)
    }

    @Test
    fun `findBestRelease defaults to full releases for an unknown flavor user`() {
        val beta = release("v8.5.0-beta")
        val stable = release("v8.5.0")

        val best = Updater.findBestRelease(
            listOf(beta, stable),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-debug"
        )

        assertEquals("v8.5.0", best?.tagName)
    }

    // endregion

    // region targetBuildTypes

    @Test
    fun `targetBuildTypes offers the beta and full assets for full users when beta is enabled`() {
        assertEquals(
            listOf("beta", "full"),
            Updater.targetBuildTypes("8.4.0-f", checkBetaUpdates = true)
        )
        assertEquals(
            listOf("beta32", "full32"),
            Updater.targetBuildTypes("8.4.0-f32", checkBetaUpdates = true)
        )
    }

    @Test
    fun `targetBuildTypes keeps the single channel for non-beta users`() {
        assertEquals(listOf("full"), Updater.targetBuildTypes("8.4.0-f", checkBetaUpdates = false))
        assertEquals(listOf("full32"), Updater.targetBuildTypes("8.4.0-f32", checkBetaUpdates = false))
        // A beta user with beta disabled is served full releases only
        assertEquals(listOf("full"), Updater.targetBuildTypes("8.4.0-beta", checkBetaUpdates = false))
        // Minified users never get beta, regardless of the flag
        assertEquals(listOf("minified"), Updater.targetBuildTypes("8.4.0-m", checkBetaUpdates = true))
        assertEquals(listOf("minified32"), Updater.targetBuildTypes("8.4.0-m32", checkBetaUpdates = true))
    }

    @Test
    fun `targetBuildTypes ignores the beta flag for dev family users`() {
        assertEquals(listOf("dev"), Updater.targetBuildTypes("8.4.0-dev-20261013", checkBetaUpdates = true))
        assertEquals(listOf("dev"), Updater.targetBuildTypes("8.4.0-dev-20261013", checkBetaUpdates = false))
        assertEquals(listOf("dev32"), Updater.targetBuildTypes("8.4.0-dev32-20261013", checkBetaUpdates = true))
    }

    @Test
    fun `targetBuildTypes returns empty for unknown flavors`() {
        assertTrue(Updater.targetBuildTypes("8.4.0-debug", checkBetaUpdates = true).isEmpty())
        assertTrue(Updater.targetBuildTypes("8.4.0", checkBetaUpdates = false).isEmpty())
    }

    // endregion

    // region extractBuild

    @Test
    fun `extractBuild gives the dev32 user the dev32 asset`() {
        val assets = listOf(build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))

        val chosen = Updater.extractBuild(assets, currentVersion = "8.4.0-dev32-20261012")

        assertEquals("N-Zik-dev32.apk", chosen.name)
    }

    @Test
    fun `extractBuild gives the dev user the dev asset`() {
        val assets = listOf(build("N-Zik-dev.apk"), build("N-Zik-dev32.apk"))

        val chosen = Updater.extractBuild(assets, currentVersion = "8.4.0-dev-20261012")

        assertEquals("N-Zik-dev.apk", chosen.name)
    }

    @Test
    fun `extractBuild keeps the stable flavor selection`() {
        val assets = listOf(build("N-Zik-beta.apk"), build("N-Zik-full.apk"), build("N-Zik-minified.apk"))

        assertEquals("N-Zik-full.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-f").name)
        // beta user with the beta channel enabled gets the beta asset; disabled they get full
        assertEquals("N-Zik-beta.apk", Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-beta").name)
        assertEquals("N-Zik-full.apk", Updater.extractBuild(assets, checkBetaUpdates = false, currentVersion = "8.4.0-beta").name)
        assertEquals("N-Zik-minified.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-m").name)
    }

    @Test
    fun `extractBuild gives 32-bit users the 32-bit assets`() {
        val assets = listOf(
            build("N-Zik-full.apk"), build("N-Zik-full32.apk"),
            build("N-Zik-minified.apk"), build("N-Zik-minified32.apk"),
            build("N-Zik-beta.apk"), build("N-Zik-beta32.apk")
        )

        assertEquals("N-Zik-full32.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-f32").name)
        assertEquals("N-Zik-minified32.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-m32").name)
        assertEquals("N-Zik-beta32.apk", Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-beta32").name)
        // beta32 with beta disabled falls back to the 32-bit full asset
        assertEquals("N-Zik-full32.apk", Updater.extractBuild(assets, checkBetaUpdates = false, currentVersion = "8.4.0-beta32").name)
    }

    @Test
    fun `extractBuild gives the full32 user the beta32 asset when beta is enabled`() {
        val assets = listOf(
            build("N-Zik-full.apk"), build("N-Zik-full32.apk"),
            build("N-Zik-beta.apk"), build("N-Zik-beta32.apk")
        )

        assertEquals("N-Zik-beta32.apk", Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-f32").name)
        assertEquals("N-Zik-full32.apk", Updater.extractBuild(assets, checkBetaUpdates = false, currentVersion = "8.4.0-f32").name)
    }

    @Test
    fun `extractBuild refuses a 64-bit fallback for 32-bit users`() {
        // Release without any 32-bit asset: a 64-bit APK must never be offered
        // to a 32-bit-only device — "no update" is the correct outcome
        val assets = listOf(build("N-Zik-full.apk"), build("N-Zik-beta.apk"))

        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(assets, currentVersion = "8.4.0-f32")
        }
        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-beta32")
        }
    }

    @Test
    fun `extractBuild refuses a 64-bit fallback for dev32 and minified32 users`() {
        val devOnly64 = listOf(build("N-Zik-dev.apk"))
        val minifiedOnly64 = listOf(build("N-Zik-minified.apk"))

        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(devOnly64, currentVersion = "8.4.0-dev32-20261012")
        }
        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(minifiedOnly64, currentVersion = "8.4.0-m32")
        }
    }

    @Test
    fun `extractBuild falls back to the base asset for an unknown flavor`() {
        val assets = listOf(build("N-Zik-full.apk"), build("N-Zik-beta.apk"))

        assertEquals("N-Zik-full.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-debug").name)
    }

    // endregion

    // region currentChangelogUrl

    @Test
    fun `currentChangelogUrl serves the dev changelog file for the dev and dev32 flavors`() {
        assertEquals(
            "${UpdateEndpoints.CHANGELOGS_URL}/dev.txt",
            Updater.currentChangelogUrl(123, "8.4.0-dev-20261013")
        )
        assertEquals(
            "${UpdateEndpoints.CHANGELOGS_URL}/dev.txt",
            Updater.currentChangelogUrl(123, "8.4.0-dev32-20261013")
        )
    }

    @Test
    fun `currentChangelogUrl serves the version-code file for the other flavors`() {
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-f"))
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-f32"))
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-beta"))
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-m32"))
    }

    // endregion

    // region isVersionNewer

    @Test
    fun `isVersionNewer compares dev dates when the base version matches`() {
        assertTrue(Updater.isVersionNewer("v8.4.0-dev-20261013", "8.4.0-dev-20261012"))
        assertFalse(Updater.isVersionNewer("v8.4.0-dev-20261012", "8.4.0-dev-20261013"))
    }

    @Test
    fun `isVersionNewer prefers the higher base version`() {
        assertTrue(Updater.isVersionNewer("v8.4.0", "8.3.9-f"))
        assertFalse(Updater.isVersionNewer("v8.3.9", "8.4.0-f"))
    }

    @Test
    fun `isVersionNewer compares a dev release tag against a dev32 current version`() {
        // Shared dev releases: a dev32 user's current version is compared
        // against the dated dev release tag on its date
        assertTrue(Updater.isVersionNewer("v8.4.0-dev-20261013", "8.4.0-dev32-20261012"))
        assertFalse(Updater.isVersionNewer("v8.4.0-dev-20261012", "8.4.0-dev32-20261012"))
    }

    // endregion

    private fun build(name: String) = GithubRelease.Build(
        id = 1u,
        url = "https://api.github.com/repos/N-Zik-Group/N-Zik/releases/assets/1",
        name = name,
        size = 1u,
        createdAt = "2026-10-13T04:59:11Z",
        downloadUrl = "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-dev-20261013/$name"
    )

    private fun release(tagName: String, vararg builds: GithubRelease.Build): GithubRelease = GithubRelease(
        id = 1u,
        tagName = tagName,
        name = tagName,
        body = "",
        builds = builds.toList()
    )
}
