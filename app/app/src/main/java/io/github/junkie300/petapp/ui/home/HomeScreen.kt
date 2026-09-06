package io.github.junkie300.petapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.OfflineBanner
import io.github.junkie300.petapp.ui.common.SkeletonBox
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.theme.CardShape
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.Spacing
import io.github.junkie300.petapp.ui.theme.tabularFigures

/**
 * S-00 홈 허브 (spec.md §5.2 · D-39).
 *
 * 이 화면이 지키는 것 두 가지.
 *
 * 1. **지역 칩은 읍면동까지 보여준다.** 앱의 기준점이 "현재 위치"가 아니라 "내가 고른 지역"
 *    이라는 신호이며, 단위를 시군구로 낮추면 제품이 흐려진다 (D-39).
 * 2. **"내 주변 N km" 를 홈의 주인공으로 두지 않는다.** 위치 기반 탐색은 S-02 안에 둔다.
 *    시안에는 있었지만 D-24 가 의도적으로 피한 자리다.
 *
 * 시안의 큐레이션 섹션·별점·장소 사진도 넣지 않는다 (D-39·D-40).
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRegionClick: () -> Unit,
    onCategoryClick: (PlaceCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
    ) {
        // 網이 끊긴 채로 사본을 그리고 있으면 맨 위에 말해 준다 (spec.md §5.3).
        (state as? HomeUiState.Ready)?.cachedAt?.let { OfflineBanner(cachedAt = it) }

        RegionChip(state = state, onClick = onRegionClick)

        Text(
            text = stringResource(R.string.home_lead),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        // 지역 이름 자체를 못 불러온 경우. 건수 실패와 다른 자리에 둔다.
        if (state is HomeUiState.Failed) {
            FailedMessage(onRetry = viewModel::retry)
        }

        CategoryGrid(state = state, onCategoryClick = onCategoryClick)

        // 건수만 실패한 경우. 지역 칩과 그리드는 그대로 두고 재시도만 붙인다.
        if ((state as? HomeUiState.Ready)?.counts is UiState.Failed) {
            FailedMessage(onRetry = viewModel::retry)
        }
    }
}

/** 상단 지역 칩. 탭하면 S-01 을 연다 (spec.md §6.4 — 읍면동까지 표기 · 아이콘 + 펼침 화살표). */
@Composable
private fun RegionChip(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (state) {
        is HomeUiState.Ready -> state.region.fullName
        is HomeUiState.Loading -> stringResource(R.string.home_region_loading)
        is HomeUiState.NoRegion -> stringResource(R.string.home_region_empty)
        // 고른 지역은 있는데 이름을 못 읽은 것이다. "골라 주세요"로 적으면 사용자가
        // 자기 선택이 날아간 줄 안다 — spec.md §5.3 이 가르라고 한 그 구분이다.
        is HomeUiState.Failed -> stringResource(R.string.home_region_failed)
    }
    val chosen = state is HomeUiState.Ready

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.heightIn(min = 48.dp), // 최소 터치 타겟 (spec.md §6.5)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null, // 바로 옆 지역명이 같은 내용을 읽어 준다
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (chosen) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_change_region),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 첫 실행 안내다. 실패 상태에서까지 띄우면 바로 아래 실패 문구와 어긋난다.
        if (state is HomeUiState.NoRegion) {
            Text(
                text = stringResource(R.string.home_region_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 카테고리 6칸 그리드. 2열 x 3행.
 *
 * LazyVerticalGrid 를 쓰지 않는다 — 칸 수가 6개로 고정이라 지연 로딩이 필요 없고,
 * 세로 스크롤 Column 안에 넣으면 중첩 스크롤 충돌이 난다.
 */
@Composable
private fun CategoryGrid(
    state: HomeUiState,
    onCategoryClick: (PlaceCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        HomeCategory.entries.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        state = state,
                        onClick = { category.place?.let(onCategoryClick) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: HomeCategory,
    state: HomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            // 적재 전 카테고리는 누를 수 없다. 눌러 봐야 빈 화면이 나오는 것보다 낫다.
            .then(if (category.loaded) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null, // 바로 아래 라벨이 같은 내용을 읽어 준다
                    tint = category.tint(),
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CountLabel(category = category, state = state)
        }
    }
}

/**
 * 칸 하나의 건수 줄. 네 경우를 서로 다르게 적는다.
 *
 * - 적재 전 카테고리 → "준비 중". **0곳이 아니다.** 0곳으로 적으면 "이 동네엔 없구나"로 읽힌다
 * - 지역 미선택 → 빈 줄. 셀 것이 없다
 * - 세는 중 → 스켈레톤 (spec.md §5.3 은 스피너를 지양한다)
 * - 실패 → "—", 그리고 그리드 아래에 재시도 버튼
 */
@Composable
private fun CountLabel(category: HomeCategory, state: HomeUiState, modifier: Modifier = Modifier) {
    if (!category.loaded) {
        Text(
            text = stringResource(R.string.home_count_pending),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    when (val counts = (state as? HomeUiState.Ready)?.counts) {
        null -> Box(modifier.height(COUNT_LINE_HEIGHT))
        is UiState.Loading -> SkeletonBox(width = 48.dp, height = COUNT_LINE_HEIGHT, modifier = modifier)
        is UiState.Success -> Text(
            text = stringResource(R.string.home_count, counts.data[category.place] ?: 0),
            // 그리드 두 칸의 숫자가 나란히 서므로 자리 폭을 맞춘다 (spec.md §6.3)
            style = MaterialTheme.typography.titleMedium.tabularFigures(),
            fontWeight = FontWeight.Bold,
            color = category.tint(),
            modifier = modifier,
        )
        // 건수 맵은 비어 있어도 Empty 가 아니라 Success 다. 여기 오는 것은 Failed 뿐이다.
        else -> Text(
            text = stringResource(R.string.home_count_unknown),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

private const val GRID_COLUMNS = 2
private val COUNT_LINE_HEIGHT = 20.dp
