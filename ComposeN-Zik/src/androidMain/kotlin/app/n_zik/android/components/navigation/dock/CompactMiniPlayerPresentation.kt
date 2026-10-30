package app.n_zik.android.components.navigation.dock

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf

/** Shared presentation clock; playback and gestures remain owned by the existing mini-player. */
val LocalCompactMiniPlayerProgress = compositionLocalOf { 0f }

/** Sample inside the mini subtree, never in the activity's composition. */
val LocalCompactMiniPlayerProgressProvider = staticCompositionLocalOf<(() -> Float)?> { null }

/** Independent compact actions; expanded mini-player preferences retain their existing keys. */
const val compactMiniPlayerSlot1Key = "compactMiniPlayerSlot1"
const val compactMiniPlayerSlot2Key = "compactMiniPlayerSlot2"
