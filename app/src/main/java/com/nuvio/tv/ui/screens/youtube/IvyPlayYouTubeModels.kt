package com.nuvio.tv.ui.screens.youtube

data class IvyPlayYouTubeChannel(
    val channelId: String,
    val handle: String? = null,
    val name: String,
    val displayName: String? = null,
    val avatar: String? = null,
)

data class IvyPlayYouTubeVideo(
    val videoId: String,
    val title: String,
    val url: String,
    val thumbnail: String? = null,
    val uploadDate: String? = null,
    val viewCount: Long? = null,
    val channelId: String? = null,
    val channelName: String? = null,
)

data class IvyPlayYouTubeChannelSnapshot(
    val channel: IvyPlayYouTubeChannel,
    val videos: List<IvyPlayYouTubeVideo>,
    val shorts: List<IvyPlayYouTubeVideo> = emptyList(),
    val live: List<IvyPlayYouTubeVideo> = emptyList(),
)
