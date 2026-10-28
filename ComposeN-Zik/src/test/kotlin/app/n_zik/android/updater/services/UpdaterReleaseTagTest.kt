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

/** Pins channel and ABI routing, release asset eligibility, and retained minified compatibility. */
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
    fun `releaseTag maps dev64 to the shared dated dev tag`() {
        assertEquals("v8.4.0-dev-20261013", Updater.releaseTag("8.4.0-dev64-20261013"))
    }

    @Test
    fun `releaseTag maps dev32 to the shared dated dev tag`() {
        assertEquals("v8.4.0-dev-20261013", Updater.releaseTag("8.4.0-dev32-20261013"))
    }

    @Test
    fun `releaseTag maps an undated dev64 version to the undated dev tag instead of a nonexistent tag`() {
        // Not producible by the build (the dev suffix always carries a date),
        // but must not yield the non-existent "v8.4.0-dev64" tag
        assertEquals("v8.4.0-dev", Updater.releaseTag("8.4.0-dev64"))
        assertEquals("v8.4.0-dev", Updater.releaseTag("8.4.0-dev32"))
    }

    @Test
    fun `releaseTag maps beta64 and beta32 to the shared beta tag`() {
        assertEquals("v8.4.0-beta", Updater.releaseTag("8.4.0-beta64"))
        assertEquals("v8.4.0-beta", Updater.releaseTag("8.4.0-beta32"))
    }

    @Test
    fun `releaseTag uses the base version for the stable flavors`() {
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-r64"))
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-r32"))
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-m64"))
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0-m32"))
    }

    @Test
    fun `releaseTag uses the base version for plain builds`() {
        assertEquals("v8.4.0", Updater.releaseTag("8.4.0"))
    }

    @Test
    fun `releaseTag targets the non-existent unknown tag for an unrecognized flavor`() {
        // Unknown flavors must not fall back to the stable tag — they are
        // served a tag that does not exist, so the updater reports "no update"
        assertEquals("v8.4.0-unknown", Updater.releaseTag("8.4.0-xyz64"))
        assertEquals("v8.4.0-unknown", Updater.releaseTag("8.4.0-gamma"))
        assertEquals("v8.4.0-unknown", Updater.releaseTag("8.4.0-debug"))
    }

    // endregion

    // region assetName

    @Test
    fun `assetName resolves the per-ABI apks for the stable flavors`() {
        assertEquals("N-Zik-release64.apk", Updater.assetName("N-Zik", "8.4.0-r64"))
        assertEquals("N-Zik-release32.apk", Updater.assetName("N-Zik", "8.4.0-r32"))
        assertEquals("N-Zik-minified64.apk", Updater.assetName("N-Zik", "8.4.0-m64"))
        assertEquals("N-Zik-minified32.apk", Updater.assetName("N-Zik", "8.4.0-m32"))
    }

    @Test
    fun `assetName resolves the per-ABI apks for the beta and dev flavors`() {
        assertEquals("N-Zik-beta64.apk", Updater.assetName("N-Zik", "8.4.0-beta64"))
        assertEquals("N-Zik-beta32.apk", Updater.assetName("N-Zik", "8.4.0-beta32"))
        assertEquals("N-Zik-dev64.apk", Updater.assetName("N-Zik", "8.4.0-dev64-20261013"))
        assertEquals("N-Zik-dev32.apk", Updater.assetName("N-Zik", "8.4.0-dev32-20261013"))
    }

    @Test
    fun `assetName defaults an unknown flavor to the release64 apk`() {
        assertEquals("N-Zik-release64.apk", Updater.assetName("N-Zik", "8.4.0"))
    }

    // endregion

    // region redownloadUrl

    @Test
    fun `redownloadUrl points to the dev assets under the shared dated dev tag`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-dev-20261013/N-Zik-dev64.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-dev64-20261013")
        )
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-dev-20261013/N-Zik-dev32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-dev32-20261013")
        )
    }

    @Test
    fun `redownloadUrl points to the stable assets of their release`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0/N-Zik-release64.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-r64")
        )
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0/N-Zik-release32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-r32")
        )
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0/N-Zik-minified32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-m32")
        )
    }

    @Test
    fun `redownloadUrl points to the beta asset under the shared beta tag`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-beta/N-Zik-beta32.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-beta32")
        )
    }

    @Test
    fun `redownloadUrl points under the non-existent unknown tag for an unrecognized flavor`() {
        assertEquals(
            "https://github.com/N-Zik-Group/N-Zik/releases/download/v8.4.0-unknown/N-Zik-release64.apk",
            Updater.redownloadUrl("N-Zik", "8.4.0-gamma")
        )
    }

    // endregion

    // region flavorLabel

    @Test
    fun `flavorLabel maps the 64-bit flavors to their labels without the 32 marker`() {
        val r64 = Updater.flavorLabel("8.4.0-r64")
        assertEquals(R.string.release_title, r64?.resId)
        assertFalse(r64?.append32 == true)
        assertNull(r64?.literal)
        assertEquals(R.string.minified_title, Updater.flavorLabel("8.4.0-m64")?.resId)
        assertEquals(R.string.beta_title, Updater.flavorLabel("8.4.0-beta64")?.resId)
        assertEquals(R.string.dev_title, Updater.flavorLabel("8.4.0-dev64-20261013")?.resId)
        assertEquals(R.string.debug_title, Updater.flavorLabel("8.4.0-debug")?.resId)
    }

    @Test
    fun `flavorLabel maps the 32-bit flavors to their label plus the 32 marker`() {
        val r32 = Updater.flavorLabel("8.4.0-r32")
        assertEquals(R.string.release_title, r32?.resId)
        assertTrue(r32?.append32 == true)
        assertEquals(R.string.minified_title, Updater.flavorLabel("8.4.0-m32")?.resId)
        assertTrue(Updater.flavorLabel("8.4.0-m32")?.append32 == true)
        assertEquals(R.string.beta_title, Updater.flavorLabel("8.4.0-beta32")?.resId)
        assertTrue(Updater.flavorLabel("8.4.0-beta32")?.append32 == true)
        assertEquals(R.string.dev_title, Updater.flavorLabel("8.4.0-dev32-20261013")?.resId)
        assertTrue(Updater.flavorLabel("8.4.0-dev32-20261013")?.append32 == true)
    }

    @Test
    fun `flavorLabel works on the display versions used by the updater screen and popup`() {
        assertEquals(R.string.release_title, Updater.flavorLabel("v8.5.0-r64")?.resId)
        assertTrue(Updater.flavorLabel("v8.5.0-r32")?.append32 == true)
        assertTrue(Updater.flavorLabel("v8.5.0-beta32")?.append32 == true)
        assertEquals(R.string.dev_title, Updater.flavorLabel("v8.5.0-dev32-20261013")?.resId)
        assertEquals(R.string.beta_title, Updater.flavorLabel("v8.5.0-beta64")?.resId)
    }

    @Test
    fun `flavorLabel returns the FOSS literal for the foss build type regardless of the suffix`() {
        assertEquals("FOSS", Updater.flavorLabel("8.4.0-r64", isFoss = true)?.literal)
        assertEquals("FOSS", Updater.flavorLabel("8.4.0", isFoss = true)?.literal)
        assertNull(Updater.flavorLabel("8.4.0-r64")?.literal)
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
        assertTrue(Updater.isDevFamily("8.4.0-dev64-20261013"))
        assertTrue(Updater.isDevFamily("8.4.0-dev32-20261013"))
        assertFalse(Updater.isDevFamily("8.4.0-beta64"))
        assertFalse(Updater.isDevFamily("8.4.0-r64"))
        assertFalse(Updater.isDevFamily("8.4.0-m64"))
        assertFalse(Updater.isDevFamily("8.4.0"))
    }

    @Test
    fun `isDevFamily recognizes the shared dated release tag form used for tag parsing`() {
        assertTrue(Updater.isDevFamily("v8.4.0-dev-20261013"))
        assertFalse(Updater.isDevFamily("v8.4.0-beta"))
        assertFalse(Updater.isDevFamily("v8.4.0"))
    }

    @Test
    fun `family helpers detect each channel`() {
        assertTrue(Updater.isMinifiedFamily("8.4.0-m64"))
        assertTrue(Updater.isMinifiedFamily("8.4.0-m32"))
        assertFalse(Updater.isMinifiedFamily("8.4.0-r32"))

        assertTrue(Updater.isBetaFamily("8.4.0-beta64"))
        assertTrue(Updater.isBetaFamily("8.4.0-beta32"))
        assertFalse(Updater.isBetaFamily("8.4.0-dev64-20261013"))
        assertFalse(Updater.isBetaFamily("8.4.0-r64"))
    }

    @Test
    fun `family helpers recognize the release tag forms used for tag parsing`() {
        assertTrue(Updater.isBetaFamily("v8.4.0-beta"))
        assertFalse(Updater.isBetaFamily("v8.4.0-dev-20261013"))
    }

    // endregion

    // region getBuildSuffix

    @Test
    fun `getBuildSuffix returns the per-ABI version suffix for the post-rename assets`() {
        Updater.build = build("N-Zik-release64.apk")
        assertEquals("-r64", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-release32.apk")
        assertEquals("-r32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-minified64.apk")
        assertEquals("-m64", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-minified32.apk")
        assertEquals("-m32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-beta64.apk")
        assertEquals("-beta64", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-beta32.apk")
        assertEquals("-beta32", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-dev64.apk")
        assertEquals("-dev64", Updater.getBuildSuffix())

        Updater.build = build("N-Zik-dev32.apk")
        assertEquals("-dev32", Updater.getBuildSuffix())
    }

    @Test
    fun `getBuildSuffix keeps the minified compatibility suffix`() {
        Updater.build = build("N-Zik-minified.apk")
        assertEquals("-m64", Updater.getBuildSuffix())
    }

    // endregion

    // region getDisplayVersion

    @Test
    fun `getDisplayVersion inserts the ABI marker into the shared dated dev tag`() {
        val build = build("N-Zik-dev64.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0-dev-20261013", build)

        assertEquals("v8.4.0-dev64-20261013", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion inserts the 32-bit marker into the shared dated dev tag`() {
        val build = build("N-Zik-dev32.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0-dev-20261013", build)

        assertEquals("v8.4.0-dev32-20261013", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion appends the ABI part to the shared beta tag`() {
        val build = build("N-Zik-beta64.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0-beta", build)

        assertEquals("v8.4.0-beta64", Updater.getDisplayVersion())

        val build32 = build("N-Zik-beta32.apk")
        Updater.build = build32
        assertEquals("v8.4.0-beta32", Updater.getDisplayVersion())
    }

    @Test
    fun `getDisplayVersion appends the full suffix to the stable tag`() {
        val build = build("N-Zik-release64.apk")
        Updater.build = build
        Updater.githubRelease = release("v8.4.0", build)

        assertEquals("v8.4.0-r64", Updater.getDisplayVersion())

        val build32 = build("N-Zik-release32.apk")
        Updater.build = build32
        assertEquals("v8.4.0-r32", Updater.getDisplayVersion())

        val minified32 = build("N-Zik-minified32.apk")
        Updater.build = minified32
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
    fun `findBestRelease picks the newest dev release for a dev64 user`() {
        // Dev releases ship both dev assets under the shared dated tag
        val older = release("v8.4.0-dev-20261012", build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))
        val newest = release("v8.4.0-dev-20261013", build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))
        val stable = release("v8.4.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        val best = Updater.findBestRelease(
            listOf(stable, newest, older),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev64-20261012"
        )

        assertEquals("v8.4.0-dev-20261013", best?.tagName)
    }

    @Test
    fun `findBestRelease picks the newest dev release for a dev32 user`() {
        val older = release("v8.4.0-dev-20261012", build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))
        val newest = release("v8.4.0-dev-20261013", build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))
        val stable = release("v8.4.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        val best = Updater.findBestRelease(
            listOf(stable, older, newest),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev32-20261012"
        )

        assertEquals("v8.4.0-dev-20261013", best?.tagName)
    }

    @Test
    fun `findBestRelease keeps the minified64 user on stable releases only`() {
        // Stable releases ship the minified64 asset
        val stable = release("v8.4.0", build("N-Zik-minified64.apk"))
        val beta = release("v8.4.0-beta", build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk"))
        val olderStable = release("v8.3.5", build("N-Zik-minified64.apk"))

        val best = Updater.findBestRelease(
            listOf(beta, stable, olderStable),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-m64"
        )

        assertEquals("v8.4.0", best?.tagName)
    }

    @Test
    fun `findBestRelease gives a release32 user the beta release when beta is enabled`() {
        // Ground truth: the beta release ships the beta32 asset, the stable one the release32 asset
        val stable = release("v8.4.0", build("N-Zik-release32.apk"))
        val beta = release("v8.4.1-beta", build("N-Zik-beta32.apk"))

        assertEquals(
            "v8.4.1-beta",
            Updater.findBestRelease(listOf(stable, beta), checkBetaUpdates = true, currentVersion = "8.4.0-r32")?.tagName
        )
        assertEquals(
            "v8.4.0",
            Updater.findBestRelease(listOf(stable, beta), checkBetaUpdates = false, currentVersion = "8.4.0-r32")?.tagName
        )
    }

    @Test
    fun `findBestRelease gives a beta32 user the beta release`() {
        val stable = release("v8.4.0", build("N-Zik-release32.apk"))
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
        val beta = release("v8.5.0-beta", build("N-Zik-beta64.apk"))
        val dev = release("v8.5.0-dev-20261013", build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))

        val best = Updater.findBestRelease(
            listOf(beta, dev),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-dev64-20261012"
        )

        assertEquals("v8.5.0-dev-20261013", best?.tagName)
    }

    @Test
    fun `findBestRelease skips a beta release that ships no 32-bit asset for a 32-bit user`() {
        // A beta release published without the 32-bit asset must be skipped —
        // the user falls back to the best stable release that ships release32
        val betaOnly64 = release("v8.5.0-beta", build("N-Zik-beta64.apk"))
        val stable = release("v8.5.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        val best = Updater.findBestRelease(
            listOf(betaOnly64, stable),
            checkBetaUpdates = true,
            currentVersion = "8.4.0-r32"
        )

        assertEquals("v8.5.0", best?.tagName)
    }

    @Test
    fun `findBestRelease returns null when no release ships an installable asset for a 32-bit user`() {
        val only64 = release("v8.5.0", build("N-Zik-release64.apk"))

        val best = Updater.findBestRelease(
            listOf(only64),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-r32"
        )

        assertNull(best)
    }

    @Test
    fun `findBestRelease returns null when no dev release ships the dev32 asset`() {
        val devOnly64 = release("v8.5.0-dev-20261013", build("N-Zik-dev64.apk"))

        val best = Updater.findBestRelease(
            listOf(devOnly64),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev32-20261012"
        )

        assertNull(best)
    }

    @Test
    fun `findBestRelease ignores stable and beta releases for a dev user`() {
        val stable = release("v8.4.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))
        val beta = release("v8.4.0-beta", build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk"))
        val dev = release("v8.3.9-dev-20260101", build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))

        val best = Updater.findBestRelease(
            listOf(stable, beta, dev),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-dev64-20260102"
        )

        assertEquals("v8.3.9-dev-20260101", best?.tagName)
    }

    @Test
    fun `findBestRelease skips an asset-less release for a known flavor`() {
        // A docs-only (asset-less) release must not win for a user with a
        // known flavor — the best release that actually ships an installable
        // APK is selected instead
        val assetlessNewest = release("v8.5.0")
        val stable = release("v8.4.0", build("N-Zik-release64.apk"))

        val best = Updater.findBestRelease(
            listOf(assetlessNewest, stable),
            checkBetaUpdates = false,
            currentVersion = "8.4.0-r64"
        )

        assertEquals("v8.4.0", best?.tagName)
    }

    @Test
    fun `findBestRelease offers no update to an unknown flavor user`() {
        // Unknown flavors target the non-existent "-unknown" tag, so no
        // release matches — a clean "no update" instead of a stable fallback
        val assetless = release("v8.5.0")
        val stable = release("v8.4.0", build("N-Zik-release64.apk"))

        assertNull(
            Updater.findBestRelease(
                listOf(assetless, stable),
                checkBetaUpdates = false,
                currentVersion = "8.4.0-debug"
            )
        )
    }

    @Test
    fun `findBestRelease does not fall back to stable releases for an unknown flavor`() {
        val beta = release("v8.5.0-beta", build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk"))
        val stable = release("v8.5.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        assertNull(
            Updater.findBestRelease(
                listOf(beta, stable),
                checkBetaUpdates = false,
                currentVersion = "8.4.0-gamma64"
            )
        )
    }

    @Test
    fun `findBestRelease rejects releases shipping only full and full32 assets for both ABIs`() {
        val legacy = release("v8.5.0", build("N-Zik-full.apk"), build("N-Zik-full32.apk"))

        for (abi in listOf("64", "32")) {
            for (checkBeta in listOf(false, true)) {
                assertNull(Updater.findBestRelease(listOf(legacy), checkBetaUpdates = checkBeta, currentVersion = "8.4.0-r$abi"))
            }
        }
    }

    @Test
    fun `findBestRelease skips newer legacy-only releases for older compatible modern releases`() {
        val legacy = release("v8.6.0", build("N-Zik-full.apk"), build("N-Zik-full32.apk"))
        val modern = release("v8.5.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        for (abi in listOf("64", "32")) {
            val best = Updater.findBestRelease(listOf(legacy, modern), checkBetaUpdates = false, currentVersion = "8.4.0-r$abi")
            assertEquals(modern, best)
            assertEquals("N-Zik-release$abi.apk", Updater.extractBuild(requireNotNull(best).builds, currentVersion = "8.4.0-r$abi").name)
        }
    }

    @Test
    fun `beta-disabled release and beta users cannot select legacy-only stable assets`() {
        val legacy = release("v8.6.0", build("N-Zik-full.apk"), build("N-Zik-full32.apk"))
        val beta = release("v8.7.0-beta", build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk"))
        val modern = release("v8.5.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        for (abi in listOf("64", "32")) {
            for (flavor in listOf("r$abi", "beta$abi")) {
                val version = "8.4.0-$flavor"
                assertNull(Updater.findBestRelease(listOf(legacy, beta), checkBetaUpdates = false, currentVersion = version))
                assertThrows(NoSuchFileException::class.java) {
                    Updater.extractBuild(legacy.builds, checkBetaUpdates = false, currentVersion = version)
                }
                val best = Updater.findBestRelease(listOf(legacy, beta, modern), checkBetaUpdates = false, currentVersion = version)
                assertEquals(modern, best)
                assertEquals("N-Zik-release$abi.apk", Updater.extractBuild(requireNotNull(best).builds, checkBetaUpdates = false, currentVersion = version).name)
            }
        }
    }

    @Test
    fun `beta-enabled release and beta users select modern beta assets for their ABI`() {
        val legacy = release("v8.7.0", build("N-Zik-full.apk"), build("N-Zik-full32.apk"))
        val beta = release("v8.6.0-beta", build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk"))
        val modern = release("v8.5.0", build("N-Zik-release64.apk"), build("N-Zik-release32.apk"))

        for (abi in listOf("64", "32")) {
            for (flavor in listOf("r$abi", "beta$abi")) {
                val version = "8.4.0-$flavor"
                val best = Updater.findBestRelease(listOf(legacy, modern, beta), checkBetaUpdates = true, currentVersion = version)
                assertEquals(beta, best)
                assertEquals("N-Zik-beta$abi.apk", Updater.extractBuild(requireNotNull(best).builds, checkBetaUpdates = true, currentVersion = version).name)
            }
        }
    }

    @Test
    fun `minified64 users can still select and extract legacy minified assets`() {
        val legacy = release("v8.5.0", build("N-Zik-minified.apk"))

        for (checkBeta in listOf(false, true)) {
            val best = Updater.findBestRelease(listOf(legacy), checkBetaUpdates = checkBeta, currentVersion = "8.4.0-m64")
            assertEquals(legacy, best)
            assertEquals("N-Zik-minified.apk", Updater.extractBuild(requireNotNull(best).builds, checkBetaUpdates = checkBeta, currentVersion = "8.4.0-m64").name)
        }
        assertNull(Updater.findBestRelease(listOf(legacy), checkBetaUpdates = false, currentVersion = "8.4.0-m32"))
        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(legacy.builds, currentVersion = "8.4.0-m32")
        }
    }

    // endregion

    // region targetBuildTypes

    @Test
    fun `targetBuildTypes offers the beta and release assets for release users when beta is enabled`() {
        assertEquals(
            listOf("beta64", "release64"),
            Updater.targetBuildTypes("8.4.0-r64", checkBetaUpdates = true)
        )
        assertEquals(
            listOf("beta32", "release32"),
            Updater.targetBuildTypes("8.4.0-r32", checkBetaUpdates = true)
        )
    }

    @Test
    fun `targetBuildTypes keeps the single channel for non-beta users`() {
        assertEquals(listOf("release64"), Updater.targetBuildTypes("8.4.0-r64", checkBetaUpdates = false))
        assertEquals(listOf("release32"), Updater.targetBuildTypes("8.4.0-r32", checkBetaUpdates = false))
        // A beta user with beta disabled is served release assets only
        assertEquals(listOf("release64"), Updater.targetBuildTypes("8.4.0-beta64", checkBetaUpdates = false))
        assertEquals(listOf("release32"), Updater.targetBuildTypes("8.4.0-beta32", checkBetaUpdates = false))
        // Minified users never get beta, regardless of the flag
        assertEquals(listOf("minified64"), Updater.targetBuildTypes("8.4.0-m64", checkBetaUpdates = true))
        assertEquals(listOf("minified32"), Updater.targetBuildTypes("8.4.0-m32", checkBetaUpdates = true))
    }

    @Test
    fun `targetBuildTypes ignores the beta flag for dev family users`() {
        assertEquals(listOf("dev64"), Updater.targetBuildTypes("8.4.0-dev64-20261013", checkBetaUpdates = true))
        assertEquals(listOf("dev64"), Updater.targetBuildTypes("8.4.0-dev64-20261013", checkBetaUpdates = false))
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
        val assets = listOf(build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))

        val chosen = Updater.extractBuild(assets, currentVersion = "8.4.0-dev32-20261012")

        assertEquals("N-Zik-dev32.apk", chosen.name)
    }

    @Test
    fun `extractBuild gives the dev64 user the dev64 asset`() {
        val assets = listOf(build("N-Zik-dev64.apk"), build("N-Zik-dev32.apk"))

        val chosen = Updater.extractBuild(assets, currentVersion = "8.4.0-dev64-20261012")

        assertEquals("N-Zik-dev64.apk", chosen.name)
    }

    @Test
    fun `extractBuild keeps the stable flavor selection`() {
        val assets = listOf(build("N-Zik-beta64.apk"), build("N-Zik-release64.apk"), build("N-Zik-minified64.apk"))

        assertEquals("N-Zik-release64.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-r64").name)
        // beta user with the beta channel enabled gets the beta asset; disabled they get release
        assertEquals("N-Zik-beta64.apk", Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-beta64").name)
        assertEquals("N-Zik-release64.apk", Updater.extractBuild(assets, checkBetaUpdates = false, currentVersion = "8.4.0-beta64").name)
        assertEquals("N-Zik-minified64.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-m64").name)
    }

    @Test
    fun `extractBuild gives 32-bit users the 32-bit assets`() {
        val assets = listOf(
            build("N-Zik-release64.apk"), build("N-Zik-release32.apk"),
            build("N-Zik-minified64.apk"), build("N-Zik-minified32.apk"),
            build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk")
        )

        assertEquals("N-Zik-release32.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-r32").name)
        assertEquals("N-Zik-minified32.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-m32").name)
        assertEquals("N-Zik-beta32.apk", Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-beta32").name)
        // beta32 with beta disabled falls back to the 32-bit release asset
        assertEquals("N-Zik-release32.apk", Updater.extractBuild(assets, checkBetaUpdates = false, currentVersion = "8.4.0-beta32").name)
    }

    @Test
    fun `extractBuild gives the release32 user the beta32 asset when beta is enabled`() {
        val assets = listOf(
            build("N-Zik-release64.apk"), build("N-Zik-release32.apk"),
            build("N-Zik-beta64.apk"), build("N-Zik-beta32.apk")
        )

        assertEquals("N-Zik-beta32.apk", Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-r32").name)
        assertEquals("N-Zik-release32.apk", Updater.extractBuild(assets, checkBetaUpdates = false, currentVersion = "8.4.0-r32").name)
    }

    @Test
    fun `extractBuild refuses a 64-bit fallback for 32-bit users`() {
        // A release without any 32-bit asset: a 64-bit APK must never be
        // offered to a 32-bit-only device — "no update" is the correct outcome
        val assets = listOf(build("N-Zik-release64.apk"), build("N-Zik-beta64.apk"))

        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(assets, currentVersion = "8.4.0-r32")
        }
        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(assets, checkBetaUpdates = true, currentVersion = "8.4.0-beta32")
        }
    }

    @Test
    fun `extractBuild refuses a 64-bit fallback for dev32 and minified32 users`() {
        val devOnly64 = listOf(build("N-Zik-dev64.apk"))
        val minifiedOnly64 = listOf(build("N-Zik-minified64.apk"))

        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(devOnly64, currentVersion = "8.4.0-dev32-20261012")
        }
        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(minifiedOnly64, currentVersion = "8.4.0-m32")
        }
    }

    @Test
    fun `extractBuild refuses any base-asset fallback for an unknown flavor`() {
        // Unknown flavors target the non-existent "-unknown" tag — even a
        // release carrying the base asset must not be offered to them
        val assets = listOf(build("N-Zik-release64.apk"), build("N-Zik-beta64.apk"))

        assertThrows(NoSuchFileException::class.java) {
            Updater.extractBuild(assets, currentVersion = "8.4.0-debug")
        }
    }

    @Test
    fun `modern assets win over full and full32 assets in mixed releases for both ABIs`() {
        val mixed = release(
            "v8.5.0",
            build("N-Zik-full.apk"), build("N-Zik-full32.apk"),
            build("N-Zik-release64.apk"), build("N-Zik-release32.apk")
        )

        for (abi in listOf("64", "32")) {
            val version = "8.4.0-r$abi"
            assertEquals(mixed, Updater.findBestRelease(listOf(mixed), checkBetaUpdates = false, currentVersion = version))
            assertEquals("N-Zik-release$abi.apk", Updater.extractBuild(mixed.builds, currentVersion = version).name)
        }
    }

    @Test
    fun `extractBuild prefers modern minified64 assets over the retained alias`() {
        val assets = listOf(build("N-Zik-minified.apk"), build("N-Zik-minified64.apk"))

        assertEquals("N-Zik-minified64.apk", Updater.extractBuild(assets, currentVersion = "8.4.0-m64").name)
    }

    @Test
    fun `extractBuild rejects full and full32 assets for both release ABIs`() {
        val assets = listOf(build("N-Zik-full.apk"), build("N-Zik-full32.apk"))

        for (abi in listOf("64", "32")) {
            for (checkBeta in listOf(false, true)) {
                assertThrows(NoSuchFileException::class.java) {
                    Updater.extractBuild(assets, checkBetaUpdates = checkBeta, currentVersion = "8.4.0-r$abi")
                }
            }
        }
    }

    // endregion

    // region currentChangelogUrl

    @Test
    fun `currentChangelogUrl serves the dev changelog file for the dev64 and dev32 flavors`() {
        assertEquals(
            "${UpdateEndpoints.CHANGELOGS_URL}/dev.txt",
            Updater.currentChangelogUrl(123, "8.4.0-dev64-20261013")
        )
        assertEquals(
            "${UpdateEndpoints.CHANGELOGS_URL}/dev.txt",
            Updater.currentChangelogUrl(123, "8.4.0-dev32-20261013")
        )
    }

    @Test
    fun `currentChangelogUrl serves the version-code file for the other flavors`() {
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-r64"))
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-r32"))
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-beta64"))
        assertEquals("${UpdateEndpoints.CHANGELOGS_URL}/123.txt", Updater.currentChangelogUrl(123, "8.4.0-m32"))
    }

    // endregion

    // region isVersionNewer

    @Test
    fun `isVersionNewer compares dev dates when the base version matches`() {
        assertTrue(Updater.isVersionNewer("v8.4.0-dev-20261013", "8.4.0-dev64-20261012"))
        assertFalse(Updater.isVersionNewer("v8.4.0-dev-20261012", "8.4.0-dev64-20261013"))
    }

    @Test
    fun `isVersionNewer prefers the higher base version`() {
        assertTrue(Updater.isVersionNewer("v8.4.0", "8.3.9-r64"))
        assertFalse(Updater.isVersionNewer("v8.3.9", "8.4.0-r64"))
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
