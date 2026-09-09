package io.github.junkie300.petapp.ui.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.data.DeviceLocation
import io.github.junkie300.petapp.data.Fetched
import io.github.junkie300.petapp.data.RecentRegionStore
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.data.RegionRepository
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「현재 위치로」가 어떻게 됐는지 (`spec.md §5.2` S-01).
 *
 * ⚠️ **한 값으로는 부족하다.** "못 잡았다"(위치가 없다)와 "못 찾았다"(가까운 읍면동이 없다)는
 * 사용자가 할 일이 다르다 — 앞엣것은 창가로 나가면 되고, 뒤엣것은 손으로 고르는 수밖에 없다
 * (`spec.md §5.3` 이 가르라고 한 그 구분이다).
 *
 * 권한 거절은 여기 없다. 그건 화면이 창에서 직접 받는 답이라 조회를 시작하지도 못한다.
 */
sealed interface HereState {
    data object Idle : HereState

    /** 최대 8초까지 걸린다 (D-81 의 제공자별 대기). 그동안 버튼이 도는 표시를 낸다. */
    data object Working : HereState

    /** 현재 위치의 읍면동을 찾았다. 화면이 이걸 보고 곧장 그 지역으로 나간다. */
    data class Found(val region: Region) : HereState

    /** 위치를 못 잡았다 — 껐거나, 실내라 안 잡힌다. */
    data object Failed : HereState

    /** 위치는 잡았는데 가까운 읍면동이 없다 — 바다 위이거나, 오프라인이라 사본에 없다. */
    data object NotFound : HereState
}

/** S-01 화면이 그리는 데 필요한 전부. 화면은 이 상태만 보고 그린다. */
data class RegionPickerUiState(
    val sido: UiState<List<Region>> = UiState.Loading,
    val sigungu: UiState<List<Region>> = UiState.Empty,
    val dong: UiState<List<Region>> = UiState.Empty,
    val recent: List<Region> = emptyList(),
    val selectedSido: Region? = null,
    val selectedSigungu: Region? = null,
    val selectedDong: Region? = null,
    val searchKeyword: String = "",
    val searchResult: UiState<List<Region>>? = null, // null = 검색 중이 아님
    /**
     * 마지막으로 성공한 조회가 **받아 둔 사본**이었으면 그 시각. 오프라인 배너가 이걸 본다.
     * 서버에서 새로 받으면 null 로 돌아간다 — 網이 살아나면 배너도 사라져야 한다.
     */
    val cachedAt: Long? = null,
    /** 「현재 위치로」의 결말. 화면이 이걸 보고 나가거나, 왜 못 갔는지 한 줄로 적는다. */
    val here: HereState = HereState.Idle,
) {
    /** 하위 단계는 상위가 선택됐을 때만 열린다 (spec.md §5.2). */
    val sigunguEnabled: Boolean get() = selectedSido != null
    val dongEnabled: Boolean get() = selectedSigungu != null
    val confirmable: Boolean get() = selectedDong != null
}

class RegionPickerViewModel(
    private val repository: RegionRepository,
    private val recentStore: RecentRegionStore,
    private val deviceLocation: DeviceLocation,
) : ViewModel() {

    private val _state = MutableStateFlow(RegionPickerUiState())
    val state: StateFlow<RegionPickerUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** 지금 위치 권한이 있는가. 버튼이 **물어볼지 바로 잡을지**를 이걸로 정한다 (D-82 와 같다). */
    val hasLocationPermission: Boolean get() = deviceLocation.hasPermission

    /** 마지막으로 읽은 최근 지역 코드. [retry] 가 이름을 다시 불러올 때 쓴다. */
    private var recentCodes: List<String> = emptyList()

    init {
        loadSido()
        observeRecent()
    }

    /**
     * 화면 전체 다시 시도.
     *
     * ⚠️ **시도 목록만 다시 부르면 안 된다.** 오프라인으로 앱을 켠 뒤 網이 살아나도
     * 최근 지역 칩이 안 돌아오던 것이 그 때문이었다 — 칩은 `recentStore.codes` 가 바뀔 때만
     * 다시 그려지는데, 코드는 그대로라 흐름이 다시 흐르지 않는다.
     */
    fun retry() {
        loadSido()
        refreshRecent()
    }

    fun loadSido() {
        _state.update { it.copy(sido = UiState.Loading) }
        viewModelScope.launch {
            _state.update { it.copy(sido = fetchList { repository.sidoList() }) }
        }
    }

    private fun observeRecent() {
        viewModelScope.launch {
            recentStore.codes.collect { codes ->
                recentCodes = codes
                loadRecent(codes)
            }
        }
    }

    private fun refreshRecent() {
        viewModelScope.launch { loadRecent(recentCodes) }
    }

    /**
     * 최근 지역 칩. **사본이 있으면 서버를 기다리지 않고 먼저 뜬다** (D-79).
     *
     * 부가 기능이므로 실패해도 화면 전체를 실패로 만들지 않는다.
     * (網이 끊겨도 한 번 본 지역이면 사본에서 이름이 나온다 — RegionRepository.)
     */
    private suspend fun loadRecent(codes: List<String>) {
        repository.byCodes(codes)
            .catch { }
            .collect { fetched ->
                // ⚠️ 서버 답을 기다리는 중인 사본은 배너를 건드리지 않는다. 다른 조회가
                // 이미 띄워 둔 배너를 지워 버리면 안 된다.
                if (!fetched.awaitingServer) markSource(fetched.offlineSince)
                _state.update { it.copy(recent = fetched.data) }
            }
    }

    fun selectSido(region: Region) {
        _state.update {
            it.copy(
                selectedSido = region,
                selectedSigungu = null,
                selectedDong = null,
                sigungu = UiState.Loading,
                dong = UiState.Empty,
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(sigungu = fetchList { repository.children(region.code) }) }
        }
    }

    fun selectSigungu(region: Region) {
        _state.update {
            it.copy(selectedSigungu = region, selectedDong = null, dong = UiState.Loading)
        }
        viewModelScope.launch {
            _state.update { it.copy(dong = fetchList { repository.children(region.code) }) }
        }
    }

    fun selectDong(region: Region) {
        _state.update { it.copy(selectedDong = region) }
    }

    fun updateSearchKeyword(keyword: String) {
        _state.update { it.copy(searchKeyword = keyword) }
        searchJob?.cancel()
        if (keyword.trim().length < RegionRepository.MIN_SEARCH_LENGTH) {
            _state.update { it.copy(searchResult = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS) // 글자마다 때리지 않는다
            _state.update { it.copy(searchResult = UiState.Loading) }
            _state.update { it.copy(searchResult = fetchList { repository.searchDong(keyword) }) }
        }
    }

    /** 검색 결과에서 고르면 3단 드롭다운도 그 지역에 맞춰 되짚어 채운다. */
    fun selectFromSearch(dong: Region) {
        _state.update { it.copy(selectedDong = dong, searchKeyword = "", searchResult = null) }
        viewModelScope.launch {
            val sidoCode = dong.code.take(SIDO_CODE_LENGTH).padEnd(dong.code.length, '0')
            // 여기서는 값 하나만 있으면 된다 — 사용자가 고른 뒤 드롭다운을 되짚어 채우는 자리다.
            val sido = runCatching { repository.byCodes(listOf(sidoCode)).last() }
                .getOrNull()?.data?.firstOrNull()
            val sigungu = dong.parentCode?.let { code ->
                runCatching { repository.byCodes(listOf(code)).last() }.getOrNull()?.data?.firstOrNull()
            }
            _state.update { it.copy(selectedSido = sido, selectedSigungu = sigungu) }
            if (sido != null) {
                _state.update { it.copy(sigungu = fetchList { repository.children(sido.code) }) }
            }
            if (sigungu != null) {
                _state.update { it.copy(dong = fetchList { repository.children(sigungu.code) }) }
            }
        }
    }

    /** 3단 드롭다운으로 고른 지역을 확정한다. */
    fun confirmSelection() {
        _state.value.selectedDong?.let(::remember)
    }

    /**
     * 최근 지역 칩처럼 드롭다운을 거치지 않고 바로 정하는 경로.
     * 여기서도 remember() 를 불러야 홈의 지역 칩이 따라온다 — 홈은 이 저장소만 보고 있다.
     */
    fun confirmRegion(region: Region) {
        _state.update { it.copy(selectedDong = region) }
        remember(region)
    }

    /**
     * 「현재 위치로」 (`spec.md §5.2` S-01).
     *
     * 지도의 그것(D-82)과 **다른 일이다** — 저쪽은 카메라만 옮기고, 여기서는 **지역을 고른다.**
     * 좌표로 장소를 찾지 않고 [RegionRepository.nearestDong] 으로 읍면동을 찾아 그것을 고른
     * 것으로 삼는다 — 그러면 그다음은 3단 드롭다운으로 고른 것과 완전히 같은 길을 지난다
     * (D-24·D-54·D-86).
     *
     * ⚠️ **경계가 아니라 가장 가까운 중심이다** (D-86). 동네 경계 근처에서는 옆 동이 나올 수
     * 있어서, 화면이 어디로 갔는지를 이름으로 말해 준다.
     */
    fun goToCurrentLocation() {
        // 두 번 눌러도 조회는 하나만 돈다. 위치 하나에 최대 8초가 걸린다 (D-81).
        if (_state.value.here == HereState.Working) return
        viewModelScope.launch {
            _state.update { it.copy(here = HereState.Working) }
            val point = deviceLocation.current()
            if (point == null) {
                _state.update { it.copy(here = HereState.Failed) }
                return@launch
            }
            // 조회가 깨지는 것과 근처에 없는 것을 갈라 적지 않는다 — 어느 쪽이든 사용자가 할 수
            // 있는 일이 "손으로 고른다"로 같다 (D-86 의 「이 지역에서 다시 검색」과 같은 판단).
            val found = runCatching { repository.nearestDong(point).data }.getOrNull()
            if (found == null) {
                _state.update { it.copy(here = HereState.NotFound) }
                return@launch
            }
            // 나가기 **전에** 저장한다. 홈은 이 저장소만 보고 지역 칩을 그리므로, 먼저 나가면
            // 옛 지역이 한 번 스쳐 보인다.
            recentStore.remember(found.code)
            _state.update { it.copy(selectedDong = found, here = HereState.Found(found)) }
        }
    }

    /** 결말을 치운다. 화면이 [HereState.Found] 로 나간 뒤에 부른다 — 두 번 나가지 않게. */
    fun clearHere() {
        _state.update { it.copy(here = HereState.Idle) }
    }

    private fun remember(region: Region) {
        viewModelScope.launch { recentStore.remember(region.code) }
    }

    /**
     * 조회 하나를 화면 상태로 바꾸면서, 그 값이 사본에서 나왔는지를 함께 기록한다.
     * 빈 목록은 성공이 아니라 [UiState.Empty] 다.
     */
    private suspend fun <T> fetchList(block: suspend () -> Fetched<List<T>>): UiState<List<T>> =
        runCatching { block() }.fold(
            onSuccess = { fetched ->
                markSource(fetched.offlineSince)
                fetched.data.toUiState()
            },
            onFailure = { UiState.Failed(it) },
        )

    /** 실패는 배너를 건드리지 않는다 — 실패 화면에는 이미 재시도 버튼이 있다. */
    private fun markSource(cachedAt: Long?) {
        _state.update { it.copy(cachedAt = cachedAt) }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L

        /** 법정동코드 10자리 중 앞 2자리가 시도. 시도 행의 코드는 그 뒤가 전부 0 이다. */
        private const val SIDO_CODE_LENGTH = 2

        fun factory(
            repository: RegionRepository,
            recentStore: RecentRegionStore,
            deviceLocation: DeviceLocation,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RegionPickerViewModel(repository, recentStore, deviceLocation) as T
        }
    }
}
