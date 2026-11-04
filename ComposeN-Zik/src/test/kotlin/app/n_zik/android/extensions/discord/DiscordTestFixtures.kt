package app.n_zik.android.extensions.discord

import android.content.Context
import app.n_zik.android.R
import io.mockk.every
import io.mockk.mockk

/**
 * Test context that resolves the Discord presence strings to their English
 * `strings_accounts_sync.xml` values. The [DiscordPresenceManager] resolves them via
 * `context.getString(...)` (item 6: no hardcoded user-facing strings), and a plain
 * relaxed mock would answer empty strings, breaking the presence-content assertions.
 */
internal fun discordTestContext(): Context {
    val context = mockk<Context>(relaxed = true)
    every { context.getString(R.string.discord_presence_name) } returns "N-Zik"
    every { context.getString(R.string.discord_presence_button_get_nzik) } returns "Get N-Zik"
    every { context.getString(R.string.discord_presence_button_listen_ytmusic) } returns "Listen to YTMusic"
    every { context.getString(R.string.discord_presence_pause_default) } returns "⏸︎ Paused: {song.name}"
    every { context.getString(R.string.discord_template_unknown_album) } returns "Unknown Album"
    return context
}
