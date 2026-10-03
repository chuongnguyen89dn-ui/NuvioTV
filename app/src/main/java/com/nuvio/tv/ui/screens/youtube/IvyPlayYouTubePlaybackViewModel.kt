package com.nuvio.tv.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.trailer.InAppYouTubeExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class IvyPlayYouTubePlaybackViewModel @Inject constructor(
    private val extractor: InAppYouTubeExtractor,
) : ViewModel() {
    fun resolve(
        youtubeUrl: String,
        onResolved: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val resolved = runCatching {
                extractor.extractSingleUrl(youtubeUrl)
            }.getOrNull()?.takeIf { it.isNotBlank() }
            onResolved(resolved)
        }
    }
}
