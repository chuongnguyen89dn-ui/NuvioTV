package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.theme.NuvioTheme

private enum class YouTubeItemKind { VIDEO, SHORT, PLAYLIST, CHANNEL }

fun CatalogRow.isYouTubeSource(): Boolean {
    val source = listOf(addonId, addonName, addonBaseUrl, catalogId, catalogName, apiType)
        .joinToString(" ").lowercase()
    return addonId.equals("vn.ivyplay.youtube.khoailangthang", true) ||
        source.contains("youtube") ||
        items.any { it.id.startsWith("khoai_") }
}

private fun MetaPreview.youtubeKind(row: CatalogRow): YouTubeItemKind {
    val key = (rawType + " " + id + " " + row.catalogId + " " + row.catalogName).lowercase()
    return when {
        "short" in key -> YouTubeItemKind.SHORT
        "playlist" in key || id.startsWith("PL") -> YouTubeItemKind.PLAYLIST
        "channel" in key || id.startsWith("UC") -> YouTubeItemKind.CHANNEL
        else -> YouTubeItemKind.VIDEO
    }
}

private fun MetaPreview.youtubeSubtitle(kind: YouTubeItemKind): String {
    val channel = description?.trim().orEmpty()
    val duration = runtime?.trim().orEmpty()
    return when (kind) {
        YouTubeItemKind.VIDEO, YouTubeItemKind.SHORT ->
            listOf(channel, duration).filter { it.isNotBlank() }.joinToString(" • ")
        YouTubeItemKind.PLAYLIST -> listOf(channel, "Playlist").filter { it.isNotBlank() }.joinToString(" • ")
        YouTubeItemKind.CHANNEL -> if (channel.isNotBlank()) channel else "Channel"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun YouTubeCatalogRowSection(
    catalogRow: CatalogRow,
    listState: LazyListState,
    rowFocusRequester: FocusRequester,
    focusedItemIndex: Int = -1,
    onItemFocused: (Int) -> Unit = {},
    onItemFocus: (MetaPreview) -> Unit = {},
    onItemClick: (String, String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = catalogRow.catalogName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(
                items = catalogRow.items,
                key = { _, item -> item.id }
            ) { index, item ->
                val kind = remember(item.id, item.rawType, catalogRow.catalogId) { item.youtubeKind(catalogRow) }
                val width = when (kind) {
                    YouTubeItemKind.SHORT -> 150.dp
                    YouTubeItemKind.CHANNEL -> 190.dp
                    else -> 286.dp
                }
                val imageHeight = when (kind) {
                    YouTubeItemKind.SHORT -> 266.dp
                    YouTubeItemKind.CHANNEL -> 190.dp
                    else -> 161.dp
                }
                val modifier = if (index == 0) Modifier.focusRequester(rowFocusRequester) else Modifier
                Card(
                    onClick = { onItemClick(item.id, item.apiType, catalogRow.addonBaseUrl) },
                    modifier = modifier.width(width),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    onFocus = {
                        onItemFocused(index)
                        onItemFocus(item)
                    }
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(imageHeight)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = item.poster ?: item.landscapePoster ?: item.background,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                            if (kind == YouTubeItemKind.PLAYLIST) {
                                Text(
                                    text = "PLAYLIST",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                )
                            }
                            if (kind == YouTubeItemKind.SHORT) {
                                Text(
                                    text = "SHORTS",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                )
                            }
                        }
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = NuvioTheme.colors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        val subtitle = item.youtubeSubtitle(kind)
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = NuvioTheme.colors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
