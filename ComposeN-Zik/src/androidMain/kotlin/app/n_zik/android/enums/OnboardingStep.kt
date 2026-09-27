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

    companion object {
        /**
         * Resolves the onboarding step to render at activity start from the persisted
         * state. Returns `null` (the main app) when the flow is complete. Otherwise the
         * persisted step — a post-import restart or process death resumes the flow
         * there, a restore included (it advances the flow but never completes the
         * onboarding, so the restart lands on the next step) — or the first step when
         * nothing is persisted (fresh install).
         */
        fun resolveStartupStep(complete: Boolean, persistedStepName: String): OnboardingStep? {
            if (complete) return null
            return entries.firstOrNull { it.name == persistedStepName } ?: PERMISSIONS
        }
    }
}
