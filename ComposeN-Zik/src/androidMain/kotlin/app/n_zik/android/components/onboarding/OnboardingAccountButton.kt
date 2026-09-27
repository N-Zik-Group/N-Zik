package app.n_zik.android.components.onboarding

import app.n_zik.android.R

/**
 * Maps an account connection state to the string id of the account card button.
 *
 * All three onboarding menus (Last.fm, Discord, YouTube) share this single
 * generic login/logoff pair, so every card reads identically in every locale.
 *
 * Pure on purpose (unit-tested in [app.n_zik.android.components.onboarding]).
 */
fun onboardingAccountButtonResId(isConnected: Boolean): Int =
    if (isConnected) R.string.account_logoff else R.string.account_login
