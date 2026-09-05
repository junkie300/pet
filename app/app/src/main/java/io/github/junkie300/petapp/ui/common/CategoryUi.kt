package io.github.junkie300.petapp.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.theme.CategoryColor
import io.github.junkie300.petapp.ui.theme.PillShape

/**
 * 카테고리를 화면에 어떻게 그리는지 — **여기 한 곳에만 둔다.**
 *
 * 홈 그리드·목록 배지·상세 머리말이 모두 이걸 읽는다. 표가 두 벌이 되면 카테고리를 늘릴 때
 * 한쪽만 고치게 되고, 그게 `plan.md` 2단계 완료 기준("필터 칩 1줄 추가")을 깨뜨린다.
 *
 * 색만으로 구분하지 않는다 — 아이콘·라벨을 함께 쓴다 (spec.md §6.5).
 */
@get:StringRes
val PlaceCategory.labelRes: Int
    get() = when (this) {
        PlaceCategory.HOSPITAL -> R.string.category_hospital
        PlaceCategory.GROOMING -> R.string.category_grooming
        PlaceCategory.RESTAURANT -> R.string.category_restaurant
        PlaceCategory.TOUR -> R.string.category_tour
        PlaceCategory.WILDLIFE_CENTER -> R.string.category_wildlife
    }

val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.HOSPITAL -> Icons.Outlined.MedicalServices
        PlaceCategory.GROOMING -> Icons.Outlined.ContentCut
        PlaceCategory.RESTAURANT -> Icons.Outlined.Restaurant
        PlaceCategory.TOUR -> Icons.Outlined.Landscape
        PlaceCategory.WILDLIFE_CENTER -> Icons.Outlined.Eco
    }

/**
 * 어두운 카드 위에서는 밝은 짝을 쓴다 (D-56).
 * `spec.md §6.2` 의 5색은 라이트 배경 기준이라 그대로 올리면 대비 4.5:1 을 못 지킨다.
 */
@Composable
@ReadOnlyComposable
fun PlaceCategory.tint(): Color = if (isSystemInDarkTheme()) {
    when (this) {
        PlaceCategory.HOSPITAL -> CategoryColor.VetDark
        PlaceCategory.GROOMING -> CategoryColor.GroomingDark
        PlaceCategory.RESTAURANT -> CategoryColor.RestaurantDark
        PlaceCategory.TOUR -> CategoryColor.TourDark
        PlaceCategory.WILDLIFE_CENTER -> CategoryColor.WildlifeCenterDark
    }
} else {
    when (this) {
        PlaceCategory.HOSPITAL -> CategoryColor.Vet
        PlaceCategory.GROOMING -> CategoryColor.Grooming
        PlaceCategory.RESTAURANT -> CategoryColor.Restaurant
        PlaceCategory.TOUR -> CategoryColor.Tour
        PlaceCategory.WILDLIFE_CENTER -> CategoryColor.WildlifeCenter
    }
}

/** 목록·상세에 붙는 카테고리 배지. 라벨이 함께 붙으므로 5색을 그대로 쓴다 (D-39). */
@Composable
fun CategoryBadge(category: PlaceCategory, modifier: Modifier = Modifier) {
    val tint = category.tint()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null, // 바로 옆 라벨이 같은 내용을 읽어 준다
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(category.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}
