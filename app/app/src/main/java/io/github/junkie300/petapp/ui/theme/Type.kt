package io.github.junkie300.petapp.ui.theme

import androidx.compose.material3.Typography

// spec.md §6.3 — Material 3 타입 스케일을 그대로 따른다.
// TODO(1단계 후반): Pretendard 를 res/font 에 번들하고 FontFamily 를 지정한다.
//   지금 기본 서체로 두는 이유는, 폰트 파일이 APK 크기(30MB 이하) 예산에 영향을 주므로
//   화면이 확정된 뒤 서브셋을 만들어 넣기 위함이다.
val PetTypography = Typography()
