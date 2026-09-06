package io.github.junkie300.petapp.ui.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.icon
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.common.tint
import io.github.junkie300.petapp.ui.theme.PillShape

/**
 * 지도 상단의 카테고리 필터 칩 — **복수 선택** (spec.md §5.2).
 *
 * 다섯 칸을 늘 보여준다. 적재되지 않은 카테고리를 아예 숨기면 사용자는 그 기능이 없다고 읽지만,
 * 사실은 **아직 안 실은 것**이다 — 홈 그리드가 "준비 중"으로 적는 것과 같은 구분이다 (D-53).
 *
 * 색만으로 구분하지 않는다: 칩마다 아이콘과 라벨이 함께 있다 (spec.md §6.5).
 * 지도 위에 얹히므로 **바탕이 불투명해야 한다** — 기본 FilterChip 은 투명이라 지도가 비친다.
 */
@Composable
fun CategoryFilterChips(
    selected: Set<PlaceCategory>,
    onToggle: (PlaceCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceCategory.entries.forEach { category ->
            val isSelected = category in selected
            FilterChip(
                selected = isSelected,
                enabled = category.loaded,
                onClick = { onToggle(category) },
                shape = PillShape,
                label = {
                    Text(
                        text = if (category.loaded) {
                            stringResource(category.labelRes)
                        } else {
                            stringResource(R.string.map_chip_pending, stringResource(category.labelRes))
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null, // 바로 옆 라벨이 같은 내용을 읽어 준다
                        // 준비 중인 칩은 아이콘까지 흐리게 둔다. 라벨만 흐리고 아이콘이 또렷하면
                        // 눌리는 칩처럼 보인다.
                        tint = when {
                            !category.loaded -> MaterialTheme.colorScheme.onSurfaceVariant
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> category.tint()
                        },
                        modifier = Modifier.size(16.dp),
                    )
                },
                elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    // ⚠️ 꺼진 칩의 기본 바탕은 **투명**이다. 지도 위에서는 글자 뒤로 도로와 건물이
                    // 그대로 비쳐 읽을 수가 없다 (실측). 준비 중인 칩도 읽히기는 해야 한다.
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
