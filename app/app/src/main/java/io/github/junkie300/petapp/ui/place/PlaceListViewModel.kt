package io.github.junkie300.petapp.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.data.oldestCachedAt
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 한 읍면동에서 **고른 카테고리들**의 목록.
 *
 * 홈과 같은 네 상태를 쓴다 — "아직 안 골랐다"(NoRegion)와 "못 불러왔다"(Failed)를
 * 절대 합치지 않는다 (spec.md §5.3).
 */
sealed interface PlaceListUiState {
    data object Loading : PlaceListUiState
    data object NoRegion : PlaceListUiState

    /** 저장된 지역 코드는 있는데 이름을 못 불러왔다. */
    data class Failed(val code: String) : PlaceListUiState

    data class Ready(
        val region: Region,
        /** 빈 목록은 성공이 아니라 [UiState.Empty] 다 — "이 동네엔 없습니다"로 적기 위해서다. */
        val places: UiState<List<Place>>,
        /** null 이 아니면 받아 둔 사본으로 그린 화면이다. 그 시각이 오프라인 배너에 뜬다. */
        val cachedAt: Long? = null,
    ) : PlaceListUiState
}

/**
 * 목록 화면과 지도가 **같이 쓰는** ViewModel (D-26·D-76).
 *
 * 다른 것은 고를 수 있는 카테고리 수뿐이다 — 목록 화면은 하나로 고정이고, 지도는 필터 칩으로
 * 여럿을 켠다. 조회를 두 벌로 나누면 같은 동네를 두고 지도와 목록이 다른 답을 하게 된다.
 */
class PlaceListViewModel(
    initialSelection: Set<PlaceCategory>,
    private val regions: RegionRepository,
    private val places: PlaceRepository,
    recentStore: RecentRegionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaceListUiState>(PlaceListUiState.Loading)
    val state: StateFlow<PlaceListUiState> = _state.asStateFlow()

    /** 지금 켜져 있는 카테고리. 필터 칩이 이걸 그린다. **절대 비지 않는다.** */
    private val _selected = MutableStateFlow(initialSelection)
    val selected: StateFlow<Set<PlaceCategory>> = _selected.asStateFlow()

    private var currentCode: String? = null

    init {
        require(initialSelection.isNotEmpty()) { "카테고리를 최소 하나는 골라야 한다." }
        viewModelScope.launch {
            // 현재 지역은 최근 목록의 맨 앞이다 (D-54). 홈과 같은 값을 본다.
            recentStore.codes
                .map { it.firstOrNull() }
                .distinctUntilChanged()
                .collect { code ->
                    currentCode = code
                    load(code)
                }
        }
    }

    /**
     * 칩 하나를 켜고 끈다.
     *
     * ⚠️ **마지막 하나는 끌 수 없다.** 다 꺼진 지도는 "이 동네엔 없다"와 구분되지 않는데,
     * 사실은 아무것도 묻지 않은 상태다 (spec.md §5.3 이 가르라고 한 그 구분이다).
     */
    fun toggle(category: PlaceCategory) {
        val current = _selected.value
        val next = if (category in current) current - category else current + category
        if (next.isEmpty()) return
        select(next)
    }

    /** 홈 타일처럼 **바깥에서** 카테고리를 정해 들어올 때. */
    fun select(categories: Set<PlaceCategory>) {
        if (categories.isEmpty() || categories == _selected.value) return
        _selected.value = categories
        viewModelScope.launch { load(currentCode) }
    }

    fun retry() {
        viewModelScope.launch { load(currentCode) }
    }

    private suspend fun load(code: String?) {
        if (code == null) {
            _state.value = PlaceListUiState.NoRegion
            return
        }
        _state.value = PlaceListUiState.Loading
        val fetchedRegion = runCatching { regions.byCodes(listOf(code)) }.getOrNull()
        val region = fetchedRegion?.data?.firstOrNull()
        if (region == null) {
            _state.value = PlaceListUiState.Failed(code)
            return
        }
        // 지역 이름을 먼저 띄운다. 목록을 기다리느라 머리말까지 비워 두지 않는다.
        _state.value = PlaceListUiState.Ready(region, UiState.Loading, fetchedRegion.cachedAt)
        val categories = _selected.value
        _state.value = runCatching { places.listByRegion(region.code, categories) }.fold(
            onSuccess = { fetched ->
                PlaceListUiState.Ready(
                    region = region,
                    places = fetched.data.toUiState(),
                    cachedAt = oldestCachedAt(fetchedRegion.cachedAt, fetched.cachedAt),
                )
            },
            onFailure = { PlaceListUiState.Ready(region, UiState.Failed(it), fetchedRegion.cachedAt) },
        )
    }

    companion object {
        fun factory(
            initialSelection: Set<PlaceCategory>,
            regions: RegionRepository,
            places: PlaceRepository,
            recentStore: RecentRegionStore,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaceListViewModel(initialSelection, regions, places, recentStore) as T
        }
    }
}
