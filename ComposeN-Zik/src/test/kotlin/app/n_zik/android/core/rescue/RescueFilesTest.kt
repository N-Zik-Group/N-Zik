package app.n_zik.android.core.rescue

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.extensions.discord.discordAdvancedShowStateKey
import app.n_zik.android.extensions.discord.discordAdvancedStateTemplateKey
import app.n_zik.android.extensions.discord.discordAdvancedSettingKeys
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.proxyPasswordEncryptedKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Unit tests for the parts of [RescueFiles] that do not need the Android framework.
 *
 * Anything needing a real [android.content.Context], [android.net.Uri] or
 * [android.database.sqlite.SQLiteDatabase] is out of scope for pure JUnit5 tests. This file covers:
 * SQLite header validation, the settings CSV round trip, encrypted key routing, and the
 * file-based cores of the destructive actions (clear cache, delete downloads) on temp dirs.
 */
class RescueFilesTest {

    /**
     * File names are profile-aware (Profiles feature), so the file-based
     * operations require [RescueFiles.initialize] first. It is one-shot per JVM
     * (no reset hook): the first test class to call it pins the profile for the
     * whole run, so the default profile is pinned here with a mock context whose
     * profile preference is empty. The exact keys (not `any()`) are stubbed so a
     * renamed preference key or file name fails loudly instead of silently
     * falling back. No other test class initializes [RescueFiles] yet — if one
     * ever does with a non-default profile, the file-based expectations in this
     * suite must be revisited.
     */
    @BeforeEach
    fun initializeProfile() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(activeProfileKey, "default") } returns null
        val context = mockk<Context>()
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns prefs
        RescueFiles.initialize(context)
    }

    // ──────────────────────────────────────────────────────────────────────
    // SQLite header validation
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `valid SQLite header is accepted`() {
        val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        assertTrue(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `header with extra bytes after magic is accepted`() {
        val header = "SQLite format 3\u0000EXTRA_DATA_HERE".toByteArray(Charsets.US_ASCII)
        assertTrue(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `non-SQLite header is rejected`() {
        val header = "This is not SQL".toByteArray(Charsets.US_ASCII)
        assertFalse(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `empty header is rejected`() {
        assertFalse(RescueFiles.isValidSqliteHeader(ByteArray(0)))
    }

    @Test
    fun `short header is rejected`() {
        val header = "SQLite".toByteArray(Charsets.US_ASCII)
        assertFalse(RescueFiles.isValidSqliteHeader(header))
    }

    @Test
    fun `random binary data is rejected`() {
        val header = ByteArray(16) { (it * 37).toByte() }
        assertFalse(RescueFiles.isValidSqliteHeader(header))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Settings CSV round trip (writer + reader used by export/import)
    // ──────────────────────────────────────────────────────────────────────

    private fun roundTrip(entries: List<Triple<String, String, Any>>): List<Triple<String, String, String>> {
        val out = ByteArrayOutputStream()
        RescueFiles.writeSettingsCsv(out, entries)
        return RescueFiles.readSettingsCsv(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `plain entries round trip`() {
        val result = roundTrip(
            listOf(
                Triple("String", "language", "en"),
                Triple("Int", "count", 42),
                Triple("Boolean", "flag", true)
            )
        )
        assertEquals(
            listOf(
                Triple("String", "language", "en"),
                Triple("Int", "count", "42"),
                Triple("Boolean", "flag", "true")
            ),
            result
        )
    }

    @Test
    fun `values with comma quote and line break round trip`() {
        // Multi-line values are exactly what a line-by-line parser breaks on.
        val original = "a \"complex\", value\nwith newline"
        val result = roundTrip(listOf(Triple("String", "key", original)))
        assertEquals(listOf(Triple("String", "key", original)), result)
    }

    @Test
    fun `empty value is preserved`() {
        val result = roundTrip(listOf(Triple("String", "key", "")))
        assertEquals(listOf(Triple("String", "key", "")), result)
    }

    @Test
    fun `header only file yields no rows`() {
        val rows = RescueFiles.readSettingsCsv(ByteArrayInputStream("Type,Key,Value\r\n".toByteArray()))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `rows without a key are dropped`() {
        val csv = "Type,Key,Value\r\nString,,orphan\r\nString,kept,v\r\n"
        val rows = RescueFiles.readSettingsCsv(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(listOf(Triple("String", "kept", "v")), rows)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Credential export assembly (shared by the manual export, auto backup, rescue)
    // ──────────────────────────────────────────────────────────────────────

    private val sparseCredentialPrefs: Map<String, Any> = mapOf(
        lastfmSessionKey to "session123",
        lastfmUsernameKey to "NEVARLeVrai",
        lastfmAvatarUrlKey to "https://s.example/avatar.jpg",
        isLastfmScrobblingEnabledKey to true,
        isLastfmNowPlayingEnabledKey to true,
        isLastfmScrobbleEnabledKey to true,
        lastfmMinTrackDurationSecondsKey to 30,
        lastfmScrobbleThresholdPercentKey to 50,
        lastfmMaxScrobbleDelaySecondsKey to 50,
        discordPersonalAccessTokenKey to "discord-token"
    )

    @Test
    fun `lastfm credentials are exported only when includeLastfm is true`() {
        val without = RescueFiles.buildCredentialExport(sparseCredentialPrefs, false, false, false)
        val with = RescueFiles.buildCredentialExport(sparseCredentialPrefs, false, false, true)

        assertTrue(without.isEmpty())
        assertEquals(
            setOf(
                lastfmSessionKey, lastfmUsernameKey, lastfmAvatarUrlKey, isLastfmScrobblingEnabledKey,
                isLastfmNowPlayingEnabledKey, isLastfmScrobbleEnabledKey, lastfmMinTrackDurationSecondsKey,
                lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey
            ),
            with.map { it.second }.toSet()
        )
    }

    @Test
    fun `each credential group is independent of the others`() {
        val discordOnly = RescueFiles.buildCredentialExport(sparseCredentialPrefs, false, true, false)

        assertEquals(1, discordOnly.size)
        assertEquals(discordPersonalAccessTokenKey, discordOnly.single().second)
    }

    @Test
    fun `entries carry the value type name and the raw value`() {
        val with = RescueFiles.buildCredentialExport(sparseCredentialPrefs, false, false, true)
        val session = with.first { it.second == lastfmSessionKey }

        assertEquals("String", session.first)
        assertEquals("session123", session.third)
    }

    @Test
    fun `missing credentials are skipped without failing the export`() {
        val with = RescueFiles.buildCredentialExport(
            mapOf(lastfmSessionKey to "session123"),
            false,
            false,
            true
        )

        assertEquals(1, with.size)
        assertEquals(lastfmSessionKey, with.single().second)
    }

    @Test
    fun `each selection exports exactly its own group and the proxy only when its box is checked`() {
        val allKeys: Map<String, Any> = RescueFiles.ALL_ENCRYPTED_KEYS.associateWith { "v" }

        val ytb = RescueFiles.buildCredentialExport(allKeys, true, false, false).map { it.second }.toSet()
        assertEquals(RescueFiles.YTB_KEYS.toSet(), ytb)

        val discord = RescueFiles.buildCredentialExport(allKeys, false, true, false).map { it.second }.toSet()
        assertEquals(RescueFiles.DISCORD_KEYS.toSet(), discord)

        val lastfm = RescueFiles.buildCredentialExport(allKeys, false, false, true).map { it.second }.toSet()
        assertEquals(RescueFiles.LASTFM_KEYS.toSet(), lastfm)

        val ytbWithProxy = RescueFiles.buildCredentialExport(allKeys, true, false, false, true).map { it.second }.toSet()
        assertEquals(RescueFiles.YTB_KEYS.toSet() + RescueFiles.PROXY_KEYS.toSet(), ytbWithProxy)

        val proxyOnly = RescueFiles.buildCredentialExport(allKeys, false, false, false, true).map { it.second }.toSet()
        assertEquals(RescueFiles.PROXY_KEYS.toSet(), proxyOnly)

        val all = RescueFiles.buildCredentialExport(allKeys, true, true, true, true).map { it.second }.toSet()
        assertEquals(RescueFiles.ALL_ENCRYPTED_KEYS.toSet(), all)
    }

    @Test
    fun `every on off combination of the credential selection exports exactly those groups`() {
        val allKeys: Map<String, Any> = RescueFiles.ALL_ENCRYPTED_KEYS.associateWith { "v" }

        for (ytb in listOf(false, true)) {
            for (discord in listOf(false, true)) {
                for (lastfm in listOf(false, true)) {
                    for (proxy in listOf(false, true)) {
                        val expected = (
                            (if (ytb) RescueFiles.YTB_KEYS else emptyList()) +
                            (if (discord) RescueFiles.DISCORD_KEYS else emptyList()) +
                            (if (lastfm) RescueFiles.LASTFM_KEYS else emptyList()) +
                            (if (proxy) RescueFiles.PROXY_KEYS else emptyList())
                            ).toSet()
                        val got = RescueFiles.buildCredentialExport(allKeys, ytb, discord, lastfm, proxy).map { it.second }.toSet()
                        assertEquals(expected, got, "selection ytb=$ytb discord=$discord lastfm=$lastfm proxy=$proxy")
                    }
                }
            }
        }
    }

    @Test
    fun `import restores every plain key and every encrypted key to its own store`() {
        val rows: List<Triple<String, String, Any>> = listOf(
            Triple<String, String, Any>("String", "languageApp", "en"),
            Triple<String, String, Any>("Int", "downloadQuality", 2),
            Triple<String, String, Any>("Long", "lastSyncMillis", 1_729_000_000_000L),
            Triple<String, String, Any>("Float", "playbackSpeed", 1.5),
            Triple<String, String, Any>("Boolean", "persistentQueue", true)
        ) + RescueFiles.ALL_ENCRYPTED_KEYS.map { key -> Triple<String, String, Any>("String", key, "credential") }

        val csvRows = roundTrip(rows)
        assertEquals(rows.size, csvRows.size)

        val plain = editor()
        val encrypted = editor()
        val stats = RescueFiles.applySettingRows(csvRows, plain, encrypted)

        assertEquals(
            RescueFiles.SettingsImportStats(imported = rows.size, encryptedSkipped = 0),
            stats
        )
        verify { plain.putString("languageApp", "en") }
        verify { plain.putInt("downloadQuality", 2) }
        verify { plain.putLong("lastSyncMillis", 1_729_000_000_000L) }
        verify { plain.putFloat("playbackSpeed", 1.5f) }
        verify { plain.putBoolean("persistentQueue", true) }
        verify(exactly = 1) { plain.putString(any(), any()) }
        verify(exactly = 1) { plain.putInt(any(), any()) }
        verify(exactly = 1) { plain.putLong(any(), any()) }
        verify(exactly = 1) { plain.putFloat(any(), any()) }
        verify(exactly = 1) { plain.putBoolean(any(), any()) }
        verify(exactly = RescueFiles.ALL_ENCRYPTED_KEYS.size) { encrypted.putString(any(), any()) }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Encrypted key routing
    // ──────────────────────────────────────────────────────────────────────

    private val allKeysMap: Map<String, Any?> = RescueFiles.ALL_ENCRYPTED_KEYS.associateWith { "x" }

    private fun credentialKeys(ytb: Boolean, discord: Boolean, lastfm: Boolean, includeProxy: Boolean = false): Set<String> =
        RescueFiles.buildCredentialExport(allKeysMap, ytb, discord, lastfm, includeProxy)
            .map { it.second }
            .toSet()

    @Test
    fun `YouTube keys match the regular settings export`() {
        assertEquals(RescueFiles.YTB_KEYS.toSet(), credentialKeys(ytb = true, discord = false, lastfm = false))
    }

    @Test
    fun `Discord keys match the regular settings export`() {
        assertEquals(RescueFiles.DISCORD_KEYS.toSet(), credentialKeys(ytb = false, discord = true, lastfm = false))
    }

    @Test
    fun `Discord keys cover every advanced presence setting`() {
        assertTrue(
            discordAdvancedSettingKeys.all { it in RescueFiles.DISCORD_KEYS },
            "every advanced Discord setting must be exported and import-routed with the credentials"
        )
    }

    @Test
    fun `Discord keys still carry the legacy section keys`() {
        val legacyKeys = listOf(
            discordPersonalAccessTokenKey, discordAvatarKey, discordUsernameKey,
            isDiscordPresenceEnabledKey, isDiscordBrowsingEnabledKey
        )
        assertTrue(
            legacyKeys.all { it in RescueFiles.DISCORD_KEYS },
            "the legacy Discord section keys must remain exported"
        )
    }

    @Test
    fun `Last fm keys match the regular settings export`() {
        assertEquals(RescueFiles.LASTFM_KEYS.toSet(), credentialKeys(ytb = false, discord = false, lastfm = true))
    }

    @Test
    fun `Last fm session key is treated as encrypted`() {
        assertTrue(lastfmSessionKey in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `normal preference keys are not encrypted`() {
        assertFalse("languageApp" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertFalse("persistentQueue" in RescueFiles.ALL_ENCRYPTED_KEYS)
        assertFalse("skipSilence" in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `YTB DISCORD and LASTFM key lists are disjoint`() {
        val ytb = RescueFiles.YTB_KEYS.toSet()
        val discord = RescueFiles.DISCORD_KEYS.toSet()
        val lastfm = RescueFiles.LASTFM_KEYS.toSet()

        assertTrue((ytb intersect discord).isEmpty(), "YTB and Discord keys overlap")
        assertTrue((ytb intersect lastfm).isEmpty(), "YTB and Last.fm keys overlap")
        assertTrue((discord intersect lastfm).isEmpty(), "Discord and Last.fm keys overlap")
    }

    @Test
    fun `ALL_ENCRYPTED_KEYS is the union of the credential groups and the app-owned encrypted settings`() {
        val expected = (
            RescueFiles.YTB_KEYS + RescueFiles.DISCORD_KEYS + RescueFiles.LASTFM_KEYS + RescueFiles.PROXY_KEYS
            ).toSet()
        assertEquals(expected, RescueFiles.ALL_ENCRYPTED_KEYS.toSet())
    }

    @Test
    fun `the proxy password key is treated as encrypted`() {
        assertTrue(proxyPasswordEncryptedKey in RescueFiles.ALL_ENCRYPTED_KEYS)
    }

    @Test
    fun `buildProxyEntries exports the password when set and is empty otherwise`() {
        val withPassword = RescueFiles.buildProxyEntries(mapOf(proxyPasswordEncryptedKey to "s3cret"))
        assertEquals(listOf(Triple("String", proxyPasswordEncryptedKey, "s3cret")), withPassword)

        val empty = RescueFiles.buildProxyEntries(mapOf(proxyPasswordEncryptedKey to null))
        assertTrue(empty.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Clear cache / delete downloads (file-based cores)
    // ──────────────────────────────────────────────────────────────────────

    private fun File.dirWithFile(name: String): File = apply {
        mkdirs()
        File(this, name).writeText("x")
    }

    @Test
    fun `clear cache removes caches but never downloaded media`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "cache").apply { mkdirs() }
        val filesDir = File(tmp, "files").apply { mkdirs() }
        val externalCacheDir = File(tmp, "external").apply { mkdirs() }

        // Caches that must go, in both possible base dirs
        File(cacheDir, "exoplayer").dirWithFile("a.bin")
        File(cacheDir, "coil").dirWithFile("img.bin")
        File(filesDir, "exoplayer").dirWithFile("a.bin")
        File(filesDir, "coil").dirWithFile("img.bin")
        File(externalCacheDir, "http_cache").dirWithFile("resp.bin")
        File(cacheDir, "temp_123.tmp").writeText("x")

        // Downloaded media that must survive, wherever it lives
        File(cacheDir, "exo_downloads").dirWithFile("song.bin")
        File(filesDir, "exo_downloads").dirWithFile("song.bin")
        File(externalCacheDir, "exo_downloads").dirWithFile("song.bin")

        // Unrelated persistent file
        File(filesDir, "persistentQueue.data").writeText("keep")

        RescueFiles.clearCacheDirs(cacheDir, filesDir, externalCacheDir)

        assertFalse(File(cacheDir, "exoplayer").exists())
        assertFalse(File(cacheDir, "coil").exists())
        assertFalse(File(filesDir, "exoplayer").exists())
        assertFalse(File(filesDir, "coil").exists())
        assertFalse(File(externalCacheDir, "http_cache").exists())
        assertFalse(File(cacheDir, "temp_123.tmp").exists())

        assertTrue(File(cacheDir, "exo_downloads/song.bin").exists())
        assertTrue(File(filesDir, "exo_downloads/song.bin").exists())
        assertTrue(File(externalCacheDir, "exo_downloads/song.bin").exists())
        assertTrue(File(filesDir, "persistentQueue.data").exists())
    }

    @Test
    fun `clear cache tolerates missing directories`(@TempDir tmp: File) {
        val deleted = RescueFiles.clearCacheDirs(File(tmp, "nope1"), File(tmp, "nope2"), null)
        assertEquals(0, deleted)
    }

    @Test
    fun `delete downloads removes media and the index database from the databases dir`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "cache").apply { mkdirs() }
        val filesDir = File(tmp, "files").apply { mkdirs() }
        val databasesDir = File(tmp, "databases").apply { mkdirs() }

        File(cacheDir, "exo_downloads").dirWithFile("song.bin")
        File(filesDir, "exo_downloads").dirWithFile("song.bin")
        val index = File(databasesDir, "exoplayer_internal.db").apply { writeText("x") }
        val indexJournal = File(databasesDir, "exoplayer_internal.db-journal").apply { writeText("x") }

        // Must survive: the app database and the streaming cache
        val appDb = File(databasesDir, "data.db").apply { writeText("keep") }
        File(cacheDir, "exoplayer").dirWithFile("stream.bin")

        RescueFiles.deleteDownloadFiles(listOf(cacheDir, filesDir), index)

        assertFalse(File(cacheDir, "exo_downloads").exists())
        assertFalse(File(filesDir, "exo_downloads").exists())
        assertFalse(index.exists(), "download index must be deleted with the media")
        assertFalse(indexJournal.exists())
        assertTrue(appDb.exists())
        assertTrue(File(cacheDir, "exoplayer/stream.bin").exists())
    }

    @Test
    fun `clear cache removes the separate profile caches as well`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "cache").apply { mkdirs() }
        val filesDir = File(tmp, "files").apply { mkdirs() }

        // The SEPARATE caches of the user profiles (their location setting picks the base dir)
        File(cacheDir, "exoplayer_work").dirWithFile("stream.bin")
        File(cacheDir, "coil_work").dirWithFile("img.bin")
        File(filesDir, "exoplayer_home").dirWithFile("stream.bin")
        // The base caches (the shared profile caches map onto these)
        File(cacheDir, "exoplayer").dirWithFile("stream.bin")
        File(cacheDir, "coil").dirWithFile("img.bin")
        // Must survive: the base AND the profile's downloads, wherever they live
        File(cacheDir, "exo_downloads").dirWithFile("song.bin")
        File(cacheDir, "exo_downloads_work").dirWithFile("song.bin")

        RescueFiles.clearCacheDirs(
            cacheDir,
            filesDir,
            null,
            listOf(File(cacheDir, "exoplayer_work"), File(cacheDir, "coil_work"), File(filesDir, "exoplayer_home")),
        )

        assertFalse(File(cacheDir, "exoplayer_work").exists())
        assertFalse(File(cacheDir, "coil_work").exists())
        assertFalse(File(filesDir, "exoplayer_home").exists())
        // The base caches are cleared too
        assertFalse(File(cacheDir, "exoplayer").exists())
        assertFalse(File(cacheDir, "coil").exists())
        // Downloads are never cleared by a cache clear — neither the base's nor a profile's
        assertTrue(File(cacheDir, "exo_downloads/song.bin").exists())
        assertTrue(File(cacheDir, "exo_downloads_work/song.bin").exists())
    }

    @Test
    fun `delete downloads removes the separate profile download dirs and their indexes`(@TempDir tmp: File) {
        val cacheDir = File(tmp, "cache").apply { mkdirs() }
        val filesDir = File(tmp, "files").apply { mkdirs() }
        val databasesDir = File(tmp, "databases").apply { mkdirs() }

        File(cacheDir, "exo_downloads").dirWithFile("song.bin")
        File(filesDir, "exo_downloads_work").dirWithFile("song.bin")
        File(cacheDir, "exo_downloads_home").dirWithFile("song.bin")
        val baseIndex = File(databasesDir, "exoplayer_internal.db").apply { writeText("x") }
        val workIndex = File(databasesDir, "exoplayer_internal_work.db").apply { writeText("x") }
        val workIndexWal = File(databasesDir, "exoplayer_internal_work.db-wal").apply { writeText("x") }
        val homeIndex = File(databasesDir, "exoplayer_internal_home.db").apply { writeText("x") }

        // Must survive: the app database and the streaming caches
        val appDb = File(databasesDir, "data.db").apply { writeText("keep") }
        File(cacheDir, "exoplayer").dirWithFile("stream.bin")
        File(cacheDir, "exoplayer_work").dirWithFile("stream.bin")

        RescueFiles.deleteDownloadFiles(
            mediaBases = listOf(cacheDir, filesDir),
            downloadDatabase = baseIndex,
            profileDownloadDirs = listOf(File(filesDir, "exo_downloads_work"), File(cacheDir, "exo_downloads_home")),
            profileDownloadIndexDatabases = listOf(workIndex, homeIndex),
        )

        // The base media + index go
        assertFalse(File(cacheDir, "exo_downloads").exists())
        assertFalse(baseIndex.exists())
        // The SEPARATE profile media + indexes go too (side files included)
        assertFalse(File(filesDir, "exo_downloads_work").exists())
        assertFalse(File(cacheDir, "exo_downloads_home").exists())
        assertFalse(workIndex.exists())
        assertFalse(workIndexWal.exists())
        assertFalse(homeIndex.exists())
        // The app database and the streaming caches survive
        assertTrue(appDb.exists())
        assertTrue(File(cacheDir, "exoplayer/stream.bin").exists())
        assertTrue(File(cacheDir, "exoplayer_work/stream.bin").exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Database files: reset / restore / import
    // ──────────────────────────────────────────────────────────────────────

    /** Writes a database main file, plus the side files that are given, into [dir]. */
    private fun writeDb(dir: File, main: String, wal: String? = null, shm: String? = null): File {
        dir.mkdirs()
        val db = File(dir, "data.db").apply { writeText(main) }
        wal?.let { File(dir, "data.db-wal").writeText(it) }
        shm?.let { File(dir, "data.db-shm").writeText(it) }
        return db
    }

    @Test
    fun `moving a database takes its wal and shm along and leaves no stale side file behind`(@TempDir tmp: File) {
        val live = writeDb(File(tmp, "databases"), main = "live-main", wal = "live-wal", shm = "live-shm")
        val backup = writeDb(File(tmp, "backups"), main = "old-main", wal = "old-wal")
        File(tmp, "backups/data.db-journal").writeText("stale journal of the old backup")

        RescueFiles.moveDatabaseFiles(live, backup)

        assertEquals("live-main", backup.readText())
        assertEquals("live-wal", File(tmp, "backups/data.db-wal").readText())
        assertEquals("live-shm", File(tmp, "backups/data.db-shm").readText())
        assertFalse(File(tmp, "backups/data.db-journal").exists(), "old backup's journal must not survive")
        assertFalse(live.exists())
        assertFalse(File(tmp, "databases/data.db-wal").exists())
        assertFalse(File(tmp, "databases/data.db-shm").exists())
    }

    @Test
    fun `swapping databases exchanges them completely and swapping again restores the original`(@TempDir tmp: File) {
        val live = writeDb(File(tmp, "databases"), main = "A-main", wal = "A-wal")
        val backup = writeDb(File(tmp, "backups"), main = "B-main", shm = "B-shm")

        RescueFiles.swapDatabaseFiles(live, backup)

        assertEquals("B-main", live.readText())
        assertEquals("B-shm", File(tmp, "databases/data.db-shm").readText())
        assertFalse(File(tmp, "databases/data.db-wal").exists())
        assertEquals("A-main", backup.readText())
        assertEquals("A-wal", File(tmp, "backups/data.db-wal").readText())
        assertFalse(File(tmp, "backups/data.db-shm").exists())
        assertFalse(File(tmp, "backups/data.db.swap").exists(), "parking file must be gone")

        RescueFiles.swapDatabaseFiles(live, backup)

        assertEquals("A-main", live.readText())
        assertEquals("A-wal", File(tmp, "databases/data.db-wal").readText())
        assertEquals("B-main", backup.readText())
        assertEquals("B-shm", File(tmp, "backups/data.db-shm").readText())
    }

    @Test
    fun `swapping when there is no live database just brings the backup back`(@TempDir tmp: File) {
        val live = File(tmp, "databases/data.db").apply { parentFile.mkdirs() }
        val backup = writeDb(File(tmp, "backups"), main = "B-main")

        RescueFiles.swapDatabaseFiles(live, backup)

        assertEquals("B-main", live.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun `replacing the database removes the stale side files of the old one`(@TempDir tmp: File) {
        val live = writeDb(File(tmp, "databases"), main = "old", wal = "old-wal", shm = "old-shm")
        val imported = File(tmp, "databases/data.db.import").apply { writeText("new") }

        RescueFiles.replaceDatabaseFile(imported, live)

        assertEquals("new", live.readText())
        assertFalse(File(tmp, "databases/data.db-wal").exists(), "a stale wal must never be replayed onto the new database")
        assertFalse(File(tmp, "databases/data.db-shm").exists())
        assertFalse(imported.exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Settings backups / reset / restore
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `settings backup then swap brings the old settings back and keeps the current ones as backup`(@TempDir tmp: File) {
        val prefsDir = File(tmp, "shared_prefs").apply { mkdirs() }
        val backupDir = File(tmp, "backups")
        File(prefsDir, "preferences.xml").writeText("before")
        File(prefsDir, "secure_preferences.xml").writeText("secure-before")

        RescueFiles.backupSettingsFiles(prefsDir, backupDir)
        // The app then runs with other settings and no secure file
        File(prefsDir, "preferences.xml").writeText("after")
        File(prefsDir, "secure_preferences.xml").delete()

        RescueFiles.swapSettingsFiles(prefsDir, backupDir)

        assertEquals("before", File(prefsDir, "preferences.xml").readText())
        assertEquals("secure-before", File(prefsDir, "secure_preferences.xml").readText())
        assertEquals("after", File(backupDir, "preferences.xml").readText())
        assertFalse(File(backupDir, "secure_preferences.xml").exists(), "there was no live secure file to keep")
        assertFalse(File(backupDir, "preferences.xml.swap").exists(), "parking file must be gone")
    }

    @Test
    fun `backing up settings leaves no stale copy of a file that no longer exists`(@TempDir tmp: File) {
        val prefsDir = File(tmp, "shared_prefs").apply { mkdirs() }
        val backupDir = File(tmp, "backups").apply { mkdirs() }
        File(backupDir, "secure_preferences.xml").writeText("stale")
        File(prefsDir, "preferences.xml").writeText("current")

        RescueFiles.backupSettingsFiles(prefsDir, backupDir)

        assertEquals("current", File(backupDir, "preferences.xml").readText())
        assertFalse(File(backupDir, "secure_preferences.xml").exists())
    }

    @Test
    fun `deleting backups removes the whole directory and tolerates a missing one`(@TempDir tmp: File) {
        val backupDir = File(tmp, "rescue_backups").apply { mkdirs() }
        File(backupDir, "data.db").writeText("x")
        File(backupDir, "nested").apply { mkdirs() }

        RescueFiles.deleteBackupDir(backupDir)
        assertFalse(backupDir.exists())

        RescueFiles.deleteBackupDir(backupDir) // already gone: no exception
    }

    // ──────────────────────────────────────────────────────────────────────
    // Settings import routing (credentials vs regular settings)
    // ──────────────────────────────────────────────────────────────────────

    private fun editor() = mockk<SharedPreferences.Editor>(relaxed = true)

    @Test
    fun `credential keys go to the encrypted editor and the rest to the plain one`() {
        val plain = editor()
        val encrypted = editor()
        val rows = listOf(
            Triple("String", "ytCookie", "SECRET"),
            Triple("Boolean", "persistentQueue", "true"),
            Triple("Int", "count", "7"),
            Triple("Long", "big", "9000000000"),
            Triple("Float", "ratio", "1.5")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, encrypted)

        verify { encrypted.putString("ytCookie", "SECRET") }
        verify(exactly = 0) { plain.putString("ytCookie", any()) }
        verify { plain.putBoolean("persistentQueue", true) }
        verify { plain.putInt("count", 7) }
        verify { plain.putLong("big", 9_000_000_000L) }
        verify { plain.putFloat("ratio", 1.5f) }
        assertEquals(RescueFiles.SettingsImportStats(imported = 5, encryptedSkipped = 0), stats)
    }

    @Test
    fun `full encrypted round trip exports every encrypted key and restores it to the encrypted store`() {
        val samples: Map<String, Any> = RescueFiles.ALL_ENCRYPTED_KEYS.associateWith { "sample-value" }

        // The export assembly every export path (settings dialog, rescue, auto-backup) builds:
        // the credential groups plus the app-owned proxy key.
        val exported = RescueFiles.buildCredentialExport(samples, true, true, true, true)

        assertEquals(RescueFiles.ALL_ENCRYPTED_KEYS.size, exported.size, "every encrypted key must be exported")
        assertEquals(RescueFiles.ALL_ENCRYPTED_KEYS.toSet(), exported.map { it.second }.toSet())

        // CSV round trip (values with commas or quotes must survive).
        val rows = roundTrip(exported)
        assertEquals(exported.size, rows.size)

        // Import side (rescue + regular import both route on ALL_ENCRYPTED_KEYS): every key
        // must land in the encrypted editor, none in the plain one.
        val plain = editor()
        val encrypted = editor()
        val stats = RescueFiles.applySettingRows(rows, plain, encrypted)

        assertEquals(
            RescueFiles.SettingsImportStats(imported = RescueFiles.ALL_ENCRYPTED_KEYS.size, encryptedSkipped = 0),
            stats
        )
        verify(exactly = 0) { plain.putString(any(), any()) }
        verify { encrypted.putString(discordAdvancedStateTemplateKey, "sample-value") }
        verify { encrypted.putString(proxyPasswordEncryptedKey, "sample-value") }
    }

    @Test
    fun `advanced discord and proxy keys are routed to the encrypted editor`() {
        val plain = editor()
        val encrypted = editor()
        val rows = listOf(
            Triple("String", discordAdvancedStateTemplateKey, "{song.name}"),
            Triple("Boolean", discordAdvancedShowStateKey, "true"),
            Triple("String", proxyPasswordEncryptedKey, "s3cret"),
            Triple("String", "language", "en")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, encrypted)

        verify { encrypted.putString(discordAdvancedStateTemplateKey, "{song.name}") }
        verify { encrypted.putBoolean(discordAdvancedShowStateKey, true) }
        verify { encrypted.putString(proxyPasswordEncryptedKey, "s3cret") }
        verify(exactly = 0) { plain.putString(discordAdvancedStateTemplateKey, any()) }
        verify(exactly = 0) { plain.putString(proxyPasswordEncryptedKey, any()) }
        verify { plain.putString("language", "en") }
        assertEquals(RescueFiles.SettingsImportStats(imported = 4, encryptedSkipped = 0), stats)
    }

    @Test
    fun `credential keys are skipped and never written in clear when the encrypted store is unavailable`() {
        val plain = editor()
        val rows = listOf(
            Triple("String", "ytCookie", "SECRET"),
            Triple("String", "language", "en")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, null)

        verify(exactly = 0) { plain.putString("ytCookie", any()) }
        verify { plain.putString("language", "en") }
        assertEquals(RescueFiles.SettingsImportStats(imported = 1, encryptedSkipped = 1), stats)
    }

    @Test
    fun `unknown types and unparsable numbers are not counted and do not abort the import`() {
        val plain = editor()
        val rows = listOf(
            Triple("HashSet", "tags", "a"),
            Triple("Int", "bad", "abc"),
            Triple("String", "ok", "v")
        )

        val stats = RescueFiles.applySettingRows(rows, plain, null)

        assertEquals(1, stats.imported)
        verify { plain.putString("ok", "v") }
        verify(exactly = 0) { plain.putInt(any(), any()) }
    }
}
