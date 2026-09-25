package app.n_zik.android.components.onboarding

import app.n_zik.android.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Tests [onboardingAccountButtonResId]: the single generic login/logoff pair
 * shared by the three onboarding menus (Last.fm, Discord, YouTube) — one key
 * per action so every card reads identically in every locale (spec I/O matrix).
 */
class OnboardingAccountButtonTest {

    @Test
    fun notConnectedMapsToLogin() {
        assertEquals(R.string.account_login, onboardingAccountButtonResId(isConnected = false))
    }

    @Test
    fun connectedMapsToLogoff() {
        assertEquals(R.string.account_logoff, onboardingAccountButtonResId(isConnected = true))
    }

    @Test
    fun bothStatesMapToDistinctResources() {
        assertNotEquals(
            onboardingAccountButtonResId(isConnected = false),
            onboardingAccountButtonResId(isConnected = true)
        )
    }
}
