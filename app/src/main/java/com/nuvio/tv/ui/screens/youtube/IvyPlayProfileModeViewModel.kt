package com.nuvio.tv.ui.screens.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.IvyPlayContentMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class IvyPlayProfileModeViewModel @Inject constructor(
    profileManager: ProfileManager,
) : ViewModel() {
    val activeContentMode: StateFlow<IvyPlayContentMode> = combine(
        profileManager.activeProfileId,
        profileManager.profiles,
    ) { activeProfileId, profiles ->
        profiles.firstOrNull { it.id == activeProfileId }?.contentMode ?: IvyPlayContentMode.STANDARD
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = profileManager.activeProfile?.contentMode ?: IvyPlayContentMode.STANDARD,
    )

    val activeProfileName: StateFlow<String> = combine(
        profileManager.activeProfileId,
        profileManager.profiles,
    ) { activeProfileId, profiles ->
        profiles.firstOrNull { it.id == activeProfileId }?.name ?: "You"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = profileManager.activeProfile?.name ?: "You",
    )
}
