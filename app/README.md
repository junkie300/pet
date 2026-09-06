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
gradlew testDebugUnitTest     # 단위 테스트 33개 (기기·네트워크 불필요)
gradlew assembleDebug         # debug APK
gradlew installDebug          # 연결된 기기/에뮬레이터에 설치
gradlew assembleRelease       # R8 적용 release APK (서명 없음, 5.79MB)
```

에뮬레이터로 확인할 때:

```
%LOCALAPPDATA%\Android\Sdk\emulator\emulator -avd <이름> -no-snapshot-load
gradlew installDebug
adb shell am start -n io.github.junkie300.petapp/.MainActivity
adb exec-out screencap -p > shot.png     # 색·형태는 화면으로만 검증된다 (D-45)
```

## 지금 있는 것

| 화면 | 상태 |
|---|---|
| S-00 홈 허브 | 지역 칩(읍면동까지) · 카테고리 6칸 · **실제 건수** (D-39·D-53) |
| 하단 탭 4개 | 홈 · 지도 탐색 · 구조·입양 · 더보기. 뒤 둘은 안내 화면 (D-39) |
| S-01 지역 선택 | 3단 드롭다운 · 지역명 검색 · 최근 지역 3개. **홈의 지역 칩에서 연다** |
| 장소 목록 | 읍면동 + 카테고리로 조회. 홈 타일에서 들어간다 |
| S-03 장소 상세 | 주소·전화·영업상태 + **출처·기준일**. 길찾기·전화·공유 (D-57) |
| S-07 즐겨찾기 | Room. **오프라인에서도 뜬다** (D-60). 상세까지 열린다 (D-64) |
| 오프라인 | 지역·장소·건수 사본 + 상단 배너. 홈·목록·상세·S-01 전부 (D-62~D-66) |
| 더보기 | 즐겨찾기 · 데이터 출처 · 앱 정보 |
| S-02 지도 | 없음 — 카카오 **네이티브 앱 키** 대기. **여기만 남았다** |

## 구조

```
app/src/main/java/io/github/junkie300/petapp/
  PetApplication.kt      Application. AppContainer 를 만든다
  AppContainer.kt        수동 DI. Hilt 를 쓰지 않는 이유는 spec.md §1.1
  MainActivity.kt        Compose 진입점. 화면 구성은 전부 ui/nav/PetApp.kt 안에 있다
  data/
    Region.kt              regions 행 (앱이 쓰는 컬럼만)
    RegionRepository.kt    S-01 이 쓰는 유일한 데이터 출입구
    RecentRegionStore.kt   최근 지역 3개 (DataStore). **맨 앞이 현재 지역이다** (D-54)
    Place.kt               places 행. 기준일은 날짜만 떼어 쓴다 (D-58)
    PlaceCategory.kt       카테고리 5종 + 적재 여부(loaded) — D-53
    PlaceRepository.kt     건수 · 목록 · 상세
    SupabaseProvider.kt    클라이언트 하나를 공유
    Fetched.kt             값 + **어디서 왔는지**(cachedAt). 배너가 이걸 본다
    OfflineCache.kt        서버 → 사본 남기기 → 실패하면 사본 꺼내기. **규칙 한 곳** (D-62)
    cache/                 CachedRegion · CachedPlace · CachedCount · CacheDao
    local/PetDatabase.kt   Room DB(버전 2). **마이그레이션 SQL 은 여기** (D-65)
    favorite/              Room — FavoritePlace · FavoriteDao. 스냅샷에 출처·기준일까지 (D-64)
  ui/
    nav/PetApp.kt          하단 탭 + NavHost. **탭 전환은 switchTab() 하나로** (D-55)
    theme/                 spec.md §6.2 컬러 · 다크 모드 (카테고리 색은 다크 짝이 있다 — D-56)
    theme/Type.kt          Pretendard 가변 폰트 · tabularFigures() — D-67·D-68
    common/UiState.kt      로딩 / 없음 / 실패
    common/CategoryUi.kt   카테고리 라벨·아이콘·색. **세 화면이 이 표 하나를 읽는다**
    common/OfflineBanner   "언제 받아 둔 정보인지" 한 줄. 오류색을 쓰지 않는다 (D-66)
    common/DetailScaffold  탭이 아닌 화면(목록·상세·즐겨찾기)의 공통 뼈대
    home/                  S-00 홈
    region/                S-01 지역 선택
    place/                 목록 · 상세 · 공용 카드 · 바깥 앱 연동
    favorite/              S-07 즐겨찾기
    more/                  더보기
```

## 알아둘 것

- **버전은 `gradle/libs.versions.toml` 한 곳에서만 바꾼다.**
  AGP 8.13.2 · compileSdk 36 에 맞춰 고정돼 있다. 이유와 올리는 방법은 `DECISIONS.md` **D-36**.
- ⚠️ **KSP 는 코틀린과 분리된 버전 라인(`2.3.11`)을 쓴다.** Kotlin 2.3.21 에는 짝이 맞는
  `<코틀린>-<KSP>` 형식(`2.2.21-2.0.5`)이 **없다.** 올릴 때 이 라인인지 먼저 확인한다 — **D-61**.
- **`applicationId` 는 임시값이다.** 스토어 첫 업로드 뒤에는 영구히 못 바꾼다 — `DECISIONS.md` **D-35**.
- **로딩 · 데이터 없음 · 불러오기 실패를 한 상태로 합치지 말 것.** `ui/common/UiState.kt` 주석 참고.
  같은 이유로 **"이 동네에 없다(0곳)"와 "우리가 아직 안 실었다(준비 중)"도 갈라 둔다** — D-53.
- **탭인 화면을 다른 탭 위에 push 하지 말 것.** 홈 탭을 눌렀을 때 그 화면이 되살아난다 — D-55.
- **Room 스키마(`app/schemas/`)를 지우지 말 것.** 마이그레이션을 쓰려면 이전 스키마가 필요하다.
- ⚠️ **DB 버전을 올렸으면 `MigrationSqlTest` 를 반드시 통과시킬 것.** 손으로 쓴 CREATE 문이
  Room 이 만드는 것과 한 글자만 달라도 **버전 1 을 깔아 둔 기기에서만** 죽는다. 새로 깐 기기에서는
  절대 안 드러난다 — **D-65**. 즐겨찾기는 `fallbackToDestructiveMigration()` 으로 날리지 않는다.
- **빈 사본은 "없다"가 아니라 캐시 미스다.** 오프라인에서 빈 목록을 성공으로 돌려주면 화면이
  "이 지역에는 없습니다"를 그리는데, 우리는 모르는 것이다 — **D-62**.
- **서체는 가변 폰트 파일 하나(`res/font/pretendard_variable.ttf`)로 굵기를 전부 낸다** — D-67.
  ⚠️ **서브셋을 만들지 말 것.** 장소명은 공공데이터에서 오므로 어떤 음절이 나올지 모르고,
  한 글자만 빠져도 병원 이름에 두부(□)가 뜬다 — D-68. 서체를 바꾸면 `.gitattributes` 의
  `*.ttf binary` 도 확인한다.
- **앱 아이콘은 아직 임시다.** 남은 임시값은 이제 이것 하나다.
