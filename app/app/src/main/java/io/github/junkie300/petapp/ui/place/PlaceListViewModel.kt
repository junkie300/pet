package io.github.junkie300.petapp.ui.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.DeviceLocation
import io.github.junkie300.petapp.data.Fetched
import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.PlaceRepository
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.data.oldestCachedAt
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
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
    private val deviceLocation: DeviceLocation,
    recentStore: RecentRegionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaceListUiState>(PlaceListUiState.Loading)
    val state: StateFlow<PlaceListUiState> = _state.asStateFlow()

    /** 지금 켜져 있는 카테고리. 필터 칩이 이걸 그린다. **절대 비지 않는다.** */
    private val _selected = MutableStateFlow(initialSelection)
    val selected: StateFlow<Set<PlaceCategory>> = _selected.asStateFlow()

    /**
     * 거리를 재는 기준점. null 이면 카드에 거리가 안 붙는다 (D-81).
     *
     * ⚠️ **목록 순서는 이것과 무관하다.** 이 앱의 기준점은 현재 위치가 아니라 사용자가 고른
     * 지역이고(D-24·D-59), 거리는 그 안에서 "어느 쪽이 가까운가"를 돕는 곁가지다.
     */
    private val _origin = MutableStateFlow<GeoPoint?>(null)
    val origin: StateFlow<GeoPoint?> = _origin.asStateFlow()

    private var currentCode: String? = null

    /** 지금 흐르고 있는 조회. 지역·카테고리가 바뀌면 **먼저 끊는다.** */
    private var loadJob: Job? = null

    init {
        require(initialSelection.isNotEmpty()) { "카테고리를 최소 하나는 골라야 한다." }
        // 이미 권한이 있으면 묻지 않고 바로 잡는다. 없으면 화면의 "거리 보기"가 물어본다.
        if (deviceLocation.hasPermission) refreshLocation()
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
        load(currentCode)
    }

    fun retry() = load(currentCode)

    /**
     * 현재 위치를 다시 잡는다. 권한을 방금 받았을 때와 화면에 다시 들어왔을 때 부른다.
     * **못 얻어도 조용하다** — 거리는 없으면 없는 대로 그린다.
     */
    fun refreshLocation() {
        viewModelScope.launch { _origin.value = deviceLocation.current() }
    }

    /**
     * 지역 이름과 목록을 **각각** 흘려 받아 합친다 (D-79). 사본이 있으면 배너 없이 먼저 그리고,
     * 서버 답이 오면 갈아 끼운다. 서버가 못 주면 그때 사본에 배너가 붙는다.
     *
     * ⚠️ 두 조회를 겹쳐 놓지 않는다 — 이유는 [HomeViewModel] 의 같은 자리에 적어 두었다.
     */
    private fun load(code: String?) {
        loadJob?.cancel()
        if (code == null) {
            _state.value = PlaceListUiState.NoRegion
            return
        }
        _state.value = PlaceListUiState.Loading
        val categories = _selected.value
        loadJob = viewModelScope.launch {
            combine(regionOf(code), placesOf(code, categories)) { region, places ->
                if (region == null) {
                    PlaceListUiState.Failed(code)
                } else {
                    PlaceListUiState.Ready(
                        region = region.data,
                        places = places.data,
                        cachedAt = oldestCachedAt(region.offlineSince, places.offlineSince),
                    )
                }
            }.collect { _state.value = it }
        }
    }

    /** 지역 이름. 사본도 서버도 못 주면 null 이며, 그때가 [PlaceListUiState.Failed] 다. */
    private fun regionOf(code: String): Flow<Fetched<Region>?> =
        regions.byCodes(listOf(code))
            .map { fetched -> fetched.data.firstOrNull()?.let { region -> fetched.map { region } } }
            .catch { emit(null) }

    /**
     * 장소 목록. **[UiState.Loading] 을 먼저 내보낸다** — 그래야 지역 이름이 사본에서 바로
     * 나왔을 때 목록을 기다리느라 머리말까지 비워 두지 않는다.
     */
    private fun placesOf(code: String, categories: Set<PlaceCategory>): Flow<Fetched<UiState<List<Place>>>> =
        places.listByRegion(code, categories)
            .map { fetched -> fetched.map { it.toUiState() } }
            .catch { emit(Fetched(UiState.Failed(it))) }
            .onStart { emit(Fetched(UiState.Loading)) }

    companion object {
        fun factory(
            initialSelection: Set<PlaceCategory>,
            regions: RegionRepository,
            places: PlaceRepository,
            deviceLocation: DeviceLocation,
            recentStore: RecentRegionStore,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaceListViewModel(initialSelection, regions, places, deviceLocation, recentStore) as T
        }
    }
}
