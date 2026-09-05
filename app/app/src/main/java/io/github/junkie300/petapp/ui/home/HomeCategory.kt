package io.github.junkie300.petapp.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.common.icon
import io.github.junkie300.petapp.ui.common.labelRes
import io.github.junkie300.petapp.ui.common.tint
import io.github.junkie300.petapp.ui.theme.BrandOrange
import io.github.junkie300.petapp.ui.theme.BrandOrangeDark

/**
 * 홈 그리드 6칸 (spec.md §5.1 · D-39).
 *
 * places 의 5개 카테고리 + 입양. 입양만 다른 테이블(animals)에서 오므로 [place] 가 null 이고,
 * 라벨·아이콘·색도 자기 것을 쓴다. 나머지 다섯은 `ui/common/CategoryUi.kt` 를 그대로 읽는다 —
 * 목록·상세와 표기가 어긋나지 않게 하기 위해서다.
 *
 * 동물병원을 맨 앞에 둔다. 지금 실제로 데이터가 있는 유일한 칸이다.
 */
enum class HomeCategory(val place: PlaceCategory?) {
    HOSPITAL(PlaceCategory.HOSPITAL),
    GROOMING(PlaceCategory.GROOMING),
    RESTAURANT(PlaceCategory.RESTAURANT),
    TOUR(PlaceCategory.TOUR),
    WILDLIFE(PlaceCategory.WILDLIFE_CENTER),

    // 입양은 animals 테이블이고 6단계다. 브랜드 오렌지를 쓴다 — §6.2 의 카테고리 5색은
    // places 전용이고, 입양은 그 목록에 없다.
    ADOPTION(null),
    ;

    @get:StringRes
    val labelRes: Int get() = place?.labelRes ?: R.string.category_adoption

    val icon: ImageVector get() = place?.icon ?: Icons.Outlined.Pets

    /** 건수를 물어봐도 되는 칸인가. 아니면 "준비 중"을 보여준다 (D-53). */
    val loaded: Boolean get() = place?.loaded == true

    @Composable
    @ReadOnlyComposable
    fun tint(): Color =
        place?.tint() ?: if (isSystemInDarkTheme()) BrandOrangeDark else BrandOrange

    companion object {
        fun of(place: PlaceCategory): HomeCategory = entries.first { it.place == place }
    }
}
