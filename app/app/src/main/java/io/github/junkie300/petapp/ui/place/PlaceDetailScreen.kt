package io.github.junkie300.petapp.ui.place

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.Place
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.CategoryBadge
import io.github.junkie300.petapp.ui.common.DetailScaffold
import io.github.junkie300.petapp.ui.common.EmptyMessage
import io.github.junkie300.petapp.ui.common.FailedMessage
import io.github.junkie300.petapp.ui.common.OfflineBanner
import io.github.junkie300.petapp.ui.common.SkeletonRows
import io.github.junkie300.petapp.ui.common.UiState
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.theme.CardShape
import io.github.junkie300.petapp.ui.theme.PillShape
import io.github.junkie300.petapp.ui.theme.Spacing
import io.github.junkie300.petapp.ui.theme.tabularFigures

/**
 * S-03 장소 상세 (spec.md §5.2).
 *
 * ⚠️ **별점·후기·장소 사진은 넣지 않는다** (D-40) — 공공데이터에 없는 필드다.
 * 시안이 별점을 놓았던 자리에는 **출처와 기준일**을 놓는다. 여행 준비 도구에서는
 * "이 정보가 언제 것인가"가 별점보다 신뢰에 직접 기여한다.
 */
@Composable
fun PlaceDetailScreen(
    viewModel: PlaceDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val place = (state as? UiState.Success)?.data?.place

    DetailScaffold(
        // 상단바에는 카테고리만. 장소 이름은 본문 머리말이 맡는다 — 둘 다 쓰면 같은 말이 두 번 나온다.
        title = place?.placeCategory?.let { stringResource(it.labelRes) }
            ?: stringResource(R.string.place_detail_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            // 장소를 못 불러왔으면 버튼도 없다 — 이름 없는 즐겨찾기를 만들지 않기 위해서다.
            if (place != null) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.cd_favorite_remove else R.string.cd_favorite_add,
                        ),
                        // 즐겨찾기는 Secondary(웜 오렌지) 자리다 (spec.md §6.2).
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
    ) { insets ->
        val bodyModifier = Modifier
            .padding(insets)
            .padding(horizontal = Spacing.screenHorizontal)

        when (val current = state) {
            is UiState.Loading -> SkeletonRows(count = 4, modifier = bodyModifier)
            // 폐업으로 빠졌거나 옛 id 다. 불러오기 실패와 다른 말이어야 한다.
            is UiState.Empty -> EmptyMessage(stringResource(R.string.place_detail_gone), bodyModifier)
            is UiState.Failed -> FailedMessage(onRetry = viewModel::load, modifier = bodyModifier)
            is UiState.Success -> DetailBody(detail = current.data, modifier = bodyModifier)
        }
    }
}

@Composable
private fun DetailBody(detail: PlaceDetail, modifier: Modifier = Modifier) {
    val place = detail.place
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        // 사본으로 그린 상세다. 아래 출처·기준일은 **원본 기준일**이고 이건 **받아 둔 시각**이라,
        // 둘은 다른 값이며 둘 다 있어야 "언제 것인지"가 온전해진다.
        detail.cachedAt?.let { OfflineBanner(cachedAt = it) }

        place.placeCategory?.let { CategoryBadge(it) }

        Text(
            text = place.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        place.address?.let { address ->
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ActionRow(place)

        InfoCard(place)

        // 식당은 고정 안내문이 붙는다 (spec.md §5.2). 3단계에 데이터가 들어오면 바로 보인다.
        if (place.placeCategory == PlaceCategory.RESTAURANT) {
            Notice(stringResource(R.string.place_detail_restaurant_notice))
        }

        SourceFooter(place)
    }
}

/** 길찾기 · 전화 · 공유. 셋 다 바깥 앱으로 넘긴다 — 지도 키가 없어도 지금 된다 (D-57). */
@Composable
private fun ActionRow(place: Place, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareText = stringResource(
        R.string.place_detail_share,
        place.name,
        place.address ?: "",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 받아 줄 앱이 없으면 아무 일도 안 일어난다. 그러면 사용자는 앱이 고장난 줄 안다.
        val noApp = stringResource(R.string.place_action_no_app)
        fun run(action: () -> Boolean) {
            if (!action()) Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
        }

        if (place.hasCoordinates) {
            ActionButton(
                icon = Icons.Outlined.Directions,
                label = stringResource(R.string.place_action_route),
                modifier = Modifier.weight(1f),
            ) { run { PlaceActions.route(context, place) } }
        }
        place.tel?.let { tel ->
            ActionButton(
                icon = Icons.Outlined.Call,
                label = stringResource(R.string.place_action_call),
                modifier = Modifier.weight(1f),
            ) { run { PlaceActions.dial(context, tel) } }
        }
        ActionButton(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.place_action_share),
            modifier = Modifier.weight(1f),
        ) { run { PlaceActions.share(context, place, shareText.trim()) } }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = PillShape,
        modifier = modifier.height(48.dp), // 최소 터치 타겟 (spec.md §6.5)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun InfoCard(place: Place, modifier: Modifier = Modifier) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRow(stringResource(R.string.place_field_address_road), place.addressRoad)
            InfoRow(stringResource(R.string.place_field_address_jibun), place.addressJibun)
            InfoRow(stringResource(R.string.place_field_tel), place.tel, tabular = true)
            // 원본이 쓰는 문구를 그대로 옮긴다. 우리가 "영업중"으로 고쳐 부르지 않는다.
            InfoRow(stringResource(R.string.place_field_status), place.salesStatus)
        }
    }
}

/** 값이 없는 줄은 아예 그리지 않는다. 빈 칸을 보여 주면 "정보가 있는데 못 불러왔나" 로 읽힌다. */
@Composable
private fun InfoRow(label: String, value: String?, tabular: Boolean = false) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            // 전화번호만 자리 폭을 맞춘다. 주소에 걸면 번지수 숫자만 벌어져 보인다 (spec.md §6.3)
            style = MaterialTheme.typography.bodyLarge.let { if (tabular) it.tabularFigures() else it },
        )
    }
}

@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(Spacing.cardPadding),
        )
    }
}

/**
 * **출처와 기준일은 의무 표기다** (spec.md §5.2 · §8, 개발계획서 §8.1).
 * 기준일이 없으면 없다고 적는다 — 지어내지 않는다.
 */
@Composable
private fun SourceFooter(place: Place, modifier: Modifier = Modifier) {
    val source = stringResource(sourceLabelRes(place.source))
    val date = place.sourceUpdatedDate
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (date != null) {
                stringResource(R.string.place_source_line, source, date)
            } else {
                stringResource(R.string.place_source_line_no_date, source)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
