package app.n_zik.android.components.onboarding

import android.content.Context
import timber.log.Timber

import app.n_zik.android.enums.OnboardingStep
import app.n_zik.android.utils.DataStoreUtils

internal fun Context.readOnboardingStep(): OnboardingStep? =
    OnboardingStep.resolveStartupStep(
        complete = DataStoreUtils.getBoolean(this, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false),
        persistedStepName = DataStoreUtils.getString(this, DataStoreUtils.KEY_ONBOARDING_STEP, ""),
    )

/** Persists the next phase before its caller can restart the process. */
internal fun Context.persistNextOnboardingStep(current: OnboardingStep): OnboardingStep? {
    val next = current.advance()
    if (next == null) {
        DataStoreUtils.saveBoolean(this, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
        DataStoreUtils.saveString(this, DataStoreUtils.KEY_ONBOARDING_STEP, "")
        Timber.tag("MainActivity").i("Onboarding complete, flag written, step cleared")
    } else {
        DataStoreUtils.saveString(this, DataStoreUtils.KEY_ONBOARDING_STEP, next.name)
        Timber.tag("MainActivity").d("Onboarding step: ${current.name} -> ${next.name}")
    }
    return next
}
