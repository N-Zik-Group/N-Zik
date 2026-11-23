package app.n_zik.android.components.ui.screens.rescue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.components.ui.chipsViewport

@Composable
internal fun RescueCategorySelector(
    selectedCategory: RescueCategory,
    onSelect: (RescueCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        RescueCategoryChip(
            label = stringResource(R.string.recovery_category_all),
            selected = selectedCategory == RescueCategory.ALL,
            onSelect = { onSelect(RescueCategory.ALL) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        RescueCategoryChip(
            label = stringResource(R.string.recovery_category_profile),
            selected = selectedCategory == RescueCategory.PER_PROFILE,
            onSelect = { onSelect(RescueCategory.PER_PROFILE) }
        )
    }
}

@Composable
internal fun RescueTargetProfileSelector(
    profileOptions: List<ProfileOption>,
    targetProfile: String,
    onSelect: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.recovery_profile_target_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .chipsViewport(16.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            profileOptions.forEach { option ->
                val selected = option.id == targetProfile
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(option.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (option.isActive) {
                        Text(
                            text = stringResource(R.string.recovery_profile_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (selected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                            ).copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}
