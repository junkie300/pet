package io.github.junkie300.petapp.ui.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
) {
    /** 하위 단계는 상위가 선택됐을 때만 열린다 (spec.md §5.2). */
    val sigunguEnabled: Boolean get() = selectedSido != null
    val dongEnabled: Boolean get() = selectedSigungu != null
    val confirmable: Boolean get() = selectedDong != null
}

class RegionPickerViewModel(
    private val repository: RegionRepository,
    private val recentStore: RecentRegionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RegionPickerUiState())
    val state: StateFlow<RegionPickerUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

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

        fun factory(repository: RegionRepository, recentStore: RecentRegionStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RegionPickerViewModel(repository, recentStore) as T
            }
    }
}
