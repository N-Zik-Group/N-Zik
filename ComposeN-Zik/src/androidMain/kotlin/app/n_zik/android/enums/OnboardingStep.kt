package app.n_zik.android.enums

/**
 * Steps of the first-launch onboarding flow, held as an activity field by
 * [app.n_zik.android.MainActivity]. A `null` step outside the flow means the
 * flow is complete and the main navigation is rendered instead.
 */
enum class OnboardingStep {
    PERMISSIONS,
    IMPORT,
    NAME,
    ACCOUNTS;

    /**
     * The step shown after this one, or `null` when the flow is complete.
     * The onboarding-complete flag is written only when the flow actually
     * completes (leaving [ACCOUNTS]), never by [advance] itself.
     */
    fun advance(): OnboardingStep? = when (this) {
        PERMISSIONS -> IMPORT
        IMPORT -> NAME
        NAME -> ACCOUNTS
        ACCOUNTS -> null
    }
}
