package io.github.junkie300.petapp.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.GeoPoint
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.DetailScaffold
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.OfflineBanner
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.theme.Spacing
import io.github.junkie300.petapp.ui.theme.tabularFigures

/**
 * 장소 목록 — 고른 읍면동의 한 카테고리를 목록으로 보여준다.
 *
 * `spec.md §5.2` 의 S-02 는 지도 + 바텀시트 목록인데, 카카오 네이티브 앱 키가 없어
 * **목록을 먼저 만든다.** 지도가 붙으면 이 목록이 바텀시트 안으로 들어간다 —
 * 카드와 조회는 그대로 산다.
 *
 * 카드의 거리는 위치를 얻었을 때만 붙는다 — 없으면 "거리 보기"가 대신 뜬다 (D-81).
 */
@Composable
fun PlaceListScreen(
    viewModel: PlaceListViewModel,
    /** 이 화면은 카테고리 하나로 고정이다. 지도만 칩으로 여럿을 켠다 (D-76). */
    category: PlaceCategory,
    onBack: () -> Unit,
    onPlaceClick: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val origin by viewModel.origin.collectAsStateWithLifecycle()
    val categoryName = stringResource(category.labelRes)

    DetailScaffold(
        title = categoryName,
        subtitle = (state as? PlaceListUiState.Ready)?.region?.fullName,
        onBack = onBack,
        modifier = modifier,
    ) { insets ->
        when (state) {
            is PlaceListUiState.Loading -> SkeletonRows(
                count = 5,
                modifier = Modifier
                    .padding(insets)
                    .padding(Spacing.screenHorizontal),
            )

            is PlaceListUiState.NoRegion -> EmptyMessage(
                text = stringResource(R.string.place_list_no_region),
                modifier = Modifier
                    .padding(insets)
                    .padding(Spacing.screenHorizontal),
            )

            is PlaceListUiState.Failed -> FailedMessage(
                onRetry = viewModel::retry,
                modifier = Modifier
                    .padding(insets)
                    .padding(Spacing.screenHorizontal),
            )

            is PlaceListUiState.Ready -> PlaceListBody(
                places = (state as PlaceListUiState.Ready).places,
                cachedAt = (state as PlaceListUiState.Ready).cachedAt,
                categoryName = categoryName,
                contentPadding = insets,
                origin = origin,
                onLocationGranted = viewModel::refreshLocation,
                onRetry = viewModel::retry,
                onPlaceClick = onPlaceClick,
            )
        }
    }
}

@Composable
private fun PlaceListBody(
    places: UiState<List<Place>>,
    /** null 이 아니면 이 목록은 받아 둔 사본이다. 실패·빈 목록에는 사본이 없다 — 그건 캐시 미스다. */
    cachedAt: Long?,
    categoryName: String,
    contentPadding: PaddingValues,
    /** 거리를 재는 기준점. null 이면 카드에 거리가 안 붙고 대신 "거리 보기"가 뜬다 (D-81). */
    origin: GeoPoint?,
    onLocationGranted: () -> Unit,
    onRetry: () -> Unit,
    onPlaceClick: (Place) -> Unit,
) {
    when (places) {
        is UiState.Loading -> SkeletonRows(
            count = 5,
            modifier = Modifier
                .padding(contentPadding)
                .padding(Spacing.screenHorizontal),
        )

        // ⚠️ "이 동네엔 없다"와 "못 불러왔다"는 다른 화면이다 (spec.md §5.3).
        is UiState.Empty -> EmptyMessage(
            text = stringResource(R.string.place_list_empty, categoryName),
            modifier = Modifier
                .padding(contentPadding)
                .padding(Spacing.screenHorizontal),
        )

        is UiState.Failed -> FailedMessage(
            onRetry = onRetry,
            modifier = Modifier
                .padding(contentPadding)
                .padding(Spacing.screenHorizontal),
        )

        is UiState.Success -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            cachedAt?.let { moment ->
                item { OfflineBanner(cachedAt = moment, modifier = Modifier.padding(bottom = 8.dp)) }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.place_list_count, places.data.size),
                        style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DistanceRow(
                        origin = origin,
                        onLocationGranted = onLocationGranted,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            // 지도 바텀시트와 **같은 항목 코드**를 쓴다 (D-26).
            placeItems(places = places.data, onPlaceClick = onPlaceClick, origin = origin)
        }
    }
}
