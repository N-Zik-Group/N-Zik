package app.n_zik.android

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.profileLastUsed
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure cold/warm decision of the direct profile launcher shortcuts (spec-profile-shortcuts), no
 * Android runtime needed: the decision is a pure function of (tapped id, active id, live
 * profile ids). The CAP-3 switch side effects are pinned by ProfileSwitchTest (executeProfileSwitch
 * on a bare Context). The WARM call site (consumeProfileShortcutWarm → executeProfileSwitch +
 * exitProcess(0)) is deliberately NOT exercised here: exitProcess(0) is a real System.exit that
 * Robolectric 4.17 does not intercept, so driving the warm switch from onNewIntent would kill the
 * test JVM — this file pins the no-switch warm cases (active / base / unknown profile) instead.
 */
class MainActivityProfileShortcutDecisionTest {

    @Test
    fun `a tap on a different live profile is a switch to that profile`() {
        assertEquals("work", profileSwitchTarget("work", "default", listOf("work", "home")))
    }

    @Test
    fun `a tap on the base profile while a user profile is active is a switch back to the base`() {
        // The base is a valid target alongside the user profiles (validProfileIds includes it),
        // so the launcher can switch back to the base from a user profile.
        assertEquals(DEFAULT_PROFILE_ID, profileSwitchTarget(DEFAULT_PROFILE_ID, "work", listOf(DEFAULT_PROFILE_ID, "work")))
    }

    @Test
    fun `a tap on the active profile is a bare launch`() {
        assertNull(profileSwitchTarget("work", "work", listOf("work", "home")))
    }

    @Test
    fun `a tap on an unknown or deleted profile is ignored`() {
        // The app never points itself at a profile that does not exist (cold: bare launch,
        // warm: bare consumption — both driven by this decision).
        assertNull(profileSwitchTarget("gone", "default", listOf("work")))
        assertNull(profileSwitchTarget(null, "default", listOf("work")))
        assertNull(profileSwitchTarget("", "default", listOf("work")))
    }

    @Test
    fun `the profile shortcut actions are the ones excluded from the home-tab shortcut state`() {
        // Feeding them to shortcutIntentAction would pop the back stack to home, which is not
        // the profile shortcuts' job (they have their own consumption paths).
        assertTrue(isProfileShortcutAction(MainActivity.action_profiles))
        assertTrue(isProfileShortcutAction(MainActivity.action_profile))
        assertFalse(isProfileShortcutAction(MainActivity.action_search))
        assertFalse(isProfileShortcutAction(MainActivity.action_library))
        assertFalse(isProfileShortcutAction(null))
        assertFalse(isProfileShortcutAction(Intent.ACTION_MAIN))
    }
}

/**
 * App OFF / app ON lifecycle contract of the profile launcher shortcuts
 * (spec-profile-shortcuts), tested on the real [MainActivity] under Robolectric, the same way
 * the rewind deep links are pinned (MainActivityRewindDeepLinkLifecycleTest):
 *
 * - app OFF (cold start): [MainActivity.consumeProfileShortcut] sets the tapped profile active
 *   BEFORE [MainActivity.startApp] runs (it is dispatched after onCreate returns, via
 *   `monet.invokeOnReady`), records the tap as a use, and strips the one-shot extra off the kept
 *   intent — a tap on the active or on a deleted profile is a bare launch;
 * - app ON (warm start): [MainActivity.onNewIntent] → [MainActivity.consumeProfileShortcutWarm]
 *   shares the same one-shot consumption contract for the no-switch cases (a tap on a different
 *   valid profile runs the full switch + process exit — see the decision test above).
 *
 * The generic « Profiles » shortcut only records the navigation target
 * ([MainActivity.openProfilesShortcut], consumed by the navigation effect, gated on onboarding).
 *
 * robolectric.properties pins a plain Application, so MainApplication is never created.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityProfileShortcutLifecycleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun profileIntent(profileId: String): Intent =
        Intent().apply {
            action = MainActivity.action_profile
            putExtra(MainActivity.EXTRA_PROFILE_ID, profileId)
        }

    private fun profilesIntent(): Intent =
        Intent().apply { action = MainActivity.action_profiles }

    @Before
    fun setUpProfiles() {
        // One user profile exists, the base profile is active, the names file is fresh.
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        context.filesDir.resolve(PROFILE_NAMES_FILE_NAME).writeText("work\tWork\n")
    }

    // ---- intent decoding: profileShortcutTarget on real Intents ----

    @Test
    fun `a direct profile intent decodes to its profile id`() {
        assertEquals("work", profileShortcutTarget(profileIntent("work")))
    }

    @Test
    fun `a direct profile intent without the id extra decodes to nothing`() {
        val intent = Intent().apply { action = MainActivity.action_profile }
        assertNull(profileShortcutTarget(intent))
    }

    @Test
    fun `the generic profiles intent decodes to no direct profile`() {
        // Its consumption is the navigation target (openProfilesShortcut), not a profile.
        assertNull(profileShortcutTarget(profilesIntent()))
        assertNull(profileShortcutTarget(Intent(Intent.ACTION_MAIN)))
        assertNull(profileShortcutTarget(null))
    }

    // ---- App OFF: cold start, onCreate consumes the launch intent ----

    @Test
    fun `cold start on a different valid profile launches in that profile and strips the extra`() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, profileIntent("work"))
            .create()
            .get()

        // The active profile is set BEFORE startApp runs, so the first per-profile state read
        // already sees the tapped profile (COLD_PROFIL_NOUVEAU).
        assertEquals("work", getActiveProfile(activity))
        // The tap counts as a use from this launch (spec-profiles-page-face semantics).
        assertTrue((activity.profileLastUsed("work") ?: 0L) > 0L)
        // One-shot: the kept intent carries the extra no more (singleTask re-delivery is inert).
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
        // A direct profile shortcut launches, it does not open the profiles page.
        assertFalse(activity.openProfilesShortcut)
    }

    @Test
    fun `cold start on the base profile from a user profile switches back to the base`() {
        // COLD_BACK_TO_BASE: a user profile is active; tapping the base shortcut sets the base
        // active before launch, so the launch lands in the base.
        setActiveProfile("work", context)
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, profileIntent(DEFAULT_PROFILE_ID))
            .create()
            .get()

        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
        assertFalse(activity.openProfilesShortcut)
    }

    @Test
    fun `cold start on the already active profile is a bare launch`() {
        // COLD_MEME_PROFIL: no switch, no restart — just a normal launch in the current profile.
        setActiveProfile("work", context)
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, profileIntent("work"))
            .create()
            .get()

        assertEquals("work", getActiveProfile(activity))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
    }

    @Test
    fun `cold start on a deleted profile is ignored and the extra is still stripped`() {
        // COLD_PROFIL_PARTI: the profile no longer exists — the tap is ignored (no crash), the
        // extra is stripped anyway, so the re-delivery decodes to nothing.
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, profileIntent("gone"))
            .create()
            .get()

        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
        assertFalse(activity.openProfilesShortcut)
    }

    @Test
    fun `cold start on the generic profiles shortcut records the navigation target`() {
        // GENERIC_OPEN (cold): the profiles page opens after the normal start — the intent only
        // records the target, the active profile is untouched.
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, profilesIntent())
            .create()
            .get()

        assertTrue(activity.openProfilesShortcut)
        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
    }

    @Test
    fun `cold start from the launcher icon does not record any profile shortcut target`() {
        val activity = Robolectric
            .buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN))
            .create()
            .get()

        assertFalse(activity.openProfilesShortcut)
        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
    }

    // ---- App ON: warm start, onNewIntent consumes the fresh intent ----

    @Test
    fun `warm start on the active profile consumes without switching`() {
        // WARM_MEME_PROFIL: bare consumption — no switch, no restart, extra stripped.
        setActiveProfile("work", context)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(profileIntent("work"))
        val activity = controller.get()

        assertEquals("work", getActiveProfile(activity))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
        assertFalse(activity.openProfilesShortcut)
    }

    @Test
    fun `warm start on the already active base consumes without switching`() {
        // WARM_MEME_BASE: the base is active (the setup default); tapping the base shortcut is a
        // bare consumption — no switch, no restart, extra stripped. (The base switch case — the
        // full CAP-3 + exitProcess(0) — is not exercised here: Robolectric does not intercept
        // System.exit, so the warm switch would kill the test JVM. ProfileSwitchTest pins the
        // CAP-3 helper itself.)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(profileIntent(DEFAULT_PROFILE_ID))
        val activity = controller.get()

        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
    }

    @Test
    fun `a warm profile tap never feeds the home-tab shortcut state`() {
        // The onNewIntent exclusion (takeUnless isProfileShortcutAction): a profile tap must not
        // leave its action in shortcutIntentAction — the home-tab effect would pop the back stack
        // to home on every warm profile tap.
        setActiveProfile("work", context)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(profileIntent("work"))

        assertNull(controller.get().shortcutIntentAction)
    }

    @Test
    fun `warm start on an unknown profile consumes without switching`() {
        // The profile does not exist (deleted since the shortcut was registered): bare
        // consumption, no switch, no crash, extra stripped.
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(profileIntent("gone"))
        val activity = controller.get()

        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
        assertFalse(activity.intent.hasExtra(MainActivity.EXTRA_PROFILE_ID))
    }

    @Test
    fun `warm start on the generic profiles shortcut records the navigation target`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        controller.newIntent(profilesIntent())
        val activity = controller.get()

        assertTrue(activity.openProfilesShortcut)
        assertEquals(DEFAULT_PROFILE_ID, getActiveProfile(activity))
    }

    @Test
    fun `warm foreign intents do not clobber a pending generic profiles target`() {
        // The pending target is held until the navigation effect runs (gated on onboarding):
        // a foreign tap in between must preserve it, or the profiles page would be lost.
        val controller = Robolectric
            .buildActivity(MainActivity::class.java, profilesIntent())
            .create()
        assertTrue(controller.get().openProfilesShortcut)

        controller.newIntent(Intent().setAction(Intent.ACTION_VIEW))

        assertTrue(controller.get().openProfilesShortcut)
    }
}
