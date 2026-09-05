package io.github.junkie300.petapp.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.ui.common.CategoryBadge
import io.github.junkie300.petapp.ui.common.DetailScaffold
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.theme.CardShape
import io.github.junkie300.petapp.ui.theme.Spacing

/**
 * 장소 목록 — 고른 읍면동의 한 카테고리를 목록으로 보여준다.
 *
 * `spec.md §5.2` 의 S-02 는 지도 + 바텀시트 목록인데, 카카오 네이티브 앱 키가 없어
 * **목록을 먼저 만든다.** 지도가 붙으면 이 목록이 바텀시트 안으로 들어간다 —
 * 카드와 조회는 그대로 산다.
 *
 * 거리는 아직 없다 (`spec.md §6.4` 의 카드 규격 중 하나). 현재 위치 권한이 붙는 시점에 넣는다.
 */
@Composable
fun PlaceListScreen(
    viewModel: PlaceListViewModel,
    onBack: () -> Unit,
    onPlaceClick: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categoryName = stringResource(viewModel.category.labelRes)

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
                categoryName = categoryName,
                contentPadding = insets,
                onRetry = viewModel::retry,
                onPlaceClick = onPlaceClick,
            )
        }
    }
}

@Composable
private fun PlaceListBody(
    places: UiState<List<Place>>,
    categoryName: String,
    contentPadding: PaddingValues,
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
            item {
                Text(
                    text = stringResource(R.string.place_list_count, places.data.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(places.data, key = { it.id }) { place ->
                PlaceCard(place = place, onClick = { onPlaceClick(place) })
            }
        }
    }
}

/** 장소 카드 (spec.md §6.4) — 이름 · 카테고리 배지 · 주소 1줄 · 영업상태. */
@Composable
private fun PlaceCard(place: Place, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            place.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                place.placeCategory?.let { CategoryBadge(it) }
                place.tel?.let { tel ->
                    Text(
                        text = tel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
