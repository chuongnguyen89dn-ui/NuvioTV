package com.nuvio.tv.ui.screens.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object IvyPlayYouTubeFeedRepository {
    private val client = OkHttpClient()

    private val defaultChannels = listOf(
        DefaultChannel(
            channelId = "UCZE88kYvCKUKjM-G0uc8Duw",
            handle = "@khoailangthang",
            name = "Khoai Lang Thang",
        ),
        DefaultChannel(
            channelId = "UCBhgBmuPFbLLxnejr09lnAQ",
            handle = "@hoabanfood",
            name = "HOA BAN FOOD",
        ),
    )

    suspend fun loadDefaultChannels(): List<IvyPlayYouTubeChannelSnapshot> = coroutineScope {
        defaultChannels.map { channel ->
            async { runCatching { loadChannel(channel) }.getOrNull() }
        }.mapNotNull { it.await() }
    }

    private suspend fun loadChannel(source: DefaultChannel): IvyPlayYouTubeChannelSnapshot = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.youtube.com/feeds/videos.xml?channel_id=${source.channelId}")
            .build()
        val xml = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "YouTube feed HTTP ${response.code}" }
            response.body.string()
        }
        val videos = parseEntries(xml, source)
        IvyPlayYouTubeChannelSnapshot(
            channel = IvyPlayYouTubeChannel(
                channelId = source.channelId,
                handle = source.handle,
                name = source.name,
                displayName = source.name,
            ),
            videos = videos,
        )
    }

    private fun parseEntries(xml: String, source: DefaultChannel): List<IvyPlayYouTubeVideo> =
        ENTRY_REGEX.findAll(xml).mapNotNull { match ->
            val entry = match.groupValues[1]
            val videoId = entry.tagValue("yt:videoId") ?: return@mapNotNull null
            val title = entry.tagValue("media:title") ?: entry.tagValue("title") ?: "YouTube video"
            val thumbnail = THUMBNAIL_REGEX.find(entry)?.groupValues?.getOrNull(1)
                ?.decodeXmlEntities()
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val published = entry.tagValue("published")?.substringBefore('T')
            val views = VIEWS_REGEX.find(entry)?.groupValues?.getOrNull(1)?.toLongOrNull()
            IvyPlayYouTubeVideo(
                videoId = videoId,
                title = title.decodeXmlEntities(),
                url = "https://www.youtube.com/watch?v=$videoId",
                thumbnail = thumbnail,
                uploadDate = published,
                viewCount = views,
                channelId = source.channelId,
                channelName = source.name,
            )
        }.toList()

    private fun String.tagValue(tag: String): String? {
        val escapedTag = Regex.escape(tag)
        return Regex("<$escapedTag>(.*?)</$escapedTag>", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun String.decodeXmlEntities(): String = this
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private data class DefaultChannel(
        val channelId: String,
        val handle: String,
        val name: String,
    )

    private val ENTRY_REGEX = Regex("<entry>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val THUMBNAIL_REGEX = Regex("<media:thumbnail[^>]*url=\"([^\"]+)\"")
    private val VIEWS_REGEX = Regex("<media:statistics[^>]*views=\"(\\d+)\"")
}
