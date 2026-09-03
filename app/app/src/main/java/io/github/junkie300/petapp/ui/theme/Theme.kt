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
    primaryContainer = BrandGreenContainer,
    onPrimaryContainer = Color(0xFF0B2418),
    secondary = BrandOrange,
    onSecondary = Color.White,
    error = EmergencyRed,
    onError = Color.White,
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1C1B),
    onSurfaceVariant = Color(0xFF5A5751), // 웜 그레이 — 보조 텍스트·기준일 표기
    background = SurfaceLight,
    onBackground = Color(0xFF1A1C1B),
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
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E3E0),
    onSurfaceVariant = Color(0xFFBFBDB6),
    background = SurfaceDark,
    onBackground = Color(0xFFE2E3E0),
)

/**
 * Android 12+ 에서는 시스템 다이내믹 컬러를 따르고, 그 아래에서는 브랜드 팔레트로 떨어진다
 * (spec.md §6.2). 다크 모드는 필수 요구사항이다.
 */
@Composable
fun PetAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = PetTypography, content = content)
}
