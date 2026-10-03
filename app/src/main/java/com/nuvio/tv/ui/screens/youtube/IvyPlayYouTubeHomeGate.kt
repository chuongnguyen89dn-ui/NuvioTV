package com.nuvio.tv.ui.screens.youtube

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nuvio.tv.domain.model.IvyPlayContentMode
import com.nuvio.tv.ui.navigation.Screen

/**
 * Shared Home gate for IvyPlayTV. STANDARD profiles keep the original NuvioTV Home.
 * YOUTUBE profiles render the IvyPlay YouTube TV surface and keep playback routed
 * through the existing NuvioTV player screen.
 */
@Composable
fun IvyPlayYouTubeHomeGate(
    navController: NavHostController,
    standardContent: @Composable () -> Unit,
    viewModel: IvyPlayProfileModeViewModel = hiltViewModel(),
    playbackViewModel: IvyPlayYouTubePlaybackViewModel = hiltViewModel(),
) {
    val contentMode by viewModel.activeContentMode.collectAsStateWithLifecycle()
    val profileName by viewModel.activeProfileName.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (contentMode) {
        IvyPlayContentMode.STANDARD -> standardContent()
        IvyPlayContentMode.YOUTUBE -> IvyPlayYouTubeTvScreen(
            profileName = profileName,
            onVideoClick = { video ->
                playbackViewModel.resolve(video.url) { resolvedUrl ->
                    if (resolvedUrl.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            "Không thể lấy luồng YouTube cho video này",
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@resolve
                    }
                    navController.navigate(
                        Screen.Player.createRoute(
                            streamUrl = resolvedUrl,
                            title = video.title,
                            streamName = video.channelName,
                            contentId = video.videoId,
                            contentType = "youtube",
                            contentName = video.title,
                            poster = video.thumbnail,
                            videoId = video.videoId,
                            returnToHomeOnBack = true,
                            addonName = "IvyPlay YouTube",
                        )
                    )
                }
            },
        )
    }
}
