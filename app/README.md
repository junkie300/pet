# app — 안드로이드 앱

Kotlin + Jetpack Compose. 이 디렉터리가 Gradle 루트이고, 실제 모듈은 `app/app` 하나다.

## 열기

Android Studio 에서 **`D:\pet\app`** 을 연다 (`D:\pet` 이 아니다).

## 접속 정보

`local.properties` 는 커밋되지 않는다. `local.properties.example` 을 복사해서 채운다.

| 키 | 설명 |
|---|---|
| `sdk.dir` | Android SDK 경로. Studio 로 열면 자동으로 채워진다 |
| `SUPABASE_URL` | 프로젝트 URL |
| `SUPABASE_ANON_KEY` | ⚠️ **`anon` (public) 키.** `service_role` 을 넣으면 APK 에 그대로 노출된다 |

키가 비어 있어도 **앱은 켜진다.** 데이터 대신 "접속 정보가 설정되지 않았습니다" 안내가 뜬다.
빌드가 깨지는 것과 키가 없는 것을 구분하기 위해서다.

## 명령

```
gradlew testDebugUnitTest     # 단위 테스트 (기기 불필요)
gradlew assembleDebug         # debug APK
gradlew installDebug          # 연결된 기기/에뮬레이터에 설치
gradlew assembleRelease       # R8 적용 release APK (서명 없음)
```

## 지금 있는 것

| 화면 | 상태 |
|---|---|
| S-01 지역 선택 | 3단 드롭다운 · 지역명 검색 · 최근 지역 3개 |
| S-02 지도 | 없음 — 카카오 **네이티브 앱 키** 대기 |
| S-03 장소 상세 | 없음 — `places` 적재 후 |

## 구조

```
app/src/main/java/io/github/junkie300/petapp/
  PetApplication.kt      Application. AppContainer 를 만든다
  AppContainer.kt        수동 DI. Hilt 를 쓰지 않는 이유는 spec.md §1.1
  MainActivity.kt        Compose 진입점
  data/
    Region.kt              regions 행 (앱이 쓰는 컬럼만)
    RegionRepository.kt    S-01 이 쓰는 유일한 데이터 출입구
    RecentRegionStore.kt   최근 지역 3개 (DataStore)
    SupabaseProvider.kt    클라이언트 하나를 공유
  ui/
    theme/                 spec.md §6.2 컬러 · 다크 모드
    common/UiState.kt      로딩 / 없음 / 실패
    region/                S-01 화면 + ViewModel
```

## 알아둘 것

- **버전은 `gradle/libs.versions.toml` 한 곳에서만 바꾼다.**
  AGP 8.13.2 · compileSdk 36 에 맞춰 고정돼 있다. 이유와 올리는 방법은 `DECISIONS.md` **D-36**.
- **`applicationId` 는 임시값이다.** 스토어 첫 업로드 뒤에는 영구히 못 바꾼다 — `DECISIONS.md` **D-35**.
- **로딩 · 데이터 없음 · 불러오기 실패를 한 상태로 합치지 말 것.** `ui/common/UiState.kt` 주석 참고.
- 앱 아이콘과 서체(Pretendard)는 아직 임시다. 화면이 확정된 뒤 교체한다.
