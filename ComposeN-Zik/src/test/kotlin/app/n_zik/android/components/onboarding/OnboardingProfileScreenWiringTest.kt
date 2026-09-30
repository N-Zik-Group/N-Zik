package app.n_zik.android.components.onboarding

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.faceAvatarSourceKey
import app.it.fast4x.rimusic.utils.faceNameSourceKey
import app.it.fast4x.rimusic.utils.profileDisplayNameKey
import app.it.fast4x.rimusic.utils.writeProfileEntries
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for the profile onboarding step ([OnboardingProfileScreen]) — the
 * "name inert for the face" regression: the name, the face-name source and the face
 * avatar source typed at onboarding must land in the stores the face chain actually
 * consumes (the profile store `displayName_default` + the plain prefs
 * `faceNameSource` / `faceAvatarSource`), never in the removed legacy `username` /
 * `display_name_source` keys.
 *
 * The step's other contracts are pinned too: the auto-advance on a restored
 * identity (a custom base name or a restored profile list), the validation rules
 * (the reserved base ID is rejected, a case variant of the resolved name is
 * accepted, a cleared field is a no-op) and the account row locks.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 * The keystore-backed [profileSecurePrefs] is the only stub (a JVM has no keystore);
 * the account state loads on the real DATA pool, so the tests wait for the lock state
 * to settle before interacting.
 *
 * The step renders TWO source selectors (name + avatar) with the same four options,
 * so the "locked" badge count is doubled, and an account label matches two nodes
 * (one per section) — the tests disambiguate with first/last in document order
 * (the name section comes first, then the avatar section).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OnboardingProfileScreenWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private var completed = false

    @Before
    fun setUp() {
        completed = false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Stubs the keystore-backed secure prefs: [cookie] under [ytCookieKey], everything else empty. */
    private fun mockSecurePrefs(cookie: String) {
        val secure = mockk<SharedPreferences>()
        every { secure.getString(ytCookieKey, "") } returns cookie
        every { secure.getString(discordPersonalAccessTokenKey, "") } returns ""
        every { secure.getString(lastfmSessionKey, "") } returns ""
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        every { profileSecurePrefs(any(), "default") } returns secure
    }

    private fun showScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                OnboardingProfileScreen(onComplete = { completed = true })
            }
        }
    }

    /** Waits for the one-shot account state load to settle: [lockedCount] "Not logged in" badges. */
    private fun waitForLockState(lockedCount: Int) {
        val locked = context.getString(R.string.face_source_locked)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { composeRule.onAllNodesWithText(locked).fetchSemanticsNodes().size == lockedCount }.isSuccess) return
            Thread.sleep(50)
            composeRule.waitForIdle()
        }
        fail("The account lock state never settled to $lockedCount locked badge(s) within 10 s")
    }

    private val profileStore: SharedPreferences
        get() = context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)

    private val plainPrefs: SharedPreferences
        get() = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    private val legacyStore: SharedPreferences
        get() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    @Test
    fun continuingWithANewNameWritesTheBaseDisplayNameIntoTheProfileStore() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        // The face preview shows the same name above the field: exactly two
        // nodes carry the text, and the field is the second one in document
        // order
        val nameNodes = composeRule.onAllNodesWithText(context.getString(R.string.profile_base_name))
        nameNodes.assertCountEquals(2)
        nameNodes[1].performTextReplacement("Moi")
        // The Continue button sits below the fold of the test window: scroll it into
        // view first, then click (performClick hit-tests, so an off-screen node is a no-op)
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // The face chain's store: the base clone is renamed through the profile store
        assertEquals(
            "Moi",
            profileStore.getString(profileDisplayNameKey("default"), null)
        )
        // No face source was changed: the default "profil" needs no write (name AND
        // avatar source)
        assertEquals(null, plainPrefs.getString(faceNameSourceKey, null))
        assertEquals(null, plainPrefs.getString(faceAvatarSourceKey, null))
        // The legacy keys are dead: nothing was written to the old store
        assertFalse(legacyStore.contains("username"))
        assertFalse(legacyStore.contains("display_name_source"))
        assertTrue(completed)
    }

    @Test
    fun keepingTheResolvedNameStoresNoCustomName() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // No-op rule: the resolved base name is not stored as a custom name, and
        // the default "profil" face sources need no write either
        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertEquals(null, plainPrefs.getString(faceNameSourceKey, null))
        assertEquals(null, plainPrefs.getString(faceAvatarSourceKey, null))
        assertTrue(completed)
    }

    @Test
    fun aLoggedInYouTubeSourceWritesTheFaceNameSource() {
        // A cookie restored by the import: the YouTube source is unlocked
        mockSecurePrefs("SID=abc; SAPISID=secret")
        showScreen()
        waitForLockState(4)

        // The name section's YouTube row (the avatar section's row is the
        // second one in document order)
        val ytRows = composeRule.onAllNodesWithText(context.getString(R.string.display_name_source_youtube))
        ytRows.assertCountEquals(2)
        ytRows[0].performScrollTo().performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // The face chain consumes the onboarding write: the face name comes from
        // the YouTube account from the next launch
        assertEquals("youtube", plainPrefs.getString(faceNameSourceKey, null))
        // The name was left as-is: no custom name is stored, and the avatar
        // source was left as-is too
        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertEquals(null, plainPrefs.getString(faceAvatarSourceKey, null))
        assertTrue(completed)
    }

    @Test
    fun aLoggedInYouTubeAvatarSourceWritesTheFaceAvatarSource() {
        // A cookie restored by the import: the YouTube source is unlocked
        mockSecurePrefs("SID=abc; SAPISID=secret")
        showScreen()
        waitForLockState(4)

        // The avatar section's YouTube row (the name section's row is the
        // first one in document order, so the avatar one is last)
        val ytRows = composeRule.onAllNodesWithText(context.getString(R.string.display_name_source_youtube))
        ytRows.assertCountEquals(2)
        ytRows[1].performScrollTo().performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // The face chain consumes the onboarding write: the face avatar comes
        // from the YouTube account from the next launch
        assertEquals("youtube", plainPrefs.getString(faceAvatarSourceKey, null))
        // The name source was left as-is: no write (the name either)
        assertEquals(null, plainPrefs.getString(faceNameSourceKey, null))
        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertTrue(completed)
    }

    @Test
    fun anInvalidNameShowsTheErrorAndStaysOnTheStep() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        // A path separator is never safe as a name (the names become file names)
        // — the field is the second node with that text (the face preview shows
        // the same name above it)
        val nameNodes = composeRule.onAllNodesWithText(context.getString(R.string.profile_base_name))
        nameNodes.assertCountEquals(2)
        nameNodes[1].performTextReplacement("Bad/Name")
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.profile_name_invalid_or_taken))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertFalse(completed)
    }

    @Test
    fun aClearedNameFieldIsANoOp() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        // Clearing the field keeps the resolved name: the preview renders the
        // resolved face for a blank field, so continuing is a no-op, not an error
        val nameNodes = composeRule.onAllNodesWithText(context.getString(R.string.profile_base_name))
        nameNodes.assertCountEquals(2)
        nameNodes[1].performTextReplacement("")
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertTrue(
            composeRule
                .onAllNodesWithText(context.getString(R.string.profile_name_invalid_or_taken))
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(completed)
    }

    @Test
    fun aCaseVariantOfTheResolvedNameIsAccepted() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        // The base's own resolved name in another case is accepted like the rename
        // flows accept it (the name being edited is excluded from the taken set)
        val base = context.getString(R.string.profile_base_name)
        val nameNodes = composeRule.onAllNodesWithText(base)
        nameNodes.assertCountEquals(2)
        nameNodes[1].performTextReplacement(base.lowercase())
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(base.lowercase(), profileStore.getString(profileDisplayNameKey("default"), null))
        assertTrue(completed)
    }

    @Test
    fun anAlreadyTakenNameShowsTheErrorAndStaysOnTheStep() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        // "default" is reserved (the base profile ID): a fresh install's taken set
        // carries it, so it is rejected even though it is file-name safe
        val nameNodes = composeRule.onAllNodesWithText(context.getString(R.string.profile_base_name))
        nameNodes.assertCountEquals(2)
        nameNodes[1].performTextReplacement("default")
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.profile_name_invalid_or_taken))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertFalse(completed)
    }

    @Test
    fun aLockedSourceRowCannotBeSelected() {
        mockSecurePrefs("")
        showScreen()
        waitForLockState(6)

        // No account connected: the account rows are faded and unclickable — the
        // click is a no-op, the source stays on the default "profil" (no write)
        val ytRows = composeRule.onAllNodesWithText(context.getString(R.string.display_name_source_youtube))
        ytRows.assertCountEquals(2)
        ytRows[0].performScrollTo().performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.onboard_profile_continue))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(null, plainPrefs.getString(faceNameSourceKey, null))
        assertEquals(null, plainPrefs.getString(faceAvatarSourceKey, null))
        assertTrue(completed)
    }

    @Test
    fun aRestoredBaseNameAutoAdvancesWithoutInteraction() {
        mockSecurePrefs("")
        // The import restored a custom base name: the identity is settled, the
        // step advances on its own
        profileStore.edit { putString(profileDisplayNameKey("default"), "Restored") }
        showScreen()
        composeRule.waitForIdle()

        assertTrue(completed)
        // The restored name is kept as-is, never silently overwritten
        assertEquals("Restored", profileStore.getString(profileDisplayNameKey("default"), null))
    }

    @Test
    fun restoredProfilesAutoAdvanceWithoutInteraction() {
        mockSecurePrefs("")
        // The import restored a profile list: the identity is settled, the step
        // advances on its own (a database-only import restores no profile and
        // keeps the step shown)
        context.writeProfileEntries(listOf("work" to ""))
        showScreen()
        composeRule.waitForIdle()

        assertTrue(completed)
        // The auto-advance writes nothing
        assertEquals(null, profileStore.getString(profileDisplayNameKey("default"), null))
        assertEquals(null, plainPrefs.getString(faceNameSourceKey, null))
    }
}
