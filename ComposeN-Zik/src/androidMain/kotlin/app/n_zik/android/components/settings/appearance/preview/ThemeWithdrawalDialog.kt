package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.settings.accounts.GitHubAccount
import app.n_zik.android.components.settings.accounts.rememberGitHubPublicationAuthorization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
internal fun ThemeWithdrawalDialog(theme: StoreTheme, profileId: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountId = theme.author.githubId
    var sending by remember { mutableStateOf(false) }
    val awaiting by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    val close = { if (!sending && !awaiting) dismiss() }
    val submit: () -> Unit = {
        sending = true
        Toaster.i(R.string.theme_store_submission_sending)
        scope.launch {
            try {
                if (getActiveProfile(context) != profileId || GitHubAccount.userId(context) != accountId) {
                    throw CreatorPublicationAccountMismatch()
                }
                val token = GitHubAccount.tokenFor(context, accountId) ?: throw CreatorPublicationSessionExpired()
                val url = submitCreatorProfile(token, accountId, emptyMap(), withdrawal = theme)
                val number = url.substringAfterLast('/').toInt()
                context.getSharedPreferences("theme_creator_submissions", 0).edit()
                    .putString("$accountId:$number", "open").apply()
                notifyCreatorSubmissionSent(context, accountId, number, R.string.theme_store_withdraw_submitted)
                Toaster.s(R.string.theme_store_withdraw_submitted)
                dismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (blocked: CreatorPublicationBlocked) {
                Toaster.e(blocked.publicMessage)
            } catch (_: CreatorPublicationWorkflowPermission) {
                GitHubAccount.requireWorkflowAuthorization(context)
                Toaster.i(R.string.theme_store_creator_workflow_authorization)
            } catch (_: CreatorPublicationSessionExpired) {
                GitHubAccount.clearPublicationToken(context)
                Toaster.i(R.string.theme_store_creator_session_expired)
            } catch (_: CreatorPublicationUnchanged) {
                Toaster.i(R.string.theme_store_withdraw_missing)
                dismiss()
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Theme withdrawal submission failed")
                Toaster.e(R.string.theme_store_withdraw_error)
            } finally {
                sending = false
            }
        }
    }
    val authorizePublication = rememberGitHubPublicationAuthorization(submit)

    DefaultDialog(
        title = stringResource(R.string.theme_store_withdraw),
        onDismiss = close,
        footer = {
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.common_action_cancel), close, danger = true, enabled = !sending && !awaiting),
                DialogButton(stringResource(R.string.theme_store_withdraw_submit), {
                    if (GitHubAccount.tokenFor(context, accountId) != null) submit()
                    else authorizePublication(accountId)
                }, primary = true, enabled = !sending && !awaiting),
            ))
        },
    ) {
        SettingsDescription(stringResource(R.string.theme_store_withdraw_confirm, theme.title))
    }
}
