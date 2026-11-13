package app.n_zik.android.utils

object ExternalUris {
    fun youtube(videoId: String) = "https://youtube.com/watch?v=$videoId"
    fun youtubeMusic(videoId: String) = "https://music.youtube.com/watch?v=$videoId"

    fun invidious(videoId: String) = "https://yewtu.be/watch?v=$videoId&autoplay=1"
    
    fun youtubeMusicPlaylist(listId: String) = "https://music.youtube.com/playlist?list=$listId"
    fun youtubePlaylist(listId: String) = "https://youtube.com/playlist?list=$listId"
    fun youtubeMusicChannel(channelId: String) = "https://music.youtube.com/channel/$channelId"
}



