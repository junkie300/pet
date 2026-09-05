package io.github.junkie300.petapp.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.favorite.FavoriteDao
import io.github.junkie300.petapp.data.favorite.FavoritePlace
import io.github.junkie300.petapp.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val favorites: FavoriteDao,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Place>>(UiState.Loading)
    val state: StateFlow<UiState<Place>> = _state.asStateFlow()

    /** DB 를 그대로 흘려 본다. 다른 화면에서 지워도 여기 별이 따라 꺼진다. */
    val isFavorite: StateFlow<Boolean> = favorites.observeIsFavorite(placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), false)

    init {
        load()
    }

    /**
     * 즐겨찾기 담기/빼기.
     *
     * 장소를 아직 못 불러왔으면 아무 것도 하지 않는다 — 이름 없는 즐겨찾기를 만들면
     * 오프라인에서 빈 줄이 뜬다. 버튼도 그때는 보이지 않는다.
     */
    fun toggleFavorite() {
        val place = (_state.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            if (isFavorite.value) favorites.remove(place.id) else favorites.add(FavoritePlace.from(place))
        }
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
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

        fun factory(placeId: Long, places: PlaceRepository, favorites: FavoriteDao) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaceDetailViewModel(placeId, places, favorites) as T
            }
    }
}
