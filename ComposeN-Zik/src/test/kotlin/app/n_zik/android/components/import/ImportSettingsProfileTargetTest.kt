package app.n_zik.android.components.import

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Tests the profile targeting of the settings import (the "Import into which
 * profile?" flow): [ImportSettings.onImport] routes every row into the requested
 * profile's own prefs — the profiled plain prefs plus the profiled secure prefs —
 * and leaves the base profile's prefs untouched when the target is not the base
 * one, so a backup can be imported into an inactive profile.
 */
class ImportSettingsProfileTargetTest {

    private lateinit var files: File
    private lateinit var context: Context
    private lateinit var targetPlain: SharedPreferences
    private lateinit var targetPlainEditor: SharedPreferences.Editor
    private lateinit var targetSecure: SharedPreferences
    private lateinit var targetSecureEditor: SharedPreferences.Editor
    private lateinit var basePlain: SharedPreferences

    @BeforeEach
    fun setup() {
        files = Files.createTempDirectory("nzik-import-settings-target").toFile()
        context = mockk()
        every { context.applicationContext } returns context
        every { context.filesDir } returns files
        targetPlain = mockk()
        targetPlainEditor = mockk()
        every { targetPlain.edit() } returns targetPlainEditor
        every { targetPlainEditor.commit() } returns true
        targetSecure = mockk()
        targetSecureEditor = mockk()
        every { targetSecure.edit() } returns targetSecureEditor
        every { targetSecureEditor.commit() } returns true
        // The base profile's plain prefs — an import into "work" must not touch them
        basePlain = mockk()
        every { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) } returns basePlain
        every { context.getSharedPreferences("preferences_work", Context.MODE_PRIVATE) } returns targetPlain
        // The keystore-backed secure prefs are mocked (a JVM has no keystore)
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        every { profileSecurePrefs(any(), "work") } returns targetSecure
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun anImportIntoWorkWritesIntoWorkPrefsAndNotIntoTheBaseOnes() {
        val csv = """
            Type,Key,Value
            string,greet,hello
            boolean,flag,true
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        ImportSettings.onImport(context, ByteArrayInputStream(csv), "work")

        verify { targetPlainEditor.putString("greet", "hello") }
        verify { targetPlainEditor.putBoolean("flag", true) }
        verify { targetPlainEditor.commit() }
        verify { targetSecureEditor.commit() }
        verify(exactly = 0) { basePlain.edit() }
    }

    @Test
    fun anEncryptedKeyIsRoutedToTheTargetProfilesSecurePrefs() {
        val csv = """
            Type,Key,Value
            string,$ytCookieKey,SID=abc
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        ImportSettings.onImport(context, ByteArrayInputStream(csv), "work")

        verify { targetSecureEditor.putString(ytCookieKey, "SID=abc") }
        verify { targetSecureEditor.commit() }
        verify(exactly = 0) { targetPlainEditor.putString(any(), any()) }
    }
}
