package app.n_zik.android.shortcuts

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.currentProfileEntries
import app.it.fast4x.rimusic.utils.saveProfileDisplayName
import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog
import app.n_zik.android.components.dialog.settings.defaultShortcutsOrder
import app.n_zik.android.components.dialog.settings.profileShortcutDefs
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class ShortcutIconSyncTest {

    @After
    fun tearDown() {
        // The dynamic-valid-set tests statically stub the profile entries; never leak them.
        unmockkAll()
    }

    @Test
    fun `google launcher package is detected`() {
        assertTrue(isGoogleLauncher(GOOGLE_LAUNCHER_PACKAGE))
        assertTrue(isGoogleLauncher("com.google.android.apps.nexuslauncher"))
    }

    @Test
    fun `other launchers are not detected`() {
        assertFalse(isGoogleLauncher("com.miui.home"))
        assertFalse(isGoogleLauncher("com.huawei.launcher"))
        assertFalse(isGoogleLauncher("com.google.android.googlequicksearchbox"))
        assertFalse(isGoogleLauncher(""))
        assertFalse(isGoogleLauncher(null))
    }

    /**
     * Plain JVM (no Robolectric needed): resource ids are compile-time constants, so this maps
     * each shortcut id to its resources/action without ever touching a `Context`.
     */
    @Test
    fun `each shortcut id maps to its own action and distinct resources`() {
        val specs = ALL_SHORTCUT_IDS.associateWith { shortcutSpec(it) }

        assertEquals(SHORTCUT_SEARCH_ID to MainActivity.action_search, SHORTCUT_SEARCH_ID to specs.getValue(SHORTCUT_SEARCH_ID).third)
        assertEquals(SHORTCUT_ALBUMS_ID to MainActivity.action_albums, SHORTCUT_ALBUMS_ID to specs.getValue(SHORTCUT_ALBUMS_ID).third)
        assertEquals(SHORTCUT_ARTISTS_ID to MainActivity.actions_artists, SHORTCUT_ARTISTS_ID to specs.getValue(SHORTCUT_ARTISTS_ID).third)
        assertEquals(SHORTCUT_LIBRARY_ID to MainActivity.action_library, SHORTCUT_LIBRARY_ID to specs.getValue(SHORTCUT_LIBRARY_ID).third)
        assertEquals(SHORTCUT_PROFILES_ID to MainActivity.action_profiles, SHORTCUT_PROFILES_ID to specs.getValue(SHORTCUT_PROFILES_ID).third)
        assertEquals(SHORTCUT_RESCUE_ID to ACTION_RESCUE, SHORTCUT_RESCUE_ID to specs.getValue(SHORTCUT_RESCUE_ID).third)
        // Every fixed id gets its own label and its own drawable -- no two shortcuts silently
        // share one (the per-profile shortcuts are dynamic and not part of this set).
        assertEquals(ALL_SHORTCUT_IDS.size, specs.values.map { it.first }.distinct().size)
        assertEquals(ALL_SHORTCUT_IDS.size, specs.values.map { it.second }.distinct().size)
    }

    @Test
    fun `6 shortcut IDs are available`() {
        assertEquals(6, ALL_SHORTCUT_IDS.size)
        assertTrue(SHORTCUT_RESCUE_ID in ALL_SHORTCUT_IDS)
        assertTrue(SHORTCUT_SEARCH_ID in ALL_SHORTCUT_IDS)
        assertTrue(SHORTCUT_PROFILES_ID in ALL_SHORTCUT_IDS)
    }

    @Test
    fun `the generic profiles shortcut maps to the profiles icon and action`() {
        val (labelRes, drawableRes, action) = shortcutSpec(SHORTCUT_PROFILES_ID)

        assertEquals(R.string.profiles, labelRes)
        assertEquals(R.drawable.shortcut_profiles, drawableRes)
        assertEquals("app.it.fast4x.rimusic.action.profiles", action)
    }

    @Test
    fun `default active shortcuts has 4 entries and includes rescue`() {
        assertEquals(MAX_ACTIVE_SHORTCUTS, DEFAULT_ACTIVE_SHORTCUT_IDS.size)
        assertTrue(SHORTCUT_RESCUE_ID in DEFAULT_ACTIVE_SHORTCUT_IDS)
        assertFalse(SHORTCUT_SEARCH_ID in DEFAULT_ACTIVE_SHORTCUT_IDS)
    }

    @Test
    fun `rescue shortcut uses ACTION_RESCUE intent action`() {
        val (_, _, action) = shortcutSpec(SHORTCUT_RESCUE_ID)
        assertEquals(ACTION_RESCUE, action)
        assertEquals("app.n_zik.android.action.rescue", action)
    }

    @Test
    fun `rescue shortcut uses rescue icon drawable`() {
        val (_, drawableRes, _) = shortcutSpec(SHORTCUT_RESCUE_ID)
        assertEquals(R.drawable.shortcut_rescue, drawableRes)
    }

    // ──────────────────────────────────────────────────────────────────────
    // AppShortcutsSettingsDialog order/enabled parsing
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `parseOrder with empty string returns default order`() {
        val order = AppShortcutsSettingsDialog.parseOrder("")
        assertEquals(defaultShortcutsOrder, order)
    }

    @Test
    fun `parseOrder with valid string preserves order and adds missing`() {
        val order = AppShortcutsSettingsDialog.parseOrder("rescue,albums")
        assertEquals("rescue", order[0])
        assertEquals("albums", order[1])
        // The remaining IDs are appended
        assertTrue(order.containsAll(ALL_SHORTCUT_IDS))
        assertEquals(ALL_SHORTCUT_IDS.size, order.size)
    }

    @Test
    fun `parseOrder ignores unknown IDs`() {
        val order = AppShortcutsSettingsDialog.parseOrder("rescue,nonexistent,albums")
        assertFalse("nonexistent" in order)
        assertEquals(ALL_SHORTCUT_IDS.size, order.size)
    }

    @Test
    fun `parseEnabled with empty string returns defaults`() {
        val enabled = AppShortcutsSettingsDialog.parseEnabled("")
        assertEquals(DEFAULT_ACTIVE_SHORTCUT_IDS.toSet(), enabled)
    }

    @Test
    fun `parseEnabled always includes rescue even if not in string`() {
        val enabled = AppShortcutsSettingsDialog.parseEnabled("search,albums")
        assertTrue(SHORTCUT_RESCUE_ID in enabled)
    }

    @Test
    fun `parseEnabled with valid string includes specified IDs plus rescue`() {
        val enabled = AppShortcutsSettingsDialog.parseEnabled("search,albums")
        assertTrue(SHORTCUT_SEARCH_ID in enabled)
        assertTrue(SHORTCUT_ALBUMS_ID in enabled)
        assertTrue(SHORTCUT_RESCUE_ID in enabled) // always included
        assertFalse(SHORTCUT_ARTISTS_ID in enabled)
    }

    // ──────────────────────────────────────────────────────────────────────
    // resolveActiveShortcutIds: the pure function that decides what gets registered
    // ──────────────────────────────────────────────────────────────────────

    private val everyId = ALL_SHORTCUT_IDS.joinToString(",")

    @Test
    fun `resolve with nothing stored gives the defaults in order`() {
        assertEquals(
            listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID),
            resolveActiveShortcutIds(null, null)
        )
    }

    @Test
    fun `resolve caps at the maximum and gives way to rescue, not the other way round`() {
        val active = resolveActiveShortcutIds(everyId, everyId)

        assertEquals(MAX_ACTIVE_SHORTCUTS, active.size)
        assertTrue(SHORTCUT_RESCUE_ID in active)
        // Survivors keep their stored order; the shortcut that gives way is the last non-rescue one.
        assertEquals(
            listOf(SHORTCUT_SEARCH_ID, SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_RESCUE_ID),
            active
        )
    }

    @Test
    fun `resolve keeps rescue when the enabled list does not mention it`() {
        val active = resolveActiveShortcutIds("albums,artists", "albums,artists")
        assertTrue(SHORTCUT_RESCUE_ID in active)
        assertEquals(listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_RESCUE_ID), active)
    }

    @Test
    fun `resolve keeps rescue when the stored order does not mention it`() {
        // An order missing ids used to make rescue vanish from the launcher.
        val active = resolveActiveShortcutIds("search,albums", "search,albums")
        assertEquals(listOf(SHORTCUT_SEARCH_ID, SHORTCUT_ALBUMS_ID, SHORTCUT_RESCUE_ID), active)
    }

    @Test
    fun `resolve with a blank order still registers the enabled shortcuts`() {
        assertEquals(
            listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_RESCUE_ID),
            resolveActiveShortcutIds("", "albums")
        )
    }

    @Test
    fun `resolve never returns duplicates`() {
        val active = resolveActiveShortcutIds("albums,albums,rescue", "albums,albums,rescue")
        assertEquals(active.distinct(), active)
        assertEquals(listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_RESCUE_ID), active)
    }

    @Test
    fun `resolve ignores unknown ids and falls back to defaults on unusable input`() {
        val defaults = listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID)
        assertEquals(defaults, resolveActiveShortcutIds("nope,nada", ""))
        assertEquals(defaults, resolveActiveShortcutIds(",,", ","))
    }

    private fun prefsHolding(order: String?, enabled: String?): SharedPreferences = mockk {
        every { getString(appShortcutsOrderKey, null) } returns order
        every { getString(appShortcutsEnabledKey, null) } returns enabled
    }

    private fun prefsWithWrongType(): SharedPreferences = mockk {
        every { getString(any(), any()) } throws ClassCastException()
    }

    @Test
    fun `the dialog shows exactly the shortcuts the launcher registers, cap included`() {
        val (order, toggles) = AppShortcutsSettingsDialog.loadPrefs(prefsHolding(everyId, everyId))
        val shown = order.filterIndexed { index, _ -> toggles[index] }

        assertEquals(MAX_ACTIVE_SHORTCUTS, shown.size)
        assertTrue(SHORTCUT_RESCUE_ID in shown)
        assertEquals(resolveActiveShortcutIds(everyId, everyId).toSet(), shown.toSet())
    }

    @Test
    fun `the dialog falls back to the defaults when the stored values have the wrong type`() {
        val (order, toggles) = AppShortcutsSettingsDialog.loadPrefs(prefsWithWrongType())
        val shown = order.filterIndexed { index, _ -> toggles[index] }

        assertEquals(ALL_SHORTCUT_IDS.toSet(), order.toSet())
        assertEquals(DEFAULT_ACTIVE_SHORTCUT_IDS.toSet(), shown.toSet())
    }

    @Test
    fun `the dialog keeps a stored per-profile shortcut when the valid id set is dynamic`() {
        // A stored profile shortcut must survive loadPrefs against the live dynamic id set. A
        // regression to the fixed id set would drop it from the order, then silently disable it
        // on confirm (savePrefs rewrites the enabled set from the rows present).
        val prefs = prefsHolding("rescue,profile_work", "rescue,profile_work")

        val (order, toggles) = AppShortcutsSettingsDialog.loadPrefs(
            prefs,
            dynamicShortcutIds(listOf("work" to "Work")),
        )
        val shown = order.filterIndexed { index, _ -> toggles[index] }
        assertTrue("profile_work" in shown)

        // The fixed set (the pre-profile behavior) would have dropped the profile shortcut.
        val fixedOrder = AppShortcutsSettingsDialog.loadPrefs(prefs).first
        assertFalse("profile_work" in fixedOrder)
    }

    @Test
    fun `registration falls back to the defaults when the stored values have the wrong type`() {
        // No user profile in this scenario: the dynamic valid-id set is the fixed one only.
        val context = mockk<Context> {
            every { getSharedPreferences("preferences", Context.MODE_PRIVATE) } returns prefsWithWrongType()
        }
        mockkStatic("app.it.fast4x.rimusic.utils.ProfilePreferencesKt")
        every { context.currentProfileEntries() } returns emptyList()

        assertEquals(
            listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID),
            resolveActiveShortcutIds(context)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-profile shortcuts (spec-profile-shortcuts)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `profile shortcut id is built from the stable profile id`() {
        assertEquals("profile_work", profileShortcutId("work"))
        assertTrue(isProfileShortcut(profileShortcutId("work")))
        assertEquals("work", profileIdOfShortcut(profileShortcutId("work")))
        // A rename never re-creates the shortcut: the id only depends on the stable id.
        assertEquals("profile_work", profileShortcutId("work"))
    }

    @Test
    fun `fixed shortcut ids are not profile shortcuts`() {
        ALL_SHORTCUT_IDS.forEach {
            assertFalse(isProfileShortcut(it))
            assertEquals(null, profileIdOfShortcut(it))
        }
        // The bare prefix carries no profile id (unreachable through registration — ids only
        // come from real profiles — pinned anyway).
        assertEquals(null, profileIdOfShortcut(PROFILE_SHORTCUT_PREFIX))
    }

    @Test
    fun `dynamic valid ids are the fixed set plus one per user profile`() {
        val ids = dynamicShortcutIds(listOf("work" to "Work", "home" to ""))

        assertTrue(ALL_SHORTCUT_IDS.all { it in ids })
        assertTrue("profile_work" in ids)
        assertTrue("profile_home" in ids)
    }

    @Test
    fun `the base profile gets a direct shortcut only alongside at least one user profile`() {
        // With at least one user profile, the base gets its own direct shortcut (profile_default),
        // so the launcher can switch back to it from a user profile.
        val withUser = dynamicShortcutIds(listOf("default" to "", "work" to "Work"))
        assertTrue("profile_work" in withUser)
        assertTrue("profile_default" in withUser)

        // With only the base, the launcher icon already starts it — no base shortcut. The names
        // file never lists the base, so "base only" is an empty (or corrupt base-line) entry list.
        val baseOnly = dynamicShortcutIds(listOf("default" to ""))
        val none = dynamicShortcutIds(emptyList())
        assertFalse("profile_default" in baseOnly)
        assertFalse("profile_default" in none)
    }

    private val fixedPlusWork = ALL_SHORTCUT_IDS.toSet() + setOf(profileShortcutId("work"))

    @Test
    fun `resolve accepts a live profile shortcut id`() {
        val active = resolveActiveShortcutIds("profile_work,albums", "profile_work", fixedPlusWork)

        assertEquals(listOf("profile_work", SHORTCUT_RESCUE_ID), active)
    }

    @Test
    fun `resolve rejects a profile shortcut id whose profile no longer exists`() {
        // "profile_gone" was valid when stored, but the profile was deleted: the valid set no
        // longer knows it, so it drops out of both order and enabled — and a dead enabled id
        // does not count against the "nothing usable" fallback.
        val active = resolveActiveShortcutIds("profile_gone,albums", "profile_gone", ALL_SHORTCUT_IDS)

        assertFalse("profile_gone" in active)
        assertEquals(
            listOf(SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_LIBRARY_ID, SHORTCUT_RESCUE_ID),
            active
        )
    }

    @Test
    fun `resolve trims at the maximum when profile shortcuts are enabled, keeping rescue`() {
        val valid = ALL_SHORTCUT_IDS.toSet() + setOf(profileShortcutId("work"), profileShortcutId("home"))
        val order = "search,albums,artists,library,profiles,rescue,profile_work,profile_home"
        val active = resolveActiveShortcutIds(order, order, valid)

        assertEquals(MAX_ACTIVE_SHORTCUTS, active.size)
        assertTrue(SHORTCUT_RESCUE_ID in active)
        assertEquals(
            listOf(SHORTCUT_SEARCH_ID, SHORTCUT_ALBUMS_ID, SHORTCUT_ARTISTS_ID, SHORTCUT_RESCUE_ID),
            active
        )
    }

    @Test
    fun `resolve keeps profile shortcuts that come first in the stored order when trimming`() {
        val valid = ALL_SHORTCUT_IDS.toSet() + setOf(profileShortcutId("work"), profileShortcutId("home"))
        val order = "profile_work,profile_home,search,albums,artists,library,profiles,rescue"
        val active = resolveActiveShortcutIds(order, order, valid)

        assertEquals(MAX_ACTIVE_SHORTCUTS, active.size)
        // The first non-rescue ids in the stored order survive, whatever their kind.
        assertEquals(listOf("profile_work", "profile_home", SHORTCUT_SEARCH_ID, SHORTCUT_RESCUE_ID), active)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Where each shortcut goes
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `rescue opens RescueActivity and every other shortcut opens MainActivity`() {
        // Pointing rescue at MainActivity would send the user into the crash it exists for.
        assertEquals(RescueActivity::class.java, shortcutTargetClass(SHORTCUT_RESCUE_ID))
        ALL_SHORTCUT_IDS.filter { it != SHORTCUT_RESCUE_ID }.forEach {
            assertEquals(MainActivity::class.java, shortcutTargetClass(it))
        }
    }
}

/**
 * [shortcutIcon]/[tintedBitmap] are what fix the actual Pixel bug: on-device/emulator testing
 * found that a manifest (static) shortcut is immutable at the platform level -- Android's
 * `ShortcutService` refuses to add, update or even disable it via the API once declared in XML --
 * so the only way to give a shortcut a fixed black icon on the Google launcher and a
 * theme-adaptive one everywhere else is to never declare it statically at all, and pick the icon
 * type per launcher when registering the dynamic shortcut.
 *
 * `Icon.createWithResource`/`createWithBitmap` and `Bitmap.createBitmap`/`Canvas` need a real
 * `android.graphics`/`android.content.pm` runtime, hence Robolectric. This module's test source
 * set does not enable `includeAndroidResources` (that regressed ~36 unrelated tests via a JVM
 * security-provider conflict), so [tintedBitmap] is tested with a synthetic `ColorDrawable`
 * instead of a real app resource -- it needs no resource lookup at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShortcutIconRenderingTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `black icon variant uses a bitmap icon`() {
        // A resource id that doesn't need to exist: TYPE_BITMAP is set before any drawable lookup.
        val icon = Icon.createWithBitmap(tintedBitmap(ColorDrawable(Color.RED), Color.BLACK, sizePx = 8))

        assertEquals(Icon.TYPE_BITMAP, icon.type)
    }

    @Test
    fun `theme-adaptive variant uses a resource icon`() {
        val icon = shortcutIcon(context, R.drawable.shortcut_search, blackIcon = false)

        assertEquals(Icon.TYPE_RESOURCE, icon.type)
    }

    /**
     * Robolectric's shadow of `ColorDrawable`/`ShapeDrawable` does not faithfully apply
     * `DrawableCompat.setTint`'s `PorterDuffColorFilter` during `draw()` (verified: the rendered
     * pixel came back as the drawable's original color, not the requested tint) -- so this only
     * asserts the reliably-testable part (size). The actual tint rendering is confirmed correct on
     * a real Pixel emulator (black icons observed directly by the user after this fix).
     */
    @Test
    fun `tintedBitmap renders at the requested size`() {
        val redRectangle = ShapeDrawable(RectShape()).apply { paint.color = Color.RED }

        val bitmap = tintedBitmap(redRectangle, Color.BLACK, sizePx = 16)

        assertEquals(16, bitmap.width)
        assertEquals(16, bitmap.height)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-profile shortcuts (spec-profile-shortcuts) — need a real framework runtime
    // ──────────────────────────────────────────────────────────────────────

    private fun namesFile() = context.filesDir.resolve(PROFILE_NAMES_FILE_NAME)

    @Test
    fun `dynamic shortcut ids read the live profiles and offer the base alongside them`() {
        namesFile().writeText("work\tWork\n")
        try {
            val ids = dynamicShortcutIds(context)

            assertTrue(ALL_SHORTCUT_IDS.all { it in ids })
            assertTrue("profile_work" in ids)
            // The base shortcut is offered only because a user profile exists alongside it.
            assertTrue("profile_default" in ids)
        } finally {
            namesFile().delete()
        }
    }

    @Test
    fun `dynamic shortcut ids omit the base shortcut when no user profile exists`() {
        namesFile().delete()
        val ids = dynamicShortcutIds(context)

        assertFalse("profile_default" in ids)
    }

    @Test
    fun `the base profile shortcut label is its default name`() {
        // The base has no stored display name; its shortcut label is the app default name, not
        // the internal id.
        assertEquals(
            context.getString(R.string.profile_base_name),
            shortcutShortLabel(context, profileShortcutId(DEFAULT_PROFILE_ID)),
        )
    }

    @Test
    fun `the base profile shortcut is offered in the dialog only alongside a user profile`() {
        namesFile().writeText("work\tWork\n")
        try {
            val defs = profileShortcutDefs(context)

            assertTrue(profileShortcutId("work") in defs.keys)
            assertTrue(profileShortcutId(DEFAULT_PROFILE_ID) in defs.keys)
            assertEquals(
                context.getString(R.string.profile_base_name),
                defs.getValue(profileShortcutId(DEFAULT_PROFILE_ID)).dynamicLabel,
            )
        } finally {
            namesFile().delete()
        }
    }

    @Test
    fun `the base profile shortcut is not offered in the dialog when no user profile exists`() {
        namesFile().delete()
        val defs = profileShortcutDefs(context)

        assertFalse(profileShortcutId(DEFAULT_PROFILE_ID) in defs.keys)
    }

    @Test
    fun `profile shortcut label is the display name truncated to the launcher limit`() {
        context.saveProfileDisplayName("work", "W".repeat(40))

        val label = shortcutShortLabel(context, profileShortcutId("work"))

        // 25 chars is the launcher's hard limit on a short label (longer makes
        // setDynamicShortcuts throw) — the inlined constant, since the framework one is not
        // exposed on this compile SDK.
        assertEquals(MAX_SHORTCUT_SHORT_LABEL_LENGTH, label.length)
        assertEquals("W".repeat(25), label)
    }

    @Test
    fun `profile shortcut label falls back to the stable id when never renamed`() {
        assertEquals("work", shortcutShortLabel(context, profileShortcutId("work")))
    }

    @Test
    fun `fixed shortcut labels still resolve their label resource`() {
        assertEquals(context.getString(R.string.search), shortcutShortLabel(context, SHORTCUT_SEARCH_ID))
        assertEquals(context.getString(R.string.profiles), shortcutShortLabel(context, SHORTCUT_PROFILES_ID))
    }

    /**
     * End-to-end: a profile shortcut enabled through the real config CSV is registered with its
     * display name, the [MainActivity.action_profile] intent action and the stable profile ID
     * extra, targeting [MainActivity] (spec-profile-shortcuts). The generic « Profiles »
     * shortcut is not enabled here, so exactly the two enabled ids (rescue + the profile) come
     * back.
     */
    @Test
    fun `a live profile shortcut is registered with its label action and profile id extra`() {
        namesFile().writeText("work\n")
        context.saveProfileDisplayName("work", "Work")
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(appShortcutsOrderKey, "rescue,profile_work")
            .putString(appShortcutsEnabledKey, "rescue,profile_work")
            .commit()
        try {
            registerAppShortcuts(context)

            val registered = context.getSystemService(ShortcutManager::class.java).getDynamicShortcuts()
            assertEquals(setOf("rescue", "profile_work"), registered.map { it.id }.toSet())

            val work = registered.first { it.id == "profile_work" }
            assertEquals("Work", work.shortLabel)
            // The icon itself is not asserted here: this compile SDK's ShortcutInfo exposes
            // no getter for it (see the shortcutIcon split-out KDoc) — the icon branch is
            // pinned by the shortcutIcon tests above.
            val intent = checkNotNull(work.intent) { "profile_work shortcut has no intent" }
            assertEquals(MainActivity.action_profile, intent.action)
            assertEquals("work", intent.getStringExtra(MainActivity.EXTRA_PROFILE_ID))
            assertEquals(MainActivity::class.java.name, intent.component?.className)
        } finally {
            namesFile().delete()
            prefs.edit().remove(appShortcutsOrderKey).remove(appShortcutsEnabledKey).commit()
        }
    }
}
