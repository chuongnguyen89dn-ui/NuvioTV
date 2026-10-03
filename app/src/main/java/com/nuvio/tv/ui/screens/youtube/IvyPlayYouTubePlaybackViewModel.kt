package com.nuvio.tv.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.trailer.InAppYouTubeExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class IvyPlayYouTubePlaybackViewModel @Inject constructor(
    private val extractor: InAppYouTubeExtractor,
) : ViewModel() {
    private var resolveJob: Job? = null
    private var resolveGeneration: Long = 0L

    fun resolve(
        youtubeUrl: String,
        onResolved: (String?) -> Unit,
    ) {
        resolveGeneration += 1L
        val generation = resolveGeneration
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val resolved = try {
                extractor.extractSingleUrl(youtubeUrl).takeIf { it.isNotBlank() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }

            if (generation == resolveGeneration) {
                onResolved(resolved)
            }
        }
    }

    override fun onCleared() {
        resolveJob?.cancel()
        super.onCleared()
    }
}
