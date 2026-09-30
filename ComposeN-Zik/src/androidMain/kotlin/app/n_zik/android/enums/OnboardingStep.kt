package app.n_zik.android.enums

/**
 * Steps of the first-launch onboarding flow, held as an activity field by
 * [app.n_zik.android.MainActivity]. A `null` step outside the flow means the
 * flow is complete and the main navigation is rendered instead.
 */
enum class OnboardingStep {
    PERMISSIONS,
    IMPORT,
    ACCOUNTS,
    NAME;

    /**
     * The step shown after this one, or `null` when the flow is complete.
     * The onboarding-complete flag is written only when the flow actually
     * completes (leaving [NAME]), never by [advance] itself. The accounts step
     * precedes the profile step (user decision): the account face sources must
     * be unlockable when the clone's identity is chosen. The enum NAMES are what
     * the persisted step holds, so an in-flight onboarding resumes at the step
     * bearing the same NAME in the current build — a step persisted by a previous
     * build order can land on a different position (an old persisted NAME skips
     * the accounts step the new order inserted before it); accepted: onboarding is
     * short and the accounts step is optional.
     */
    fun advance(): OnboardingStep? = when (this) {
        PERMISSIONS -> IMPORT
        IMPORT -> ACCOUNTS
        ACCOUNTS -> NAME
        NAME -> null
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
