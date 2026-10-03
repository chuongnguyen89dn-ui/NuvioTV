package com.nuvio.tv.ui.screens.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private enum class IvyPlayYouTubeTvTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SHORTS("Shorts", Icons.Default.SmartDisplay),
    SUBSCRIPTIONS("Subscriptions", Icons.Default.Subscriptions),
    YOU("You", Icons.Default.AccountCircle),
}

@Composable
fun IvyPlayYouTubeTvScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (IvyPlayYouTubeVideo) -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(IvyPlayYouTubeTvTab.HOME) }
    var channels by remember { mutableStateOf<List<IvyPlayYouTubeChannelSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        channels = IvyPlayYouTubeFeedRepository.loadDefaultChannels()
    }

    Row(modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.width(132.dp).fillMaxHeight().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            IvyPlayYouTubeTvTab.entries.forEach { tab ->
                IvyPlayYouTubeTvNavItem(
                    tab = tab,
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                )
                if (tab == IvyPlayYouTubeTvTab.SUBSCRIPTIONS) Spacer(Modifier.weight(1f))
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("YouTube", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF272727)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("Search", color = Color.White)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1565C0)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("I", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
                when (selectedTab) {
                    IvyPlayYouTubeTvTab.HOME -> IvyPlayYouTubeHomeContent(channels, onVideoClick)
                    IvyPlayYouTubeTvTab.SHORTS -> IvyPlayYouTubeTvPlaceholder("Shorts", "Shorts đang được đồng bộ cùng nguồn dữ liệu mobile")
                    IvyPlayYouTubeTvTab.SUBSCRIPTIONS -> IvyPlayYouTubeSubscriptionsContent(channels, onVideoClick)
                    IvyPlayYouTubeTvTab.YOU -> IvyPlayYouTubeTvPlaceholder("You", "Profile YouTube dùng chung hệ thống profile IvyPlay")
                }
            }
        }
    }
}

@Composable
private fun IvyPlayYouTubeHomeContent(
    channels: List<IvyPlayYouTubeChannelSnapshot>,
    onVideoClick: (IvyPlayYouTubeVideo) -> Unit,
) {
    if (channels.isEmpty()) {
        IvyPlayYouTubeTvPlaceholder("Home", "Đang tải Khoai Lang Thang và HOA BAN FOOD…")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        val recommended = channels.flatMap { it.videos }.distinctBy { it.videoId }.take(12)
        if (recommended.isNotEmpty()) {
            item { IvyPlayYouTubeShelf("Recommended", recommended, onVideoClick) }
        }
        channels.forEach { snapshot ->
            item(key = snapshot.channel.channelId) {
                IvyPlayYouTubeShelf(
                    snapshot.channel.displayName ?: snapshot.channel.name,
                    snapshot.videos.take(10),
                    onVideoClick,
                )
            }
        }
    }
}

@Composable
private fun IvyPlayYouTubeSubscriptionsContent(
    channels: List<IvyPlayYouTubeChannelSnapshot>,
    onVideoClick: (IvyPlayYouTubeVideo) -> Unit,
) {
    if (channels.isEmpty()) {
        IvyPlayYouTubeTvPlaceholder("Subscriptions", "Đang tải kênh đã theo dõi…")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        channels.forEach { snapshot ->
            item(key = "sub-${snapshot.channel.channelId}") {
                IvyPlayYouTubeShelf(
                    snapshot.channel.displayName ?: snapshot.channel.name,
                    snapshot.videos.take(10),
                    onVideoClick,
                )
            }
        }
    }
}

@Composable
private fun IvyPlayYouTubeShelf(
    title: String,
    videos: List<IvyPlayYouTubeVideo>,
    onVideoClick: (IvyPlayYouTubeVideo) -> Unit,
) {
    Column {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(videos, key = { it.videoId }) { video ->
                IvyPlayYouTubeVideoCard(video, onVideoClick)
            }
        }
    }
}

@Composable
private fun IvyPlayYouTubeVideoCard(
    video: IvyPlayYouTubeVideo,
    onVideoClick: (IvyPlayYouTubeVideo) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(300.dp)
            .scale(if (focused) 1.06f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onVideoClick(video) },
    ) {
        AsyncImage(
            model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
            contentDescription = video.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF202020)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = video.channelName.orEmpty(),
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IvyPlayYouTubeTvNavItem(
    tab: IvyPlayYouTubeTvTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Color(0xFF272727)
        else -> Color.Transparent
    }
    val foreground = if (focused) Color.Black else Color.White

    Surface(
        onClick = onClick,
        modifier = Modifier.width(104.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = background,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = foreground, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(4.dp))
            Text(tab.label, color = foreground, fontSize = 11.sp)
        }
    }
}

@Composable
private fun IvyPlayYouTubeTvPlaceholder(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFFAAAAAA), fontSize = 18.sp)
    }
}
