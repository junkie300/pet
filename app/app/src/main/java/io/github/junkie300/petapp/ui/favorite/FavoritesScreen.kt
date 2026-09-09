package io.github.junkie300.petapp.ui.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.DeviceLocation
import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.distanceMeters
import io.github.junkie300.petapp.data.favorite.FavoriteDao
import io.github.junkie300.petapp.data.favorite.FavoritePlace
import io.github.junkie300.petapp.ui.common.DetailScaffold
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.toUiState
import io.github.junkie300.petapp.ui.place.DistanceRow
import io.github.junkie300.petapp.ui.place.PlaceCard
import io.github.junkie300.petapp.ui.theme.Spacing
import io.github.junkie300.petapp.ui.theme.tabularFigures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * S-07 즐겨찾기 (spec.md §5.2).
 *
 * **로컬 DB 만 본다 — 네트워크를 타지 않는다.** 그래서 실패 상태가 없다.
 * 비어 있는 것은 실패가 아니라 "아직 담은 게 없다"이며, 그 둘을 가르라는 §5.3 의 요구가
 * 여기서는 자동으로 지켜진다.
 */
class FavoritesViewModel(
    favorites: FavoriteDao,
    private val deviceLocation: DeviceLocation,
) : ViewModel() {

    val state: StateFlow<UiState<List<FavoritePlace>>> = favorites.observeAll()
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), UiState.Loading)

    /**
     * 거리를 재는 기준점 (D-88). 목록·지도와 **같은 뜻**이다 — 지금 내가 있는 자리에서의
     * 직선 거리다. 즐겨찾기는 전국이 섞이지만 그래도 뜻은 달라지지 않는다: 목록에서도 지역은
     * *무엇을 보여줄지*만 정했고, 거리는 처음부터 현재 위치에서 쟀다 (D-24·D-59·D-81).
     */
    private val _origin = MutableStateFlow<GeoPoint?>(null)
    val origin: StateFlow<GeoPoint?> = _origin.asStateFlow()

    init {
        // 목록에서 이미 허용한 사람에게 또 묻지 않는다. 권한이 없으면 화면의 `거리 보기` 가 묻는다.
        if (deviceLocation.hasPermission) refreshLocation()
    }

    /** 권한을 방금 받았을 때 부른다. 못 잡아도 **이전 위치를 지우지 않는다** (D-81 과 같은 규칙). */
    fun refreshLocation() {
        viewModelScope.launch {
            deviceLocation.current()?.let { _origin.value = it }
        }
    }

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

        fun factory(favorites: FavoriteDao, deviceLocation: DeviceLocation) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FavoritesViewModel(favorites, deviceLocation) as T
            }
    }
}

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onBack: () -> Unit,
    onPlaceClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val origin by viewModel.origin.collectAsStateWithLifecycle()

    DetailScaffold(
        title = stringResource(R.string.more_favorites),
        onBack = onBack,
        modifier = modifier,
    ) { insets ->
        val bodyModifier = Modifier
            .padding(insets)
            .padding(horizontal = Spacing.screenHorizontal)

        when (val current = state) {
            is UiState.Loading -> SkeletonRows(count = 3, modifier = bodyModifier)
            is UiState.Empty -> EmptyMessage(stringResource(R.string.favorites_empty), bodyModifier)
            // 로컬 DB 라 실패가 날 자리가 없다. 타입을 맞추기 위한 가지다.
            is UiState.Failed -> EmptyMessage(stringResource(R.string.favorites_empty), bodyModifier)
            is UiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.place_list_count, current.data.size),
                        style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                // 목록·지도와 같은 줄, 같은 코드다 (D-81·D-88). 위치를 이미 얻었으면 아무것도
                // 그리지 않고 카드가 거리로 말한다.
                item { DistanceRow(origin = origin, onLocationGranted = viewModel::refreshLocation) }
                items(current.data, key = { it.placeId }) { favorite ->
                    PlaceCard(
                        name = favorite.name,
                        address = favorite.address,
                        category = PlaceCategory.fromDbValue(favorite.category),
                        tel = favorite.tel,
                        onClick = { onPlaceClick(favorite.placeId) },
                        // 버전 1 때 담은 스냅샷에는 좌표가 없다 (D-64). 그때는 거리도 없다 —
                        // 지어내지 않는다.
                        distanceMeters = origin?.let { from ->
                            val lat = favorite.lat
                            val lng = favorite.lng
                            if (lat != null && lng != null) distanceMeters(from, lat, lng) else null
                        },
                    )
                }
            }
        }
    }
}
