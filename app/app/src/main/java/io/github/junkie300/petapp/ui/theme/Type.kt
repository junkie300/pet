package io.github.junkie300.petapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.junkie300.petapp.R

/**
 * spec.md §6.3 — 서체는 **Pretendard**, 스케일은 Material 3 타입 스케일 그대로.
 *
 * **가변 폰트(variable font) 파일 하나로 굵기를 전부 낸다.** 굵기마다 static 파일을 넣으면
 * (Regular·Medium·Bold 만 해도 2.6MB × 3 ≈ 7.9MB) 파일 하나(6.6MB)보다 오히려 크고,
 * 나중에 굵기를 하나 더 쓸 때마다 파일이 는다. minSdk 26 이라 가변 폰트가 그대로 동작한다.
 *
 * ⚠️ **서브셋을 만들지 않는다.** APK 예산(30MB)을 아끼려면 쓰는 글자만 남기는 게 정석이지만,
 * 이 앱이 그리는 것은 **공공데이터에서 온 장소명**이라 어떤 음절이 나올지 우리가 모른다.
 * 한 글자라도 빠지면 병원 이름 한가운데에 두부(□)가 뜨고, 그건 데이터가 틀린 것처럼 보인다.
 * 6.6MB 는 예산의 1/5 이고, 지도(S-02)가 붙어도 여유가 남는다.
 *
 * ⚠️ 굵기 축을 넘기는 [Font] 오버로드는 아직 실험 API 다. 여기 네 줄이 유일한 사용처이며,
 * 시그니처가 바뀌면 이 파일만 고치면 된다. XML `<font-family>` 로 우회할 수도 있지만
 * 그러면 굵기 선택이 플랫폼으로 넘어가 Compose 쪽에서 무슨 일이 일어나는지 안 보인다.
 */
@OptIn(ExperimentalTextApi::class)
private val PretendardFamily = FontFamily(
    // 가변 폰트라 파일은 같고 축(weight) 값만 다르다. Compose 가 굵기별로 이 표를 찾아 쓴다.
    Font(R.font.pretendard_variable, FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.pretendard_variable, FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.pretendard_variable, FontWeight.SemiBold, variationSettings = weightAxis(600)),
    Font(R.font.pretendard_variable, FontWeight.Bold, variationSettings = weightAxis(700)),
)

private fun weightAxis(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/**
 * Material 3 기본 스케일에 서체만 갈아 끼운다.
 *
 * 크기·행간·자간은 M3 값을 그대로 둔다. 한글은 라틴보다 세로로 꽉 차 보이지만, 스케일까지
 * 손대기 시작하면 화면마다 기준이 달라진다 — 필요하면 그때 한 곳에서 고친다.
 */
val PetTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PretendardFamily),
        displayMedium = displayMedium.copy(fontFamily = PretendardFamily),
        displaySmall = displaySmall.copy(fontFamily = PretendardFamily),
        headlineLarge = headlineLarge.copy(fontFamily = PretendardFamily),
        headlineMedium = headlineMedium.copy(fontFamily = PretendardFamily),
        headlineSmall = headlineSmall.copy(fontFamily = PretendardFamily),
        titleLarge = titleLarge.copy(fontFamily = PretendardFamily),
        titleMedium = titleMedium.copy(fontFamily = PretendardFamily),
        titleSmall = titleSmall.copy(fontFamily = PretendardFamily),
        bodyLarge = bodyLarge.copy(fontFamily = PretendardFamily),
        bodyMedium = bodyMedium.copy(fontFamily = PretendardFamily),
        bodySmall = bodySmall.copy(fontFamily = PretendardFamily),
        labelLarge = labelLarge.copy(fontFamily = PretendardFamily),
        labelMedium = labelMedium.copy(fontFamily = PretendardFamily),
        labelSmall = labelSmall.copy(fontFamily = PretendardFamily),
    )
}

/**
 * 숫자를 **자리 폭이 같게** 그린다 (spec.md §6.3 — tabular figures).
 *
 * 건수(`8곳`)와 전화번호처럼 세로로 늘어서는 숫자에만 쓴다. 기본(proportional) 숫자는
 * `1` 이 좁아서 목록에서 자릿수가 어긋나 보인다. 본문 전체에 걸면 문장 속 숫자가
 * 부자연스럽게 벌어지므로 **필요한 자리에만** 붙인다.
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
