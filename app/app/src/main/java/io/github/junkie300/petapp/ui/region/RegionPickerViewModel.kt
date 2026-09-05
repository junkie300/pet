package io.github.junkie300.petapp.ui.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    init {
        loadSido()
        observeRecent()
    }

    fun loadSido() {
        _state.update { it.copy(sido = UiState.Loading) }
        viewModelScope.launch {
            _state.update { it.copy(sido = runCatchingList { repository.sidoList() }) }
        }
    }

    private fun observeRecent() {
        viewModelScope.launch {
            recentStore.codes.collect { codes ->
                // 최근 지역은 부가 기능이다. 실패해도 화면 전체를 실패로 만들지 않는다.
                val regions = runCatching { repository.byCodes(codes) }.getOrDefault(emptyList())
                _state.update { it.copy(recent = regions) }
            }
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
            _state.update { it.copy(sigungu = runCatchingList { repository.children(region.code) }) }
        }
    }

    fun selectSigungu(region: Region) {
        _state.update {
            it.copy(selectedSigungu = region, selectedDong = null, dong = UiState.Loading)
        }
        viewModelScope.launch {
            _state.update { it.copy(dong = runCatchingList { repository.children(region.code) }) }
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
            _state.update { it.copy(searchResult = runCatchingList { repository.searchDong(keyword) }) }
        }
    }

    /** 검색 결과에서 고르면 3단 드롭다운도 그 지역에 맞춰 되짚어 채운다. */
    fun selectFromSearch(dong: Region) {
        _state.update { it.copy(selectedDong = dong, searchKeyword = "", searchResult = null) }
        viewModelScope.launch {
            val sidoCode = dong.code.take(SIDO_CODE_LENGTH).padEnd(dong.code.length, '0')
            val sido = runCatching { repository.byCodes(listOf(sidoCode)) }.getOrNull()?.firstOrNull()
            val sigungu = dong.parentCode
                ?.let { code -> runCatching { repository.byCodes(listOf(code)) }.getOrNull()?.firstOrNull() }
            _state.update { it.copy(selectedSido = sido, selectedSigungu = sigungu) }
            if (sido != null) {
                _state.update { it.copy(sigungu = runCatchingList { repository.children(sido.code) }) }
            }
            if (sigungu != null) {
                _state.update { it.copy(dong = runCatchingList { repository.children(sigungu.code) }) }
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

    private inline fun <T> runCatchingList(block: () -> List<T>): UiState<List<T>> =
        runCatching(block).fold(onSuccess = { it.toUiState() }, onFailure = { UiState.Failed(it) })

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
