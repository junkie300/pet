package io.github.junkie300.petapp.ui.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.data.formatDistance
import io.github.junkie300.petapp.ui.common.CategoryBadge
import io.github.junkie300.petapp.ui.theme.CardShape
import io.github.junkie300.petapp.ui.theme.tabularFigures
import io.github.junkie300.petapp.ui.theme.Spacing

/**
 * 장소 카드 (spec.md §6.4) — 이름 · 카테고리 배지 · 주소 1줄 · 전화.
 *
 * 목록과 즐겨찾기가 같은 카드를 쓴다. 한쪽은 서버에서, 한쪽은 로컬 DB 에서 오지만
 * **사용자에게는 같은 것**이므로 다르게 생기면 안 된다. 그래서 원시값을 받는다.
 *
 * [distanceMeters] 는 **있으면 적고 없으면 뺀다.** 위치 권한을 안 줬거나 못 잡은 것이며,
 * 그때 카드가 달라 보이면 안 된다 (D-81).
 */
@Composable
fun PlaceCard(
    name: String,
    address: String?,
    category: PlaceCategory?,
    tel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 현재 위치에서의 **직선** 거리(m). null 이면 거리를 적지 않는다. */
    distanceMeters: Double? = null,
) {
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
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!address.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 거리는 주소 뒤에 붙는다. 이름 옆에 두면 이름이 그만큼 짧게 잘린다.
                    distanceMeters?.let { meters ->
                        Text(
                            text = formatDistance(meters),
                            style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                category?.let { CategoryBadge(it) }
                if (!tel.isNullOrBlank()) {
                    Text(
                        text = tel,
                        // 전화번호는 자리 폭을 맞춘다 — 카드가 세로로 늘어서기 때문이다 (spec.md §6.3)
                        style = MaterialTheme.typography.bodyMedium.tabularFigures(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
