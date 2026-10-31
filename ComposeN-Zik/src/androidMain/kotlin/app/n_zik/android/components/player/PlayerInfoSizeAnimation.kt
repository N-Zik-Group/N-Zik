/*
 * Adapted from AndroidX AnimationModifier.kt.
 * Copyright 2020 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package app.n_zik.android.components.player

import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.unclippedGlassContentSize
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial

/** Retains sizing while letting metadata and repositioned transport overdraw their slots. */
@Composable
fun Modifier.playerInfoAwareContentSize(
    modernInfo: Boolean,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerInformation),
): Modifier {
    val settings = settings.inScope(GlassScope.PlayerInformation)
    val iconsVisible by rememberPreference(playerInfoShowIconsKey, true)
    val railVisible = modernInfo && iconsVisible && (settings.usesGlassMaterial(GlassArea.ActionButtons) ||
        settings.usesGlassContour(GlassArea.ActionButtons))
    // PlayerTransportCenter can move the transport beyond this measured controls box.
    // Essential metadata and hidden icon rails need that overflow just as Modern rails do.
    return if (settings.enabled || railVisible || LocalPlayerTransportGeometry.current != null)
        unclippedGlassContentSize() else animateContentSize()
}
