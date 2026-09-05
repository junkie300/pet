package io.github.junkie300.petapp.ui.theme

import androidx.compose.ui.graphics.Color

// spec.md §6.2 — "차분한 안내자". 채도를 낮게 두고, 강한 색은 CTA 와 응급에만 쓴다.
val BrandGreen = Color(0xFF2E6B4F)          // Primary
val BrandGreenContainer = Color(0xFFB8E0C8) // 선택된 필터 칩
val BrandOrange = Color(0xFFE8734A)         // Secondary — 강조·즐겨찾기
val EmergencyRed = Color(0xFFD64545)        // Error — 응급·오류 전용. 다른 용도 금지
// 시안(이태우_디자인시안)의 바탕은 순백이 아니라 **웜 크림**이고, 그 위에 흰 카드가 뜬다.
// 이 대비가 시안 톤의 핵심이라 배경과 카드를 다른 값으로 둔다 (D-47).
val SurfaceLight = Color(0xFFFAF7F0)        // 앱 배경 — 웜 크림
val CardLight = Color(0xFFFFFFFF)           // 카드 — 배경 위에 떠 보이게
val BrandGreenSoft = Color(0xFFE8F3EC)      // 아이콘 원형 배경 · 선택된 칩
val BrandOrangeSoft = Color(0xFFFDEDE5)     // 주황 계열 배지 배경

val SurfaceDark = Color(0xFF14181A)
val CardDark = Color(0xFF1E2224)
val BrandGreenSoftDark = Color(0xFF1F5136)
val BrandOrangeSoftDark = Color(0xFF4A2418)

// Material 3 은 surface 하나만 지정하면 메뉴·카드가 쓰는 surfaceContainer 계열을
// **기본 보라 팔레트**로 남겨 둔다. 그래서 웜 뉴트럴 램프를 직접 깔아 준다 (D-45).
val NeutralLowest = Color(0xFFFFFFFF)
val NeutralLow = Color(0xFFF7F5F1)
val Neutral = Color(0xFFF2F0EB)
val NeutralHigh = Color(0xFFECEAE5)
val NeutralHighest = Color(0xFFE6E4DF)
val SurfaceVariantLight = Color(0xFFE3E1DA)
val OutlineLight = Color(0xFF7A776F)
val OutlineVariantLight = Color(0xFFCBC8C0)

val NeutralLowestDark = Color(0xFF0E1113)
val NeutralLowDark = Color(0xFF1A1E20)
val NeutralDark = Color(0xFF1E2224)
val NeutralHighDark = Color(0xFF282C2E)
val NeutralHighestDark = Color(0xFF333739)
val SurfaceVariantDark = Color(0xFF3F4441)
val OutlineDark = Color(0xFF8A8D88)
val OutlineVariantDark = Color(0xFF43484A)

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
