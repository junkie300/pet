package io.github.junkie300.petapp.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * S-03 장소 상세.
 *
 * [UiState.Empty] 는 **그런 장소가 없다**는 뜻이다 (폐업으로 빠졌거나 id 가 옛것이다).
 * 불러오기 실패와 같은 화면으로 처리하지 않는다 (spec.md §5.3).
 */
class PlaceDetailViewModel(
    private val placeId: Long,
    private val places: PlaceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Place>>(UiState.Loading)
    val state: StateFlow<UiState<Place>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { places.byId(placeId) }.fold(
                onSuccess = { place -> if (place == null) UiState.Empty else UiState.Success(place) },
                onFailure = { UiState.Failed(it) },
            )
        }
    }

    companion object {
        fun factory(placeId: Long, places: PlaceRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaceDetailViewModel(placeId, places) as T
        }
    }
}
