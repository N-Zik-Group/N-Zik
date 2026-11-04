package app.n_zik.android.utils

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

sealed interface SyncStatusDetail {
    data class Text(@StringRes val resource: Int) : SyncStatusDetail
    data class Count(@PluralsRes val resource: Int, val count: Int) : SyncStatusDetail
    data class PushResult(val succeeded: Int, val failed: Int) : SyncStatusDetail
}
