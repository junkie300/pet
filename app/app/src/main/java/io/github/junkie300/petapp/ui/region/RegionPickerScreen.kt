package io.github.junkie300.petapp.ui.region

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.OfflineBanner
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.theme.CardShape
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.Spacing

/**
 * S-01 지역 선택 (spec.md §5.2).
 *
 * 시도 → 시군구 → 읍면동 3단. 각 단계는 상위가 정해져야 열린다.
 * "현재 위치로" 버튼은 카카오맵 SDK·위치 권한이 붙는 시점에 추가한다.
 *
 * 형태는 시안(이태우_디자인시안)을 따른다 — 웜 크림 배경 위에 흰 카드, 알약 검색창,
 * 딥그린 주요 버튼 (D-47). 시안이 약속하던 별점·사진은 넣지 않는다 (D-40).
 */
@Composable
fun RegionPickerScreen(
    viewModel: RegionPickerViewModel,
    onRegionConfirmed: (Region) -> Unit,
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
        Header(selected = state.selectedDong)

        // 사본으로 그린 목록이면 말해 준다. 오프라인 검색은 **이미 받아 둔 읍면동**만 찾으므로
        // 결과가 적을 수 있는데, 배너가 없으면 "우리 동네가 없다"로 읽힌다 (spec.md §5.3).
        state.cachedAt?.let { OfflineBanner(cachedAt = it) }

        SearchField(
            keyword = state.searchKeyword,
            onKeywordChange = viewModel::updateSearchKeyword,
        )

        state.searchResult?.let { result ->
            SearchResults(
                result = result,
                onSelect = viewModel::selectFromSearch,
                onRetry = { viewModel.updateSearchKeyword(state.searchKeyword) },
            )
        }

        if (state.recent.isNotEmpty()) {
            RecentRegions(
                regions = state.recent,
                onSelect = { region ->
                    viewModel.confirmRegion(region)
                    onRegionConfirmed(region)
                },
            )
        }

        StepCard(state = state, viewModel = viewModel)

        Button(
            onClick = {
                val dong = state.selectedDong ?: return@Button
                viewModel.confirmSelection()
                onRegionConfirmed(dong)
            },
            enabled = state.confirmable,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = stringResource(R.string.region_confirm),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * 시안의 홈 상단을 이 화면에 맞게 옮긴 것. 선택한 지역을 **읍면동까지** 항상 보여준다 —
 * 이 앱의 기준점이 "현재 위치"가 아니라 "내가 고른 지역"이라는 신호다 (D-39).
 */
@Composable
private fun Header(selected: Region?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = stringResource(R.string.cd_location),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = selected?.fullName ?: stringResource(R.string.region_selected_none),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = stringResource(R.string.region_picker_lead),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.region_step_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 시안의 알약 검색창. 카드와 달리 테두리 없이 흰 면으로 띄운다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        placeholder = {
            Text(
                text = stringResource(R.string.region_search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = PillShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    )
}

@Composable
private fun SearchResults(
    result: UiState<List<Region>>,
    onSelect: (Region) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (result) {
        is UiState.Loading -> SkeletonRows(modifier = modifier)
        is UiState.Empty -> EmptyMessage(stringResource(R.string.state_empty_regions), modifier)
        is UiState.Failed -> FailedMessage(onRetry = onRetry, modifier = modifier)
        is UiState.Success -> Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.heightIn(max = 260.dp)) {
                result.data.forEach { region ->
                    DropdownMenuItem(
                        text = {
                            Text(region.fullName, style = MaterialTheme.typography.bodyLarge)
                        },
                        onClick = { onSelect(region) },
                    )
                }
            }
        }
    }
}

/** 시안의 필터 칩 형태. 최근 지역은 여행 준비 중 반복 조회를 줄이는 장치다 (spec.md §5.2). */
@Composable
private fun RecentRegions(
    regions: List<Region>,
    onSelect: (Region) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
        Text(
            text = stringResource(R.string.region_recent),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        // ⚠️ **Row 로 두면 안 된다.** 칩 셋이 한 줄을 넘으면 마지막 칩이 남은 폭으로 눌려
        // 글자가 한 자씩 세로로 쌓인다(실기기 실측 — `중구 필동2가 · 중구 충무로3가 · 강남구 삼성동`).
        // 지역명 길이는 우리가 정하는 값이 아니므로 넘칠 때는 줄을 바꾼다.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            regions.forEach { region ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(region) },
                    shape = PillShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    label = {
                        // 동 이름만 쓰면 "신사동"처럼 여러 구에 있는 이름이 구분되지 않는다.
                        Text("${region.sigunguName.orEmpty()} ${region.shortName}".trim())
                    },
                )
            }
        }
    }
}

/** 3단 드롭다운을 흰 카드 하나에 묶는다. 시안이 관련 항목을 카드로 묶는 방식이다. */
@Composable
private fun StepCard(
    state: RegionPickerUiState,
    viewModel: RegionPickerViewModel,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            RegionDropdown(
                label = stringResource(R.string.region_level_sido),
                state = state.sido,
                selected = state.selectedSido,
                enabled = true,
                onSelect = viewModel::selectSido,
                onRetry = viewModel::retry,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RegionDropdown(
                label = stringResource(R.string.region_level_sigungu),
                state = state.sigungu,
                selected = state.selectedSigungu,
                enabled = state.sigunguEnabled,
                onSelect = viewModel::selectSigungu,
                onRetry = { state.selectedSido?.let(viewModel::selectSido) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RegionDropdown(
                label = stringResource(R.string.region_level_eupmyeondong),
                state = state.dong,
                selected = state.selectedDong,
                enabled = state.dongEnabled,
                onSelect = viewModel::selectDong,
                onRetry = { state.selectedSigungu?.let(viewModel::selectSigungu) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionDropdown(
    label: String,
    state: UiState<List<Region>>,
    selected: Region?,
    enabled: Boolean,
    onSelect: (Region) -> Unit,
    onRetry: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.shortName.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(label) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            )
            if (state is UiState.Success) {
                ExposedDropdownMenu(
                    expanded = expanded && enabled,
                    onDismissRequest = { expanded = false },
                ) {
                    state.data.forEach { region ->
                        DropdownMenuItem(
                            text = { Text(region.shortName) },
                            onClick = {
                                onSelect(region)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // 로딩·빈 상태·실패를 구분해서 보여준다 (spec.md §5.3).
        when (state) {
            is UiState.Loading -> SkeletonRows(count = 1)
            is UiState.Failed -> FailedMessage(onRetry = onRetry)
            is UiState.Empty -> if (enabled) EmptyMessage(stringResource(R.string.state_empty_regions))
            is UiState.Success -> Unit
        }
    }
}
