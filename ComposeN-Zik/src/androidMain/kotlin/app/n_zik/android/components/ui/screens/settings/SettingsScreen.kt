package app.n_zik.android.components.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.withContext

import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.search.SearchResultsViewport
import app.n_zik.android.components.settings.about.About
import app.n_zik.android.components.settings.accounts.AccountsSettings
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.components.settings.accounts.isYouTubeLoginEnabled
import app.n_zik.android.components.settings.ai.AIRecommendationSettings
import app.n_zik.android.components.settings.appearance.AppearanceSettings
import app.n_zik.android.components.settings.common.SETTINGS_GLASS_ROUTE
import app.n_zik.android.components.settings.common.SETTINGS_THEME_STORE_ROUTE
import app.n_zik.android.components.settings.common.SettingsCategoryPage
import app.n_zik.android.components.settings.common.SettingsGlobalPage
import app.n_zik.android.components.settings.common.SettingsPageItem
import app.n_zik.android.components.settings.common.categoryRoute
import app.n_zik.android.components.settings.common.destinationRoute
import app.n_zik.android.components.settings.common.settingsShortcutRoute
import app.n_zik.android.components.settings.data.DataSettings
import app.n_zik.android.components.settings.general.GeneralSettings
import app.n_zik.android.components.settings.network.NetworkSettings
import app.n_zik.android.components.settings.other.OtherSettings
import app.n_zik.android.components.settings.ui.GlassSettingsPage
import app.n_zik.android.components.settings.ui.UiSettings
import app.n_zik.android.core.navigation.LocalSettingsBackNavigation
import app.n_zik.android.core.navigation.RevealScaffoldOnNavigation
import app.n_zik.android.core.navigation.ScreenTransitions
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.encryptedPreferencesUpdateTrigger
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.transitionEffectKey

/** Global page, the inner start destination. */
private const val SETTINGS_GLOBAL_ROUTE = "settingsGlobal"

/** One internal destination per settings tab, with the tab and focus as params. */
private const val SETTINGS_TAB_ROUTE = "settingsTab"

private fun settingsTabRoute(tab: Int, focus: String): String =
    "$SETTINGS_TAB_ROUTE?tab=$tab" + if (focus.isEmpty()) "" else "&focus=$focus"

/** Nested settings destinations retain their category back stack. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    initialTab: Int = 0,
    focus: String = "",
) {
    val context = LocalContext.current
    val innerNavController = rememberNavController()
    RevealScaffoldOnNavigation(innerNavController)
    val settingsBackNavigation = LocalSettingsBackNavigation.current
    val backOwner = remember { Any() }
    val innerEntry by innerNavController.currentBackStackEntryAsState()
    val handleBack by rememberUpdatedState<() -> Unit>({
        if (innerEntry?.destination?.route == SETTINGS_GLOBAL_ROUTE || !innerNavController.popBackStack()) {
            if (!navController.popBackStack(NavRoutes.home.name, inclusive = false)) {
                navController.navigate(NavRoutes.home.name) {
                    popUpTo(NavRoutes.home.name) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    })
    DisposableEffect(settingsBackNavigation, backOwner) {
        settingsBackNavigation?.register(
            backOwner,
            canNavigateBack = {
                innerEntry != null && innerEntry?.destination?.route != SETTINGS_GLOBAL_ROUTE
            },
        ) { handleBack() }
        onDispose { settingsBackNavigation?.unregister(backOwner) }
    }

    val trigger = encryptedPreferencesUpdateTrigger
    var ytLoggedIn by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        ytLoggedIn = withContext(NzikDispatchers.DATA) {
            isYouTubeLoginEnabled() && isYouTubeLoggedIn()
        }
    }

    var openedShortcut by rememberSaveable(initialTab, focus) { mutableStateOf(false) }
    LaunchedEffect(initialTab, focus) {
        if (!openedShortcut && (initialTab != 0 || focus.isNotEmpty())) {
            val route = settingsShortcutRoute(context, initialTab, focus) ?: settingsTabRoute(initialTab, focus)
            innerNavController.navigate(route)
            openedShortcut = true
        }
    }

    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    val transitions = remember(transitionEffect) { ScreenTransitions(transitionEffect) }

    SearchResultsViewport {
        NavHost(
            navController = innerNavController,
            startDestination = SETTINGS_GLOBAL_ROUTE,
            enterTransition = transitions.enterTransition,
            exitTransition = transitions.exitTransition,
            popEnterTransition = transitions.popEnterTransition,
            popExitTransition = transitions.popExitTransition,
            predictivePopEnterTransition = transitions.predictivePopEnterTransition,
            predictivePopExitTransition = transitions.predictivePopExitTransition,
        ) {
            composable(
                route = "$SETTINGS_GLASS_ROUTE?target={target}",
                arguments = listOf(navArgument("target") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                GlassSettingsPage(
                    target = entry.arguments?.getString("target").orEmpty(),
                    onSearchTarget = { id -> innerNavController.navigate("$SETTINGS_GLASS_ROUTE?target=${android.net.Uri.encode(id)}") },
                )
            }
            composable(route = SETTINGS_GLOBAL_ROUTE) {
                SettingsGlobalPage(
                    onItemSelected = { item: SettingsPageItem ->
                        item.categoryId?.let { id ->
                            innerNavController.navigate("settingsCategory/$id")
                        } ?: item.tab?.let { tab ->
                            innerNavController.navigate(settingsTabRoute(tab, ""))
                        } ?: item.route?.let { route ->
                            when (route) {
                                SETTINGS_GLASS_ROUTE -> innerNavController.navigate(route)
                                SETTINGS_THEME_STORE_ROUTE -> navController.navigate(NavRoutes.themeStore.name)
                                else -> navController.navigate(route)
                            }
                        }
                    },
                    onCardSelected = { tab, cardFocus ->
                        innerNavController.navigate(settingsTabRoute(tab, cardFocus))
                    },
                    onEntrySelected = { result ->
                        innerNavController.navigate(result.destinationRoute(context))
                    },
                    onOrganizedCardSelected = { card ->
                        innerNavController.navigate(card.categoryRoute(context))
                    },
                )
            }
            composable(
                route = "settingsCategory/{category}?target={target}&parent={parent}",
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType },
                    navArgument("target") { type = NavType.StringType; defaultValue = "" },
                    navArgument("parent") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                SettingsCategoryPage(
                    categoryId = entry.arguments?.getString("category").orEmpty(),
                    navController = navController,
                    target = entry.arguments?.getString("target").orEmpty(),
                    parentTarget = entry.arguments?.getString("parent").orEmpty(),
                    onSearchResultNavigate = { route -> innerNavController.navigate(route) },
                )
            }
            composable(
                route = "$SETTINGS_TAB_ROUTE?tab={tab}&focus={focus}",
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("focus") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                )
            ) { entry ->
                val tab = entry.arguments?.getInt("tab", 0) ?: 0
                val tabFocus = entry.arguments?.getString("focus", "") ?: ""
                when (tab) {
                    0 -> GeneralSettings(focus = tabFocus)
                    1 -> UiSettings(
                        navController = navController,
                        focus = tabFocus,
                        onOpenGlass = { innerNavController.navigate(SETTINGS_GLASS_ROUTE) },
                    )
                    2 -> AppearanceSettings(navController = navController)
                    3 -> AIRecommendationSettings(
                        navController = navController,
                        ytLoggedIn = ytLoggedIn,
                        focus = tabFocus,
                    )
                    4 -> DataSettings(navController = navController, focus = tabFocus)
                    5 -> AccountsSettings(navController = navController, focus = tabFocus)
                    6 -> NetworkSettings(navController = navController, focus = tabFocus)
                    7 -> OtherSettings(focus = tabFocus)
                    8 -> About(navController = navController)
                }
            }
        }
    }

    RestartAppDialog.Render()
}
