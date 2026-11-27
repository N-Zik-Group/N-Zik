package app.n_zik.android.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavBackStackEntry
import app.n_zik.android.enums.TransitionEffect

/** The app-wide navigation policy, shared by the main host and nested Settings host. */
class ScreenTransitions(transitionEffect: TransitionEffect) {
    val enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = {
        screenEnter(transitionEffect)
    }
    val exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = {
        screenExit(transitionEffect)
    }
    val popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = {
        screenPopEnter(transitionEffect)
    }
    val popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = {
        screenPopExit(transitionEffect)
    }

    // Navigation has separate predictive callbacks; keep their edge-independent policy identical.
    val predictivePopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition =
        { popEnterTransition() }
    val predictivePopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition =
        { popExitTransition() }
}

internal fun <T> AnimatedContentTransitionScope<T>.screenEnter(effect: TransitionEffect): EnterTransition =
    when (effect) {
        TransitionEffect.None -> EnterTransition.None
        TransitionEffect.Expand -> scaleIn(animationSpec = tween(350), initialScale = 2.0f)
        TransitionEffect.Fade -> fadeIn(animationSpec = tween(350))
        TransitionEffect.Scale -> scaleIn(animationSpec = tween(350))
        TransitionEffect.SlideVertical -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up)
        TransitionEffect.SlideHorizontal -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
    }

internal fun <T> AnimatedContentTransitionScope<T>.screenExit(effect: TransitionEffect): ExitTransition =
    when (effect) {
        TransitionEffect.None -> ExitTransition.None
        TransitionEffect.Expand -> scaleOut(animationSpec = tween(350), targetScale = 2.0f)
        TransitionEffect.Fade -> fadeOut(animationSpec = tween(350))
        TransitionEffect.Scale -> scaleOut(animationSpec = tween(350))
        TransitionEffect.SlideVertical -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up)
        TransitionEffect.SlideHorizontal -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left)
    }

internal fun <T> AnimatedContentTransitionScope<T>.screenPopEnter(effect: TransitionEffect): EnterTransition =
    when (effect) {
        TransitionEffect.None -> EnterTransition.None
        TransitionEffect.Expand -> scaleIn(animationSpec = tween(350), initialScale = 2.0f)
        TransitionEffect.Fade -> fadeIn(animationSpec = tween(350))
        TransitionEffect.Scale -> scaleIn(animationSpec = tween(350))
        TransitionEffect.SlideVertical -> EnterTransition.None
        TransitionEffect.SlideHorizontal -> EnterTransition.None
    }

internal fun <T> AnimatedContentTransitionScope<T>.screenPopExit(effect: TransitionEffect): ExitTransition =
    when (effect) {
        TransitionEffect.None -> ExitTransition.None
        TransitionEffect.Expand -> scaleOut(animationSpec = tween(350), targetScale = 2.0f)
        TransitionEffect.Fade -> fadeOut(animationSpec = tween(350))
        TransitionEffect.Scale -> scaleOut(animationSpec = tween(350))
        TransitionEffect.SlideVertical -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down)
        TransitionEffect.SlideHorizontal -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right)
    }
