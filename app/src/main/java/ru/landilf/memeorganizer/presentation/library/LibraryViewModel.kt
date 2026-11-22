package ru.landilf.memeorganizer.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import ru.landilf.memeorganizer.domain.usecase.meme.ObserveMemesUseCase
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(observeMemes: ObserveMemesUseCase) : ViewModel() {
    val memes = observeMemes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
