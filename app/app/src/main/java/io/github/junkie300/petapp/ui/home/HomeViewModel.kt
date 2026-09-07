package io.github.junkie300.petapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.Fetched
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.data.oldestCachedAt
import io.github.junkie300.petapp.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

    /** 지금 흐르고 있는 조회. 지역이 바뀌거나 다시 시도하면 **먼저 끊는다.** */
    private var loadJob: Job? = null

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

    fun retry() = load(currentCode)

    /**
     * 지역 이름과 건수를 **각각** 흘려 받아 합친다 (D-79).
     *
     * ⚠️ 두 조회를 겹쳐 놓지 않는다(`flatMapLatest`). 지역이 사본→서버로 두 번 오는데 그때마다
     * 건수 조회를 다시 걸면, 圈外에서 8초짜리 타임아웃을 두 번 쓴다. 건수에 필요한 것은
     * 지역 **이름**이 아니라 코드이고, 코드는 처음부터 알고 있다.
     */
    private fun load(code: String?) {
        loadJob?.cancel()
        if (code == null) {
            _state.value = HomeUiState.NoRegion
            return
        }
        _state.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            combine(regionOf(code), countsOf(code)) { region, counts ->
                if (region == null) {
                    HomeUiState.Failed(code)
                } else {
                    HomeUiState.Ready(
                        region = region.data,
                        counts = counts.data,
                        // 지역과 건수 중 **더 오래된 쪽**이 이 화면의 기준이다.
                        cachedAt = oldestCachedAt(region.offlineSince, counts.offlineSince),
                    )
                }
            }.collect { _state.value = it }
        }
    }

    /** 지역 이름. 사본도 서버도 못 주면 null 이며, 그때가 [HomeUiState.Failed] 다. */
    private fun regionOf(code: String): Flow<Fetched<Region>?> =
        regions.byCodes(listOf(code))
            .map { fetched -> fetched.data.firstOrNull()?.let { region -> fetched.map { region } } }
            .catch { emit(null) }

    /**
     * 카테고리별 건수. **[UiState.Loading] 을 먼저 내보낸다** — 그래야 지역 이름이 사본에서
     * 바로 나왔을 때 건수를 기다리느라 지역 칩까지 비워 두지 않는다.
     */
    private fun countsOf(code: String): Flow<Fetched<UiState<Map<PlaceCategory, Int>>>> =
        places.countsByCategory(code)
            .map { fetched -> fetched.map<UiState<Map<PlaceCategory, Int>>> { UiState.Success(it) } }
            .catch { emit(Fetched(UiState.Failed(it))) }
            .onStart { emit(Fetched(UiState.Loading)) }

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
