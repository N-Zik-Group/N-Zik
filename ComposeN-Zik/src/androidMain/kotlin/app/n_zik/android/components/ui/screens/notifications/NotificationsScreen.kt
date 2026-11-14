package app.n_zik.android.components.ui.screens.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import app.n_zik.android.core.navigation.LocalRevealScaffold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.styling.Dimensions
import kotlinx.coroutines.delay

internal fun List<StatusBarNotification>.inboxEntries() = filter {
    val channel = it.notification.channelId
    val playbackChannel = PlayerServiceModern.NotificationChannelId
    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
        it.notification.category != Notification.CATEGORY_TRANSPORT &&
        channel != playbackChannel && channel?.startsWith("${playbackChannel}_") != true
}.sortedByDescending { it.postTime }

internal const val InboxPreferences = "notification_inbox"
internal const val InboxReadKey = "read"
internal fun StatusBarNotification.inboxIdentity() = "$key:$postTime"

@OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class,
)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun NotificationsScreen(navController: NavController, miniPlayer: @Composable () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(NotificationManager::class.java) }
    val preferences = remember(context) { context.getSharedPreferences(InboxPreferences, 0) }
    var entries by remember(manager) { mutableStateOf(manager.activeNotifications.toList().inboxEntries()) }
    var read by remember(preferences) { mutableStateOf(preferences.getStringSet("read", emptySet()).orEmpty()) }
    fun identity(entry: StatusBarNotification) = entry.inboxIdentity()
    LaunchedEffect(manager, preferences) {
        val visible = manager.activeNotifications.toList().inboxEntries().map(::identity).toSet()
        read = visible
        preferences.edit().putStringSet(InboxReadKey, read).apply()
    }
    fun sendAction(entry: StatusBarNotification, intent: PendingIntent, opensNotification: Boolean = false) {
        try {
            intent.send()
        } catch (_: PendingIntent.CanceledException) {
            entries = manager.activeNotifications.toList().inboxEntries()
            return
        }
        read = read + identity(entry)
        preferences.edit().putStringSet("read", read).apply()
        if (opensNotification && entry.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) {
            manager.cancel(entry.tag, entry.id)
        }
        entries = manager.activeNotifications.toList().inboxEntries()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(manager, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val active = manager.activeNotifications.toList().inboxEntries()
                entries = active
                delay(1_000)
            }
        }
    }

    val floating = NavigationBarPosition.BottomFloating.isCurrent()
    val revealScaffold = LocalRevealScaffold.current
    val bottom = Dimensions.navBarBottomPadding(floating) + if (floating) 10.dp else 15.dp
    val title = stringResource(R.string.notification_inbox_title)
    Skeleton(navController = navController, topInsetHandledByContent = true, miniPlayer = miniPlayer,
        navBarContent = { item -> item(0, title, R.drawable.notification2) }) { _ ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = bottom))) {
            item {
                HeaderWithIcon(title = title, iconId = R.drawable.notification2, enabled = false, modifier = Modifier, onClick = {})
            }
            if (entries.isNotEmpty()) {
                items(entries, key = { it.key }) { entry ->
                    val extras = entry.notification.extras
                    val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                    val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
                    val previewText = listOf(message, subText).filter(String::isNotBlank).distinct().joinToString("\n")
                    val detail = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: message
                    val notificationTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().takeUnless { it.isNullOrBlank() }
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    var expanded by remember(entry.key) { mutableStateOf(false) }
                    val destination = entry.notification.contentIntent
                    val actions = entry.notification.actions.orEmpty().filter {
                        it.actionIntent != null && it.remoteInputs.isNullOrEmpty()
                    }
                    val extraLines = buildList {
                        add(subText)
                        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().forEach { add(it.toString()) }
                        extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.let(::add)
                    }.filter { it.isNotBlank() && it != message && it != detail }.distinct()
                    val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
                    val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
                    val swipeState = rememberSwipeToDismissBoxState()
                    val age = android.text.format.DateUtils.getRelativeTimeSpanString(
                        entry.postTime, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                    SwipeToDismissBox(
                        state = swipeState,
                        modifier = Modifier.animateItem(fadeInSpec = tween(240), fadeOutSpec = tween(200)),
                        enableDismissFromStartToEnd = entry.isClearable,
                        enableDismissFromEndToStart = entry.isClearable,
                        backgroundContent = {
                            val direction = swipeState.dismissDirection
                            if (direction != SwipeToDismissBoxValue.Settled) {
                                Box(Modifier.fillMaxSize().padding(horizontal = 28.dp),
                                    contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                                ) { Icon(
                                    painterResource(R.drawable.trash), contentDescription = null,
                                    tint = colorPalette().red,
                                ) }
                            }
                        },
                        onDismiss = {
                            if (entry.isClearable) {
                                manager.cancel(entry.tag, entry.id)
                                entries = entries.filterNot { it.key == entry.key }
                                read = read - identity(entry)
                                preferences.edit().putStringSet("read", read).apply()
                            }
                        },
                    ) {
                    SettingsSectionCard(
                        title = notificationTitle.ifBlank { title },
                        icon = R.drawable.notification2,
                        modifier = Modifier.alpha(if (identity(entry) in read) 0.7f else 1f),
                        expanded = expanded,
                        onHeaderClick = { expanded = !expanded },
                        description = previewText.takeUnless { expanded },
                        headerAction = {
                            BasicText(age, style = typography().xxs.copy(color = colorPalette().textSecondary))
                        },
                        content = {
                            if (message.isNotBlank()) {
                                BasicText(message, style = typography().s.copy(color = colorPalette().textSecondary),
                                    modifier = Modifier.padding(start = 44.dp))
                            }
                            if (detail.isNotBlank() && detail != message) {
                                BasicText(detail, style = typography().s.copy(color = colorPalette().textSecondary),
                                    modifier = Modifier.padding(start = 44.dp, top = 4.dp))
                            }
                            extraLines.forEach { line ->
                                BasicText(line, style = typography().s.copy(color = colorPalette().textSecondary),
                                    modifier = Modifier.padding(start = 44.dp, top = 4.dp))
                            }
                            if (indeterminate || progressMax > 0) {
                                val progressModifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 8.dp).height(8.dp)
                                if (indeterminate) {
                                    LinearWavyProgressIndicator(modifier = progressModifier, color = colorPalette().accent, trackColor = colorPalette().background2)
                                } else {
                                    val progress = extras.getInt(Notification.EXTRA_PROGRESS).coerceIn(0, progressMax)
                                    LinearWavyProgressIndicator(progress = { progress.toFloat() / progressMax },
                                        modifier = progressModifier, color = colorPalette().accent, trackColor = colorPalette().background2)
                                }
                            }
                            val buttons = buildList {
                                if (destination != null && destination !in actions.map { it.actionIntent }) {
                                    add(DialogButton(stringResource(R.string.notification_inbox_open), {
                                        sendAction(entry, destination, opensNotification = true)
                                    }))
                                }
                                actions.forEach { action ->
                                    val actionIntent = action.actionIntent ?: return@forEach
                                    add(DialogButton(action.title.toString(), {
                                        sendAction(entry, actionIntent)
                                    }))
                                }
                            }
                            if (buttons.isNotEmpty()) {
                                ButtonRow(buttons, Modifier.padding(top = 8.dp))
                            }
                        },
                    )
                    }
                }
                if (entries.any { it.isClearable }) item(key = "notification-inbox-clear") {
                    ButtonRow(
                        buttons = listOf(DialogButton(stringResource(R.string.notification_inbox_clear_all), {
                            val clearable = entries.filter { it.isClearable }
                            clearable.forEach { manager.cancel(it.tag, it.id) }
                            entries = entries.filterNot { it.isClearable }
                            read = read - clearable.map(::identity).toSet()
                            preferences.edit().putStringSet("read", read).apply()
                            revealScaffold()
                        })),
                        modifier = Modifier.animateItem(fadeInSpec = tween(240), fadeOutSpec = tween(200))
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
            AnimatedVisibility(
                visible = entries.isEmpty(),
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(200, delayMillis = 200)),
                exit = fadeOut(tween(120)),
            ) {
                SettingsDescription(text = stringResource(R.string.notification_inbox_empty))
            }
        }
    }
}
