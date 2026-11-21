package app.n_zik.android.components.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue

import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.common.SamsungStorageAdviceDialog
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.LocalGlassScope
import app.n_zik.android.enums.OnboardingStep
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.transitionEffectKey

@Composable
internal fun OnboardingFlow(
    step: OnboardingStep?,
    onAdvance: () -> Unit,
    libraryContent: @Composable () -> Unit,
) {
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            when (transitionEffect) {
                TransitionEffect.None ->
                    EnterTransition.None togetherWith ExitTransition.None

                TransitionEffect.Expand ->
                    scaleIn(animationSpec = tween(350), initialScale = 2.0f) togetherWith
                    scaleOut(animationSpec = tween(350), targetScale = 2.0f)

                TransitionEffect.Fade ->
                    fadeIn(animationSpec = tween(350)) togetherWith
                    fadeOut(animationSpec = tween(350))

                TransitionEffect.Scale ->
                    scaleIn(animationSpec = tween(350)) togetherWith
                    scaleOut(animationSpec = tween(350))

                TransitionEffect.SlideVertical ->
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up)

                TransitionEffect.SlideHorizontal ->
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
            }
        },
        label = "onboardingPhase",
    ) { phase ->
        CompositionLocalProvider(
            LocalGlassScope provides if (phase == null) GlassScope.Shared else GlassScope.Onboarding,
        ) {
            when (phase) {
                null -> libraryContent()
                OnboardingStep.GLASS -> OnboardingGlassScreen(onComplete = onAdvance)
                OnboardingStep.PERMISSIONS -> OnboardingScreen(onComplete = onAdvance)
                OnboardingStep.IMPORT -> OnboardingImportScreen(onComplete = onAdvance)
                OnboardingStep.ACCOUNTS -> OnboardingAccountsScreen(onComplete = onAdvance)
                OnboardingStep.NAME -> OnboardingProfileScreen(onComplete = onAdvance)
            }
        }
    }

    // Keep the restart prompt alive as its originating step leaves the composition.
    if (step != null) RestartAppDialog.Render()
    if (step == null) SamsungStorageAdviceDialog.Render()
}
