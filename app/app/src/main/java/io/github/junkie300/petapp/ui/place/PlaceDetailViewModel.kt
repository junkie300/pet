package io.github.junkie300.petapp.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.favorite.FavoriteDao
import io.github.junkie300.petapp.data.favorite.FavoritePlace
import io.github.junkie300.petapp.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 상세 화면이 그리는 장소 한 건과 **그 값이 언제 것인지**.
 *
 * [cachedAt] 이 null 이 아니면 網이 아니라 사본에서 나온 값이며, 화면 위에 오프라인 배너가 뜬다.
 * 출처·기준일은 어느 쪽에서 왔든 그대로 붙는다 — 그게 없으면 애초에 그리지 않는다 (D-61).
 */
data class PlaceDetail(val place: Place, val cachedAt: Long? = null)

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

    private val _state = MutableStateFlow<UiState<PlaceDetail>>(UiState.Loading)
    val state: StateFlow<UiState<PlaceDetail>> = _state.asStateFlow()

    /** 지금 흐르고 있는 조회. 다시 시도하면 **먼저 끊는다.** */
    private var loadJob: Job? = null

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
        val place = (_state.value as? UiState.Success)?.data?.place ?: return
        viewModelScope.launch {
            if (isFavorite.value) favorites.remove(place.id) else favorites.add(FavoritePlace.from(place))
        }
    }

    /**
     * 사본이 있으면 **배너 없이 먼저 그리고** 서버 답으로 갈아 끼운다 (D-79).
     * 서버가 못 주면 그때 사본에 배너가 붙는다.
     */
    fun load() {
        loadJob?.cancel()
        _state.value = UiState.Loading
        loadJob = viewModelScope.launch {
            places.byId(placeId)
                .map { fetched ->
                    val place = fetched.data
                    // 서버가 "그런 장소 없다"고 답한 것이다. 실패와 같은 화면으로 만들지 않는다.
                    if (place == null) UiState.Empty else UiState.Success(PlaceDetail(place, fetched.offlineSince))
                }
                // 서버도 캐시도 못 줬다. 담아 둔 곳이면 그 스냅샷이 마지막 수단이다.
                .catch { failure -> emit(fromFavorite() ?: UiState.Failed(failure)) }
                .collect { _state.value = it }
        }
    }

    /**
     * 즐겨찾기 스냅샷으로 상세를 그린다 (D-60·D-61).
     *
     * 오프라인 캐시는 상한을 넘으면 밀려 나가지만 즐겨찾기는 지우지 않는다. 그래서
     * **담아 둔 곳은 몇 달 뒤 지하철에서도 열린다.** 다만 DB 버전 1 때 담은 것은
     * 출처가 없어 null 이 되고, 그때는 종전대로 실패 화면이다 — 출처를 지어내지 않는다.
     */
    private suspend fun fromFavorite(): UiState<PlaceDetail>? {
        val favorite = runCatching { favorites.byId(placeId) }.getOrNull() ?: return null
        val place = favorite.toPlace() ?: return null
        return UiState.Success(PlaceDetail(place, favorite.savedAt))
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
