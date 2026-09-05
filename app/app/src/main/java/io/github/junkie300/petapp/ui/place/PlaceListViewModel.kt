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
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 한 읍면동의 한 카테고리 목록.
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
    ) : PlaceListUiState
}

class PlaceListViewModel(
    val category: PlaceCategory,
    private val regions: RegionRepository,
    private val places: PlaceRepository,
    recentStore: RecentRegionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaceListUiState>(PlaceListUiState.Loading)
    val state: StateFlow<PlaceListUiState> = _state.asStateFlow()

    private var currentCode: String? = null

    init {
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

    fun retry() {
        viewModelScope.launch { load(currentCode) }
    }

    private suspend fun load(code: String?) {
        if (code == null) {
            _state.value = PlaceListUiState.NoRegion
            return
        }
        _state.value = PlaceListUiState.Loading
        val region = runCatching { regions.byCodes(listOf(code)) }.getOrNull()?.firstOrNull()
        if (region == null) {
            _state.value = PlaceListUiState.Failed(code)
            return
        }
        // 지역 이름을 먼저 띄운다. 목록을 기다리느라 머리말까지 비워 두지 않는다.
        _state.value = PlaceListUiState.Ready(region, UiState.Loading)
        val result = runCatching { places.listByRegion(region.code, category) }
            .fold(onSuccess = { it.toUiState() }, onFailure = { UiState.Failed(it) })
        _state.value = PlaceListUiState.Ready(region, result)
    }

    companion object {
        fun factory(
            category: PlaceCategory,
            regions: RegionRepository,
            places: PlaceRepository,
            recentStore: RecentRegionStore,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaceListViewModel(category, regions, places, recentStore) as T
        }
    }
}
