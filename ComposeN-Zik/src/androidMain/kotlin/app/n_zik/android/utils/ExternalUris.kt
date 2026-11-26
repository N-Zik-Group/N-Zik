package app.n_zik.android.utils

object ExternalUris {
    private val podcastPlaylistId = Regex("MPSP(PL[A-Za-z0-9_-]+)")

    fun youtube(videoId: String) = "https://youtube.com/watch?v=$videoId"
    fun youtubeMusic(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    
    fun youtubeMusicPlaylist(listId: String) = "https://music.youtube.com/playlist?list=$listId"
    fun youtubePlaylist(listId: String) = "https://youtube.com/playlist?list=$listId"
    fun youtubeMusicPodcast(browseId: String): String? =
        podcastPlaylistId.matchEntire(browseId)?.groupValues?.get(1)?.let(::youtubeMusicPlaylist)
    fun youtubeMusicChannel(channelId: String) = "https://music.youtube.com/channel/$channelId"
}



