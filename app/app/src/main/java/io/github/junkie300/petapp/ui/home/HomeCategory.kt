package io.github.junkie300.petapp.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.junkie300.petapp.R
import io.github.junkie300.petapp.data.PlaceCategory
import io.github.junkie300.petapp.ui.theme.BrandOrange
import io.github.junkie300.petapp.ui.theme.BrandOrangeDark
import io.github.junkie300.petapp.ui.theme.CategoryColor

/**
 * 홈 그리드 6칸 (spec.md §5.1 · D-39).
 *
 * places 의 5개 카테고리 + 입양. 입양만 다른 테이블(animals)에서 오므로 [place] 가 null 이다.
 * 색과 아이콘은 spec.md §6.2 표를 그대로 옮긴 것이며, **색만으로 구분하지 않는다** (§6.5) —
 * 6칸 모두 아이콘과 라벨이 함께 붙는다.
 *
 * 동물병원을 맨 앞에 둔다. 지금 실제로 데이터가 있는 유일한 칸이다.
 */
enum class HomeCategory(
    val place: PlaceCategory?,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    private val lightTint: Color,
    private val darkTint: Color,
) {
    HOSPITAL(
        PlaceCategory.HOSPITAL, R.string.category_hospital, Icons.Outlined.MedicalServices,
        CategoryColor.Vet, CategoryColor.VetDark,
    ),
    GROOMING(
        PlaceCategory.GROOMING, R.string.category_grooming, Icons.Outlined.ContentCut,
        CategoryColor.Grooming, CategoryColor.GroomingDark,
    ),
    RESTAURANT(
        PlaceCategory.RESTAURANT, R.string.category_restaurant, Icons.Outlined.Restaurant,
        CategoryColor.Restaurant, CategoryColor.RestaurantDark,
    ),
    TOUR(
        PlaceCategory.TOUR, R.string.category_tour, Icons.Outlined.Landscape,
        CategoryColor.Tour, CategoryColor.TourDark,
    ),
    WILDLIFE(
        PlaceCategory.WILDLIFE_CENTER, R.string.category_wildlife, Icons.Outlined.Eco,
        CategoryColor.WildlifeCenter, CategoryColor.WildlifeCenterDark,
    ),

    // 입양은 animals 테이블이고 6단계다. 브랜드 오렌지를 쓴다 — §6.2 의 카테고리 5색은
    // places 전용이고, 입양은 그 목록에 없다.
    ADOPTION(null, R.string.category_adoption, Icons.Outlined.Pets, BrandOrange, BrandOrangeDark),
    ;

    /** 어두운 카드 위에서는 밝은 짝을 쓴다. spec §6.5 의 명도 대비 4.5:1 요구다. */
    @Composable
    @ReadOnlyComposable
    fun tint(): Color = if (isSystemInDarkTheme()) darkTint else lightTint

    /** 건수를 물어봐도 되는 칸인가. 아니면 "준비 중"을 보여준다. */
    val loaded: Boolean get() = place?.loaded == true

    companion object {
        /** 라벨·아이콘·색은 여기 한 곳에만 둔다. 다른 화면도 카테고리 표기는 이걸 통해 얻는다. */
        fun of(place: PlaceCategory): HomeCategory = entries.first { it.place == place }
    }
}
