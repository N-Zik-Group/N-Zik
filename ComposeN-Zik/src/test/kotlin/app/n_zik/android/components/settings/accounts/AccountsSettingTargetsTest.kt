package app.n_zik.android.components.settings.accounts

import app.n_zik.android.components.settings.useLoginForSearchKey
import app.n_zik.android.extensions.discord.discordAdvancedSettingKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsSettingTargetsTest {
    @Test fun inventoryExcludesRemovedProfileStorageCard() {
        assertEquals(
            listOf("face", "youtube", "sync", "autosync", "sync_import", "sync_push", "lastfm", "listentogether", "discord", "reset"),
            AccountsSettingTargets.cards.map { it.id.removePrefix("accounts.card.") },
        )
    }

    @Test fun everyTargetIsUniqueAndRemainsStableWhenCardOrderChanges() {
        val ids = AccountsSettingTargets.ids
        assertEquals(ids.size, ids.toSet().size)
        val reordered = AccountsSettingTargets.cards.reversed().flatMap { card ->
            listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } +
                card.actions.map { "${card.id}.action.$it" }
        }
        assertEquals(ids.toSet(), reordered.toSet())
    }

    @Test fun embeddedSearchAndAllDiscordAdvancedControlsHaveTargets() {
        val youtube = AccountsSettingTargets.cards.single { it.id == "accounts.card.youtube" }
        val discord = AccountsSettingTargets.cards.single { it.id == "accounts.card.discord" }
        assertTrue(youtube.preferenceKeys.contains(useLoginForSearchKey))
        assertTrue(discord.preferenceKeys.containsAll(discordAdvancedSettingKeys))
    }
}
