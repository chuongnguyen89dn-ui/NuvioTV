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

    suspend fun searchYouTube(query: String, limit: Int = 30): List<IvyPlayYouTubeVideo> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isBlank()) return@withContext emptyList()
        val html = fetchHtml("https://www.youtube.com/results?search_query=${urlEncode(normalized)}")
        parseVideoRenderers(html, fallbackChannel = null)
            .distinctBy { it.videoId }
            .take(limit)
    }

    private suspend fun loadChannel(source: DefaultChannel): IvyPlayYouTubeChannelSnapshot = coroutineScope {
        val uploadsDeferred = async(Dispatchers.IO) {
            runCatching { loadUploads(source) }.getOrDefault(emptyList())
        }
        val shortsDeferred = async(Dispatchers.IO) {
            runCatching {
                parseShortRenderers(fetchHtml("https://www.youtube.com/${source.handle}/shorts"), source)
            }.getOrDefault(emptyList())
        }
        val liveDeferred = async(Dispatchers.IO) {
            runCatching {
                parseVideoRenderers(fetchHtml("https://www.youtube.com/${source.handle}/streams"), source)
            }.getOrDefault(emptyList())
        }

        IvyPlayYouTubeChannelSnapshot(
            channel = IvyPlayYouTubeChannel(
                channelId = source.channelId,
                handle = source.handle,
                name = source.name,
                displayName = source.name,
            ),
            videos = uploadsDeferred.await(),
            shorts = shortsDeferred.await(),
            live = liveDeferred.await(),
        )
    }

    private fun loadUploads(source: DefaultChannel): List<IvyPlayYouTubeVideo> {
        val request = Request.Builder()
            .url("https://www.youtube.com/feeds/videos.xml?channel_id=${source.channelId}")
            .header("User-Agent", DESKTOP_USER_AGENT)
            .build()
        val xml = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "YouTube feed HTTP ${response.code}" }
            response.body.string()
        }
        return parseEntries(xml, source)
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "YouTube page HTTP ${response.code}" }
            response.body.string()
        }
    }

    private fun parseEntries(xml: String, source: DefaultChannel): List<IvyPlayYouTubeVideo> =
        ENTRY_REGEX.findAll(xml).mapNotNull { match ->
            val entry = match.groupValues[1]
            val videoId = entry.tagValue("yt:videoId") ?: return@mapNotNull null
            val title = entry.tagValue("media:title") ?: entry.tagValue("title") ?: "YouTube video"
            val thumbnail = THUMBNAIL_REGEX.find(entry)?.groupValues?.getOrNull(1)
                ?.decodeXmlEntities()
                ?: thumbnailFor(videoId)
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

    private fun parseShortRenderers(html: String, source: DefaultChannel): List<IvyPlayYouTubeVideo> {
        val results = mutableListOf<IvyPlayYouTubeVideo>()
        SHORT_RENDERER_REGEX.findAll(html).forEach { match ->
            val block = match.groupValues[1]
            val videoId = VIDEO_ID_REGEX.find(block)?.groupValues?.getOrNull(1) ?: return@forEach
            val title = firstText(block) ?: "YouTube Short"
            results += IvyPlayYouTubeVideo(
                videoId = videoId,
                title = title,
                url = "https://www.youtube.com/shorts/$videoId",
                thumbnail = thumbnailFor(videoId),
                channelId = source.channelId,
                channelName = source.name,
            )
        }
        return results.distinctBy { it.videoId }
    }

    private fun parseVideoRenderers(html: String, fallbackChannel: DefaultChannel?): List<IvyPlayYouTubeVideo> {
        val results = mutableListOf<IvyPlayYouTubeVideo>()
        VIDEO_RENDERER_REGEX.findAll(html).forEach { match ->
            val block = match.groupValues[1]
            val videoId = VIDEO_ID_REGEX.find(block)?.groupValues?.getOrNull(1) ?: return@forEach
            val title = firstText(block) ?: return@forEach
            val channelName = CHANNEL_TEXT_REGEX.find(block)?.groupValues?.getOrNull(1)?.decodeJsonString()
                ?: fallbackChannel?.name
            results += IvyPlayYouTubeVideo(
                videoId = videoId,
                title = title,
                url = "https://www.youtube.com/watch?v=$videoId",
                thumbnail = thumbnailFor(videoId),
                channelId = fallbackChannel?.channelId,
                channelName = channelName,
            )
        }
        return results.distinctBy { it.videoId }
    }

    private fun firstText(block: String): String? {
        return SIMPLE_TEXT_REGEX.find(block)?.groupValues?.getOrNull(1)?.decodeJsonString()
            ?: RUN_TEXT_REGEX.find(block)?.groupValues?.getOrNull(1)?.decodeJsonString()
    }

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

    private fun String.decodeJsonString(): String = this
        .replace("\\u0026", "&")
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\\"", "\"")
        .replace("\\n", " ")
        .replace("\\/", "/")
        .trim()

    private fun thumbnailFor(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class DefaultChannel(
        val channelId: String,
        val handle: String,
        val name: String,
    )

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0 Safari/537.36"

    private val ENTRY_REGEX = Regex("<entry>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val THUMBNAIL_REGEX = Regex("<media:thumbnail[^>]*url=\"([^\"]+)\"")
    private val VIEWS_REGEX = Regex("<media:statistics[^>]*views=\"(\\d+)\"")

    // Keep renderer matches bounded so one malformed page cannot swallow the full ytInitialData payload.
    private val SHORT_RENDERER_REGEX = Regex(
        "\\\"(?:reelItemRenderer|shortsLockupViewModel)\\\"\\s*:\\s*\\{(.{0,5000}?)\\}\s*(?:,|\\})",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val VIDEO_RENDERER_REGEX = Regex(
        "\\\"videoRenderer\\\"\\s*:\\s*\\{(.{0,9000}?)\\}\s*(?:,|\\})",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val VIDEO_ID_REGEX = Regex("\\\"videoId\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]{6,20})\\\"")
    private val SIMPLE_TEXT_REGEX = Regex("\\\"simpleText\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
    private val RUN_TEXT_REGEX = Regex("\\\"text\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
    private val CHANNEL_TEXT_REGEX = Regex(
        "\\\"ownerText\\\".{0,1200}?\\\"text\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
}
