package io.github.junkie300.petapp.ui.theme

import androidx.compose.ui.graphics.Color

// spec.md §6.2 — "차분한 안내자". 채도를 낮게 두고, 강한 색은 CTA 와 응급에만 쓴다.
val BrandGreen = Color(0xFF2E6B4F)          // Primary
val BrandGreenContainer = Color(0xFFB8E0C8) // 선택된 필터 칩
val BrandOrange = Color(0xFFE8734A)         // Secondary — 강조·즐겨찾기
val EmergencyRed = Color(0xFFD64545)        // Error — 응급·오류 전용. 다른 용도 금지
val SurfaceLight = Color(0xFFFBFAF8)
val SurfaceDark = Color(0xFF14181A)

// 다크에서는 같은 색상을 그대로 쓰면 대비가 무너진다. 밝기를 올린 짝을 따로 둔다.
val BrandGreenDark = Color(0xFF8FD3AC)
val BrandOrangeDark = Color(0xFFFFB59B)
val EmergencyRedDark = Color(0xFFFFB4AB)

/** 카테고리 색. 색만으로 구분하지 않는다 — 아이콘·라벨과 함께 쓴다 (spec §6.5). */
object CategoryColor {
    val Vet = Color(0xFF2E6B4F)
    val Grooming = Color(0xFF7B5EA7)
    val Restaurant = Color(0xFFE8734A)
    val Tour = Color(0xFF3A7CA5)
    val WildlifeCenter = Color(0xFF6B7A3F)
}
