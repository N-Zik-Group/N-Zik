package app.n_zik.android.components.ui.screens.rewind

import com.mikepenz.hypnoticcanvas.shaders.BlackCherryCosmos
import com.mikepenz.hypnoticcanvas.shaders.GoldenMagma
import com.mikepenz.hypnoticcanvas.shaders.GradientFlow
import com.mikepenz.hypnoticcanvas.shaders.Heat
import com.mikepenz.hypnoticcanvas.shaders.InkFlow
import com.mikepenz.hypnoticcanvas.shaders.OilFlow
import com.mikepenz.hypnoticcanvas.shaders.PurpleLiquid
import com.mikepenz.hypnoticcanvas.shaders.Shader
import com.mikepenz.hypnoticcanvas.shaders.Stage
import com.mikepenz.hypnoticcanvas.shaders.Stripy

/**
 * Animated shader backgrounds for the Rewind slides.
 *
 * Ported from the RiPlay Rewind (GPL-3.0, original in docs/RiPlay); the HypnoticCanvas
 * library itself is Apache-2.0 / MIT. Indexed by deck page so every slide keeps a
 * distinct ambient motion while the solid slide color stays on top as a tint.
 */
internal val rewindSlideShaders: List<Shader> = listOf(
    GradientFlow,        // 0  Intro
    PurpleLiquid,        // 1  Listener badge
    Heat(),              // 2  Total time
    Stage,               // 3  Top song
    Stripy(),            // 4  Top artists
    BlackCherryCosmos,   // 5  Artist spotlight
    InkFlow,             // 6  Top songs
    OilFlow,             // 7  Deep cuts
    GoldenMagma,         // 8  Peak time
    GradientFlow,        // 9  Listening days
    PurpleLiquid,        // 10 Discovery
    Stage,               // 11 Top album
    Stripy(),            // 12 Albums
    OilFlow,             // 13 Top playlists
    Heat(),              // 14 Song achievement
    InkFlow,             // 15 Album achievement
    Stage,               // 16 Artist achievement
    PurpleLiquid,        // 17 Playlist achievement
    InkFlow,             // 18 Monthly
    BlackCherryCosmos    // 19 Finale
)
