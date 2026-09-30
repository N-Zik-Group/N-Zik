package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.faceAvatarSourceKey
import app.it.fast4x.rimusic.utils.faceNameSourceKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import app.n_zik.android.utils.FACE_SOURCE_PROFILE
import app.n_zik.android.utils.FACE_SOURCE_YOUTUBE
import app.n_zik.android.utils.FaceAvatar
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests the prefs-to-face wiring of the real [loadProfileFace] (spec-profiles-page-face)
 * against mocked profile prefs / secure prefs: a logged-in account source wins with its
 * name and avatar, a not-logged-in source falls back to the profile display name and
 * the deterministic initials, and the "Profil" source uses the stored photo when set.
 *
 * Only the keystore-backed [profileSecurePrefs] is stubbed (a JVM has no keystore);
 * every other hop runs for real — the plain profiled prefs, the profile store
 * (display name) and the photo source.
 */
class ProfileFaceWiringTest {

    private val defaultName = "NzikFan"
    private lateinit var files: File
    private lateinit var context: Context
    private lateinit var plainPrefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences

    @BeforeEach
    fun setup() {
        files = Files.createTempDirectory("nzik-profile-face-wiring").toFile()
        plainPrefs = mockk()
        securePrefs = mockk()
        context = mockk()
        every { context.applicationContext } returns context
        every { context.filesDir } returns files
        // The plain profiled prefs of "work" (the face sources)
        every { context.getSharedPreferences("preferences_work", Context.MODE_PRIVATE) } returns plainPrefs
        // The profile store (the display name of "work")
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns
            stringPrefsOf("displayName_work" to "Danie")
        // No account is logged in by default — each test unlocks the ones it needs.
        every { securePrefs.getString(ytCookieKey, "") } returns ""
        every { securePrefs.getString(ytAccountNameKey, "") } returns ""
        every { securePrefs.getString(ytAccountThumbnailKey, "") } returns ""
        every { securePrefs.getString(discordPersonalAccessTokenKey, "") } returns ""
        every { securePrefs.getString(discordUsernameKey, "") } returns ""
        every { securePrefs.getString(discordAvatarKey, "") } returns ""
        every { securePrefs.getString(lastfmSessionKey, "") } returns ""
        every { securePrefs.getString(lastfmUsernameKey, "") } returns ""
        every { securePrefs.getString(lastfmAvatarUrlKey, "") } returns ""
        // The keystore-backed secure prefs are mocked (a JVM has no keystore).
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        every { profileSecurePrefs(any(), "work") } returns securePrefs
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    private fun stringPrefsOf(vararg pairs: Pair<String, String?>): SharedPreferences {
        val store = pairs.toMap()
        return mockk {
            every { getString(any(), any()) } answers { store[firstArg<String>()] ?: secondArg() }
        }
    }

    @Test
    fun aLoggedInAccountSourceWinsWithItsNameAndAvatar() {
        every { plainPrefs.getString(faceNameSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_YOUTUBE
        every { plainPrefs.getString(faceAvatarSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_YOUTUBE
        every { securePrefs.getString(ytCookieKey, "") } returns "SID=abc; SAPISID=secret"
        every { securePrefs.getString(ytAccountNameKey, "") } returns "Danie YT"
        every { securePrefs.getString(ytAccountThumbnailKey, "") } returns "https://yt.example/a.jpg"

        val face = loadProfileFace(context, "work", defaultName)

        assertEquals("Danie YT", face.name)
        assertEquals(FaceAvatar.Photo("https://yt.example/a.jpg"), face.avatar)
    }

    @Test
    fun aNotLoggedInSourceFallsBackToTheProfileNameAndInitials() {
        every { plainPrefs.getString(faceNameSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_YOUTUBE
        every { plainPrefs.getString(faceAvatarSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_YOUTUBE

        val face = loadProfileFace(context, "work", defaultName)

        assertEquals("Danie", face.name)
        assertEquals(FaceAvatar.Initials("Danie"), face.avatar)
    }

    @Test
    fun theProfilSourceUsesTheStoredPhotoWhenSet() {
        every { plainPrefs.getString(faceNameSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_PROFILE
        every { plainPrefs.getString(faceAvatarSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_PROFILE
        val photo = File(files, "profiles/work/avatar.jpg")
        photo.parentFile?.mkdirs()
        photo.writeText("photo")

        val face = loadProfileFace(context, "work", defaultName)

        assertEquals("Danie", face.name)
        assertEquals(FaceAvatar.Photo(photo.absolutePath), face.avatar)
    }

    @Test
    fun theBaseProfileFaceResolvesFromTheUnsuffixedPrefs() {
        // The base profile's plain prefs are the un-suffixed "preferences" file.
        every { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) } returns plainPrefs
        every { profileSecurePrefs(any(), DEFAULT_PROFILE_ID) } returns securePrefs
        every { plainPrefs.getString(faceNameSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_PROFILE
        every { plainPrefs.getString(faceAvatarSourceKey, FACE_SOURCE_PROFILE) } returns FACE_SOURCE_PROFILE

        val face = loadProfileFace(context, DEFAULT_PROFILE_ID, defaultName)

        // No display name is stored for the base: it falls back to the app default name.
        assertEquals(defaultName, face.name)
        assertEquals(FaceAvatar.Initials(defaultName), face.avatar)
    }

    @Test
    fun theProfilePrefsFileMappingCoversBothTheBaseAndTheSuffixedNames() {
        val dataDir = File(files, "data").apply { mkdirs() }
        // A real instance: dataDir is a public field, not a method — mockk cannot stub it.
        val appInfo = ApplicationInfo().apply { this.dataDir = dataDir.absolutePath }
        every { context.applicationInfo } returns appInfo

        assertEquals(
            File(dataDir, "shared_prefs/preferences.xml"),
            profilePrefsFile(context, DEFAULT_PROFILE_ID)
        )
        assertEquals(
            File(dataDir, "shared_prefs/preferences_work.xml"),
            profilePrefsFile(context, "work")
        )
    }
}
