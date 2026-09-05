package io.github.junkie300.petapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 시안(이태우_디자인시안)의 형태 언어 — spec.md §6.4 · D-47.
 *
 * 시안은 M3 기본값보다 **눈에 띄게 더 둥글다.** 카드 20dp, 검색창·버튼은 알약 모양이다.
 * 이 곡률이 시안 톤의 절반을 만든다. 값을 바꾸면 화면 인상이 통째로 바뀌므로 여기서만 고친다.
 */
val PetShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),   // 카드
    extraLarge = RoundedCornerShape(28.dp),
)

/** 알약 모양 — 검색창·주요 버튼·칩. 시안에서 가장 자주 보이는 형태다. */
val PillShape = RoundedCornerShape(percent = 50)

/** 카드 곡률. `PetShapes.large` 와 같은 값이지만 카드에 쓸 때 의도를 드러낸다. */
val CardShape = RoundedCornerShape(20.dp)

object Spacing {
    val screenHorizontal = 20.dp
    val sectionGap = 24.dp
    val cardPadding = 20.dp
    val itemGap = 12.dp
}
