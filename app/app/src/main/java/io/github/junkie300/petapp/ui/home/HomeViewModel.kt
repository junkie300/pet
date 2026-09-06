package io.github.junkie300.petapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.data.oldestCachedAt
import io.github.junkie300.petapp.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * S-00 홈이 그리는 데 필요한 전부.
 *
 * 네 상태를 **타입으로** 갈라 둔다 (D-38 과 같은 이유). 특히 [NoRegion] 과 [Failed] 는
 * 절대 합치지 않는다 — "아직 안 골랐다"와 "못 불러왔다"는 사용자가 할 일이 정반대다.
 */
sealed interface HomeUiState {

    /** 저장소에서 지역을 되살리는 중. */
    data object Loading : HomeUiState

    /** 저장된 지역이 없다. 첫 실행이며, 실패가 아니다. */
    data object NoRegion : HomeUiState

    /** 저장된 지역 코드는 있는데 이름을 못 불러왔다 (네트워크 실패거나 사라진 코드). */
    data class Failed(val code: String) : HomeUiState

    data class Ready(
        val region: Region,
        /**
         * 건수는 지역이 정해진 다음에 따로 온다. 전부 0 이어도 **Success** 다 —
         * 적재된 카테고리의 0 은 "이 동네엔 없다"는 유효한 답이기 때문이다.
         * 적재 안 된 카테고리는 애초에 이 맵에 없다 ([PlaceCategory.loaded]).
         */
        val counts: UiState<Map<PlaceCategory, Int>>,
        /**
         * null 이 아니면 이 화면은 **받아 둔 사본**으로 그린 것이고, 그 시각이 이 값이다.
         * 화면 위에 오프라인 배너가 뜬다 (spec.md §5.3).
         */
        val cachedAt: Long? = null,
    ) : HomeUiState
}

class HomeViewModel(
    private val regions: RegionRepository,
    private val places: PlaceRepository,
    recentStore: RecentRegionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var currentCode: String? = null

    init {
        viewModelScope.launch {
            // 현재 지역 = 최근 목록의 맨 앞. 현재 지역용 키를 따로 두면 두 값이 어긋날 수 있고,
            // S-01 이 이미 확정할 때마다 remember() 를 부르므로 여기만 보면 항상 최신이다.
            recentStore.codes
                .map { it.firstOrNull() }
                .distinctUntilChanged()
                .collect { code ->
                    currentCode = code
                    load(code)
                }
        }
    }

    fun retry() {
        viewModelScope.launch { load(currentCode) }
    }

    private suspend fun load(code: String?) {
        if (code == null) {
            _state.value = HomeUiState.NoRegion
            return
        }
        _state.value = HomeUiState.Loading
        // 網이 끊겨도 한 번 본 지역이면 사본으로 이름이 나온다 (RegionRepository).
        val fetchedRegion = runCatching { regions.byCodes(listOf(code)) }.getOrNull()
        val region = fetchedRegion?.data?.firstOrNull()
        if (region == null) {
            _state.value = HomeUiState.Failed(code)
            return
        }
        // 지역 이름을 먼저 띄운다. 건수를 기다리느라 지역 칩까지 비워 두지 않는다.
        _state.value = HomeUiState.Ready(region, UiState.Loading, fetchedRegion.cachedAt)
        _state.value = runCatching { places.countsByCategory(region.code) }.fold(
            onSuccess = { counts ->
                // 지역과 건수 중 **더 오래된 쪽**이 이 화면의 기준이다. 둘 다 지금 것이면 null 이다.
                HomeUiState.Ready(
                    region = region,
                    counts = UiState.Success(counts.data),
                    cachedAt = oldestCachedAt(fetchedRegion.cachedAt, counts.cachedAt),
                )
            },
            onFailure = { HomeUiState.Ready(region, UiState.Failed(it), fetchedRegion.cachedAt) },
        )
    }

    companion object {
        fun factory(
            regions: RegionRepository,
            places: PlaceRepository,
            recentStore: RecentRegionStore,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(regions, places, recentStore) as T
        }
    }
}
