package app.n_zik.android.components.settings.accounts

import app.n_zik.android.R

internal fun discordSettingsCardTitle(resolveString: (Int) -> String): String =
    "${resolveString(R.string.settings_discord_title)} ${resolveString(R.string.updates_build_beta_label)}"

internal enum class DiscordButtonField(val targetId: String, private val buttonRes: Int, val fieldRes: Int) {
    FirstLabel("accounts.social_discord.discord_advanced_button1.label", R.string.discord_advanced_button1, R.string.discord_advanced_button_label),
    FirstUrl("accounts.social_discord.discord_advanced_button1.url", R.string.discord_advanced_button1, R.string.discord_advanced_button_url),
    SecondLabel("accounts.social_discord.discord_advanced_button2.label", R.string.discord_advanced_button2, R.string.discord_advanced_button_label),
    SecondUrl("accounts.social_discord.discord_advanced_button2.url", R.string.discord_advanced_button2, R.string.discord_advanced_button_url);

    fun title(resolveString: (Int) -> String, separator: String = " - "): String =
        "${resolveString(buttonRes)}$separator${resolveString(fieldRes)}"

    companion object {
        fun forTarget(targetId: String): DiscordButtonField? = entries.firstOrNull { it.targetId == targetId }
    }
}

internal fun discordSettingsSearchFallback(targetId: String, isVisible: (String) -> Boolean): String? {
    val prefix = "accounts.social_discord."
    if (!targetId.startsWith(prefix)) return null
    val master = "${prefix}discord_enable_rich_presence"
    val advanced = "${prefix}discord_advanced_mode"
    val alternate = when (targetId) {
        "${prefix}account_login" -> "${prefix}account_logoff"
        "${prefix}account_logoff" -> "${prefix}account_login"
        else -> DiscordButtonField.forTarget(targetId)?.targetId?.substringBeforeLast('.')
    }
    return listOfNotNull(alternate, advanced.takeIf { targetId != it }, master.takeIf { targetId != it })
        .firstOrNull(isVisible)
}
