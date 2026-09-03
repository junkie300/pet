package io.github.junkie300.petapp.ui.region

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Region
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState

/**
 * S-01 지역 선택 (spec.md §5.2).
 *
 * 시도 → 시군구 → 읍면동 3단. 각 단계는 상위가 정해져야 열린다.
 * "현재 위치로" 버튼은 카카오맵 SDK·위치 권한이 붙는 시점에 추가한다.
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.region_picker_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (state.recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.region_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recent.forEach { region ->
                        AssistChip(
                            onClick = { onRegionConfirmed(region) },
                            label = { Text(region.shortName) },
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        OutlinedTextField(
            value = state.searchKeyword,
            onValueChange = viewModel::updateSearchKeyword,
            label = { Text(stringResource(R.string.region_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (val result = state.searchResult) {
            null -> Unit
            is UiState.Loading -> SkeletonRows()
            is UiState.Empty -> EmptyMessage(stringResource(R.string.state_empty_regions))
            is UiState.Failed -> FailedMessage(onRetry = { viewModel.updateSearchKeyword(state.searchKeyword) })
            is UiState.Success -> Column(modifier = Modifier.heightIn(max = 260.dp)) {
                result.data.forEach { region ->
                    DropdownMenuItem(
                        text = { Text(region.fullName) },
                        onClick = { viewModel.selectFromSearch(region) },
                    )
                }
            }
        }

        RegionDropdown(
            label = stringResource(R.string.region_level_sido),
            state = state.sido,
            selected = state.selectedSido,
            enabled = true,
            onSelect = viewModel::selectSido,
            onRetry = viewModel::loadSido,
        )
        RegionDropdown(
            label = stringResource(R.string.region_level_sigungu),
            state = state.sigungu,
            selected = state.selectedSigungu,
            enabled = state.sigunguEnabled,
            onSelect = viewModel::selectSigungu,
            onRetry = { state.selectedSido?.let(viewModel::selectSido) },
        )
        RegionDropdown(
            label = stringResource(R.string.region_level_eupmyeondong),
            state = state.dong,
            selected = state.selectedDong,
            enabled = state.dongEnabled,
            onSelect = viewModel::selectDong,
            onRetry = { state.selectedSigungu?.let(viewModel::selectSigungu) },
        )

        Button(
            onClick = {
                val dong = state.selectedDong ?: return@Button
                viewModel.confirmSelection()
                onRegionConfirmed(dong)
            },
            enabled = state.confirmable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.region_confirm))
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            )
            if (state is UiState.Success) {
                ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
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
