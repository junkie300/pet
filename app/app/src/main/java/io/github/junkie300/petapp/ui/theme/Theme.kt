package io.github.junkie300.petapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = Color(0xFF0B2418),
    secondary = BrandOrange,
    onSecondary = Color.White,
    error = EmergencyRed,
    onError = Color.White,
    tertiary = CategoryColor.Tour,
    onTertiary = Color.White,
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1C1B),
    onSurfaceVariant = Color(0xFF5A5751), // 웜 그레이 — 보조 텍스트·기준일 표기
    background = SurfaceLight,
    onBackground = Color(0xFF1A1C1B),
    // 아래를 비워 두면 메뉴·카드가 M3 기본 보라로 나온다 (D-45).
    surfaceVariant = SurfaceVariantLight,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = NeutralHigh,
    surfaceContainerHighest = NeutralHighest,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = Color(0xFF2F312F),
    inverseOnSurface = Color(0xFFF1EFEA),
    inversePrimary = BrandGreenDark,
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF1F5136),
    onPrimaryContainer = BrandGreenContainer,
    secondary = BrandOrangeDark,
    onSecondary = Color(0xFF5A1A05),
    error = EmergencyRedDark,
    onError = Color(0xFF690005),
    tertiary = Color(0xFF9CCBEA),
    onTertiary = Color(0xFF00344F),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E3E0),
    onSurfaceVariant = Color(0xFFBFBDB6),
    background = SurfaceDark,
    onBackground = Color(0xFFE2E3E0),
    surfaceVariant = SurfaceVariantDark,
    surfaceContainerLowest = CardDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = NeutralHighDark,
    surfaceContainerHighest = NeutralHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Color(0xFFE2E3E0),
    inverseOnSurface = Color(0xFF2F312F),
    inversePrimary = BrandGreen,
)

/**
 * 기본은 **브랜드 팔레트**다 (spec.md §6.2 · D-44). 다크 모드는 필수 요구사항이다.
 *
 * 다이내믹 컬러는 끈다. 켜면 Android 12+ 에서 배경화면 색이 이기고
 * **브랜드 딥그린을 아무도 못 본다.** 게다가 지도 핀 색(`CategoryColor`)은 하드코딩이라
 * 주변 UI 만 배경화면을 따라가면 핀과 충돌한다 — 보라 배경화면 + 초록·주황 핀.
 * 켜고 싶으면 `dynamicColor = true` 를 넘기면 되지만, 그 전에 D-44 를 읽을 것.
 */
@Composable
fun PetAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PetTypography,
        shapes = PetShapes,
        content = content,
    )
}
