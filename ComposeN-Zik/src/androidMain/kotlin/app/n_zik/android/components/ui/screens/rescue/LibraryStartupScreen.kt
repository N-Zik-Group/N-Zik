package app.n_zik.android.components.ui.screens.rescue

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.core.backup.LibraryStartupState

@Composable
internal fun LibraryStartupScreen(state: LibraryStartupState, retry: () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (state is LibraryStartupState.Failed) {
                    Text(
                        text = stringResource(R.string.backup_startup_recovery_failed),
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = retry) {
                        Text(stringResource(R.string.common_action_retry))
                    }
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.backup_startup_preparing_library))
                }
            }
        }
    }
}
