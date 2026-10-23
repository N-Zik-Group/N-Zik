package app.n_zik.android.extensions.discord

import com.metrolist.music.discordrpc.entities.Button
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Item 6: the presence content builder — normal mode keeps the fixed NZik identity (frozen 2026-09-21); advanced mode renders name/state/details/buttons from the templates with the upstream placeholders, falls back to the defaults on empty templates, and keeps the "N-Zik" identity when the advanced name is blank (fork divergence vs upstream's artist fallback). */
class DiscordActivityBuilderTest {

    // Test fixture: the same values strings.xml ships in English — the builder
    // itself hardcodes no user-facing text anymore (2026-09-21 i18n refactor).
    private val strings = DiscordStrings(
        nameFallback = "N-Zik",
        buttonGetNZik = "Get N-Zik",
        buttonListenYtmusic = "Listen to YTMusic",
        pausedLineDefault = "⏸︎ Paused: {song.name}",
        unknownAlbum = "Unknown Album",
    )

    private val info = DiscordMediaInfo(
        title = "Song",
        artist = "Artist",
        albumName = "Album",
        songId = "dQw4w9WgXcQ",
    )

    private val normalButtons = listOf(
        Button("Get N-Zik", "https://github.com/N-Zik-Group/N-Zik/"),
        Button("Listen to YTMusic", "https://music.youtube.com/watch?v=dQw4w9WgXcQ"),
    )

    @Test
    fun `normal mode keeps the fixed NZik identity`() {
        val content = DiscordActivityBuilder.buildForPlaying(info, DiscordAdvancedSettings.DEFAULTS, strings)
        assertEquals("N-Zik", content.name)
        assertEquals("Artist", content.state)
        assertEquals("Song", content.details)
        assertEquals(normalButtons, content.buttons)
    }

    @Test
    fun `advanced mode renders name, state, details and buttons from the templates`() {
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(
            advancedMode = true,
            activityName = "♪ {song.name}",
            stateTemplate = "{album.name} · {song.id}",
            detailsTemplate = "{artist.name}",
            button1Label = "Watch {song.name}",
            button1Url = "https://watch/{song.id}",
            button2Enabled = false,
        )
        val content = DiscordActivityBuilder.buildForPlaying(info, settings, strings)
        assertEquals("♪ Song", content.name)
        assertEquals("Album · dQw4w9WgXcQ", content.state)
        assertEquals("Artist", content.details)
        assertEquals(listOf(Button("Watch Song", "https://watch/dQw4w9WgXcQ")), content.buttons)
    }

    @Test
    fun `advanced mode with empty templates falls back to the defaults`() {
        val content = DiscordActivityBuilder.buildForPlaying(
            info,
            DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true),
            strings,
        )
        assertEquals("N-Zik", content.name, "an empty advanced name keeps the NZik identity")
        assertEquals("Artist", content.state, "default state template = {artist.name}")
        assertEquals("Song", content.details, "default details template = {song.name}")
        assertEquals(normalButtons, content.buttons, "default buttons keep the NZik identity")
    }

    @Test
    fun `advanced blank name falls back to N-Zik, not to the artist`() {
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true, activityName = "   ")
        assertEquals("N-Zik", DiscordActivityBuilder.buildForPlaying(info, settings, strings).name)
    }

    @Test
    fun `a disabled button is omitted`() {
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true, button1Enabled = false)
        val content = DiscordActivityBuilder.buildForPlaying(info, settings, strings)
        assertEquals(1, content.buttons.size)
        assertEquals(Button("Listen to YTMusic", "https://music.youtube.com/watch?v=dQw4w9WgXcQ"), content.buttons.first())
    }

    @Test
    fun `paused line in normal mode keeps the fixed representation`() {
        assertEquals("⏸︎ Paused: Song", DiscordActivityBuilder.buildPausedLine(info, DiscordAdvancedSettings.DEFAULTS, strings))
    }

    @Test
    fun `idle preview shows the default template variables in normal mode`() {
        // The fallback card (nothing playing) shows the template variables as-is:
        // the fixed NZik identity + the default placeholders (2026-09-21 UI fix).
        val content = DiscordActivityBuilder.buildIdlePreview(DiscordAdvancedSettings.DEFAULTS, strings)
        assertEquals("N-Zik", content.name, "the fallback keeps the NZik identity")
        assertEquals("{song.name}", content.details, "default details template shown as-is")
        assertEquals("{artist.name}", content.state, "default state template shown as-is")
        assertEquals(
            listOf(
                Button("Get N-Zik", "https://github.com/N-Zik-Group/N-Zik/"),
                Button("Listen to YTMusic", "https://music.youtube.com/watch?v="),
            ),
            content.buttons,
            "the default button set, with the empty song id in the ytmusic URL",
        )
    }

    @Test
    fun `idle preview shows the current advanced templates as-is`() {
        // 2026-09-21 UI fix: the fallback card shows the user's actual current
        // customization (raw templates), so the preview reacts to the config.
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(
            advancedMode = true,
            activityName = "♪ {song.name}",
            stateTemplate = "on {album.name}",
            detailsTemplate = "▶ {song.id}",
            button1Label = "Watch {song.name}",
            button2Enabled = false,
        )
        val content = DiscordActivityBuilder.buildIdlePreview(settings, strings)
        assertEquals("♪ {song.name}", content.name, "the custom name template is shown as-is")
        assertEquals("▶ {song.id}", content.details, "the custom details template is shown as-is")
        assertEquals("on {album.name}", content.state, "the custom state template is shown as-is")
        assertEquals(listOf(Button("Watch {song.name}", "https://github.com/N-Zik-Group/N-Zik/")), content.buttons)
    }

    @Test
    fun `all five template placeholders are offered in the dialogs`() {
        // Regression (2026-09-21): {song.id} was clipped off the single-line chip
        // row in the template dialogs — the full set must stay offered (the fork

        // addition {app.version} joined it for the image tooltips).
        assertEquals(
            setOf("{song.name}", "{song.id}", "{artist.name}", "{album.name}", "{app.version}"),
            DiscordTemplateRenderer.PLACEHOLDERS.toSet(),
        )
    }

    @Test
    fun `paused line in advanced mode renders the pause template, default equals the current behavior`() {
        val default = DiscordActivityBuilder.buildPausedLine(
            info,
            DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true),
            strings,
        )
        assertEquals("⏸︎ Paused: Song", default)

        val custom = DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true, pauseTemplate = "{song.name} ⏸")
        assertEquals("Song ⏸", DiscordActivityBuilder.buildPausedLine(info, custom, strings))
    }

    // ─── Per-section visibility (advanced mode only) ────────────────────────────────

    @Test
    fun `advanced mode hides the state and details lines when the sections are disabled`() {
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(
            advancedMode = true,
            showState = false,
            showDetails = false,
        )
        val content = DiscordActivityBuilder.buildForPlaying(info, settings, strings)
        assertEquals("", content.state, "the disabled state section must be empty")
        assertEquals("", content.details, "the disabled details section must be empty")
        assertEquals("N-Zik", content.name, "the name line is not affected")
        assertEquals(normalButtons, content.buttons, "buttons are not affected")
    }

    @Test
    fun `normal mode ignores the section toggles (frozen identity)`() {
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(
            showState = false,
            showDetails = false,
            showArtwork = false,
            showSmallImage = false,
            showTimestamps = false,
        )
        val content = DiscordActivityBuilder.buildForPlaying(info, settings, strings)
        assertEquals("Artist", content.state, "normal mode keeps the fixed state line")
        assertEquals("Song", content.details, "normal mode keeps the fixed details line")
    }

    @Test
    fun `the paused line is hidden when the details section is disabled`() {
        val disabled = DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true, showDetails = false)
        assertEquals("", DiscordActivityBuilder.buildPausedLine(info, disabled, strings))
        // The enabled section keeps the pause line; normal mode is unaffected either way.
        val enabled = DiscordAdvancedSettings.DEFAULTS.copy(advancedMode = true)
        assertEquals("⏸︎ Paused: Song", DiscordActivityBuilder.buildPausedLine(info, enabled, strings))
        assertEquals("⏸︎ Paused: Song", DiscordActivityBuilder.buildPausedLine(info, DiscordAdvancedSettings.DEFAULTS, strings))
    }

    @Test
    fun `idle preview hides the disabled sections in advanced mode`() {
        val settings = DiscordAdvancedSettings.DEFAULTS.copy(
            advancedMode = true,
            showState = false,
            showDetails = false,
        )
        val content = DiscordActivityBuilder.buildIdlePreview(settings, strings)
        assertEquals("", content.state, "the preview must honor the disabled state section")
        assertEquals("", content.details, "the preview must honor the disabled details section")
        assertEquals("N-Zik", content.name)
    }
}
