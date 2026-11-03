package app.it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.enums.TextView
import app.n_zik.android.R

enum class RecommendationsNumber: TextView {
    `5`,
    `10`,
    `15`,
    `20`,
    `30`,
    `50`,
    `100`,
    Adaptive;

    override val text: String
        @Composable
        get() = when (this) {
            Adaptive -> stringResource(R.string.recommendations_adaptive)
            else -> this.name
        }

    fun toInt(): Int = when (this) {
        Adaptive -> 20 // Default fallback for adaptive
        else -> this.name.toInt()
    }
    
    /**
     * Calculate adaptive recommendations based on playlist size
     * @param playlistSize The number of songs in the playlist
     * @return Number of recommendations to show
     * 
     * Adaptive scaling logic:
     * - â‰¤50 songs: 10 recommendations (20% of playlist)
     * - â‰¤100 songs: 20 recommendations (20% of playlist)
     * - â‰¤250 songs: 30 recommendations (12% of playlist)
     * - â‰¤500 songs: 50 recommendations (10% of playlist)
     * - â‰¤1000 songs: 75 recommendations (7.5% of playlist)
     * - â‰¤2000 songs: 100 recommendations (5% of playlist)
     * - >2000 songs: 150 recommendations (capped for performance)
     */
    fun calculateAdaptiveRecommendations(playlistSize: Int): Int {
        return when (this) {
            Adaptive -> {
                when {
                    playlistSize <= 50 -> 10
                    playlistSize <= 100 -> 20
                    playlistSize <= 250 -> 30
                    playlistSize <= 500 -> 50
                    playlistSize <= 1000 -> 75
                    playlistSize <= 2000 -> 100
                    else -> 120
                }
            }
            else -> this.toInt()
        }
    }
}



