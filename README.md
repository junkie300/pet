# 미리펫

반려동물과 낯선 지역에 갈 때, 그 지역의 동물병원·미용·동반 식당·관광지를
**읍면동 단위로 한 화면에서 미리 확인하는** 안드로이드 앱.

> 이름이 제품의 한 줄 정의다 — **"미리"** 확인한다 (D-24·D-43).
> `applicationId` 는 이름과 무관한 값으로 남겨 둔다. 이름은 언제든 바꿀 수 있어야 한다 (D-41).

## 문서

| 문서 | 내용 |
|---|---|
| [개발계획서.md](개발계획서.md) | 배경·목표·포지셔닝·범위·리스크·출시 전략 (WHY & WHAT) |
| [spec.md](spec.md) | 기술 스택·아키텍처·데이터 모델·API·화면·디자인 |
| [plan.md](plan.md) | 단계별 작업·완료 기준·체크리스트·일정 (HOW & WHEN) |
| [DECISIONS.md](DECISIONS.md) | 의사결정 이력 (폐기된 대안 포함) |

## 저장소 구성

```
supabase/
  migrations/          DB 스키마 (번호 순으로 실행)
  apply_all.sql        위 7개를 이어붙인 것 — SQL Editor 에 한 번에 붙여넣기용
  verify.sql           스키마·적재·매핑·좌표를 한 번에 점검
etl/                   공공데이터 → Supabase 파이썬 ETL   → etl/README.md
  run.py                 진입점 (status · regions · mapping · coords · localdata · hospitals)
  petetl/status.py       "지금 어디까지 왔나" 요약
  petetl/publicapi.py    공공데이터포털 공통 클라이언트
  petetl/sources/        소스별 ETL
app/                   안드로이드 앱 (Kotlin + Compose)   → app/README.md
  gradle/libs.versions.toml   의존성 버전은 전부 여기서만 바꾼다
  local.properties.example    Supabase 접속 정보 — 복사해서 local.properties 로
  app/src/main/java/.../
    data/                     regions·places 조회 · 최근 지역 저장
    data/PlaceCategory.kt     카테고리 5종 + **적재 여부**(loaded) — D-53
    data/OfflineCache.kt      서버 → 사본 남기기 → 실패하면 사본 꺼내기. **오프라인 규칙 한 곳** — D-62
    data/cache/               오프라인 캐시 표 3개 (지역 · 장소 · 건수) + DAO
    data/local/PetDatabase.kt Room DB. 즐겨찾기 + 캐시. **마이그레이션 SQL 은 여기** — D-65
    data/favorite/            즐겨찾기 Room 테이블 · DAO (스냅샷에 출처·기준일까지 — D-64)
    ui/nav/PetApp.kt          하단 탭 4개 + NavHost. 탭 전환은 여기 switchTab() 하나로 — D-55
    ui/home/                  S-00 홈 허브 (지역 칩 · 카테고리 6칸 · 건수)
    ui/region/                S-01 지역 선택 화면
    ui/place/                 장소 목록 · S-03 상세 (길찾기·전화·공유 — 키 불필요, D-57)
    ui/favorite/              S-07 즐겨찾기 (오프라인에서도 뜬다 — D-60)
    ui/more/                  더보기 (즐겨찾기 · 데이터 출처 · 앱 정보)
    ui/common/UiState.kt      로딩 / 없음 / 실패를 타입으로 구분
    ui/common/CategoryUi.kt   카테고리 라벨·아이콘·색 — 세 화면이 이 표 하나를 읽는다
    ui/common/OfflineBanner   "언제 받아 둔 정보인지" 한 줄 — D-66
    ui/theme/Type.kt          Pretendard 가변 폰트 · tabular figures — D-67·D-68
  app/src/main/res/font/       Pretendard 가변 폰트 (6.6MB). 서브셋을 만들지 않는다 — D-68
  app/src/main/assets/licenses SIL OFL 1.1 전문 (서체와 함께 배포해야 한다)
.github/workflows/     test.yml · etl.yml(mapping·coords 수동 실행 가능)
```

---

## 진행 상황 (2026-09-05)

### 0단계 — 지역코드 정규화 ✅ 완료

- [x] Supabase 프로젝트 생성 (`pet-app`, 리전 Seoul), PostGIS·pg_trgm 활성화
- [x] DB 스키마 전체 적용 — `regions` `places` `shelters` `animals` `sync_logs` + RLS + `places_nearby` RPC
- [x] 법정동코드 → `regions` ETL 작성 및 **적재 완료**
      → **시도 16 · 시군구 256 · 읍면동 5,067**
- [x] 국가동물보호정보시스템 코드 매핑 (`apms_upr_cd`, `apms_org_cd`) — **미매핑 0**
- [x] TourAPI 코드 매핑 (`tour_area_cd`, `tour_sigungu_cd`) — **미매핑 0**
- [x] LOCALDATA 지역코드 매핑 (`localdata_cd`) — **미매핑 0** (인증키 없이 문서만으로 처리)
- [x] 읍면동 중심좌표 (`center_lat`, `center_lng`) — **5,067곳 전량 적재, 실패 0**

### 1단계 — 동물병원 ◐ 지도(S-02)만 남았다

- [x] 안드로이드 프로젝트 생성 — `app/`. **debug·release 빌드와 단위 테스트 22개 통과 확인**
      (release APK 2.67MB — spec 의 30MB 예산 안)
- [x] Supabase Kotlin SDK 연결. anon 키는 `local.properties` 에서 읽어 `BuildConfig` 로 넣는다
- [x] S-01 지역 선택 — 시도→시군구→읍면동 3단 드롭다운 · 지역명 검색 · 최근 지역 3개
- [x] 로딩 / 데이터 없음 / 불러오기 실패를 **타입으로 구분** (`UiState`) · 다크 모드
- [x] Supabase anon 키 투입 — **debug 빌드 성공**. anon 키로 `regions` 질의 4종 + RLS 쓰기 차단까지 실측 확인
- [x] **에뮬레이터에서 실제로 띄워 확인** — 시도 16개(전남광주통합특별시 포함) · 3단 연동 ·
      최근 지역이 앱 재시작 후에도 유지 · 라이트/다크 · 오프라인 실패와 재시도 회복까지
- [x] 시안 기준 디자인 적용 — 웜 크림 배경 + 흰 카드 + 알약 검색창·버튼 (D-47)
- [x] **동물병원 ETL — `places` 10,617건 적재 완료**
      → 영업중 **5,474** · 좌표 5,470(99.9%) · 지역 배정 5,447 · 미배정 27(0.5%)
- [x] **S-00 홈 + 하단 탭 4개** (D-39) — 홈의 카테고리 건수가 **실제 DB 숫자**로 나온다
      → 삼성동 8 · 대치동 7 (PostgREST 직접 조회와 대조 완료)
      → 적재 전 카테고리는 `0곳` 이 아니라 **`준비 중`** (D-53). 0곳으로 적으면 "이 동네엔 없구나"로 읽힌다
      → 에뮬레이터로 라이트/다크 · 탭 왕복 · S-01 왕복 · 오프라인 실패와 재시도 회복까지 확인
- [x] **장소 목록 + S-03 상세** — 홈 타일 → 그 동네 병원 목록 → 상세까지 이어진다
      → 상세에 **출처·기준일**이 붙는다 (별점 자리에 놓기로 한 것 — D-40)
      → **길찾기·전화·공유가 지도 키 없이 동작한다** (D-57). 카카오맵 공개 링크를 쓴다
      → 빈 목록(강릉시 강동면)과 실패를 화면으로 갈라 확인했다
- [x] **S-07 즐겨찾기 (Room)** — 상세의 북마크 · 더보기 → 즐겨찾기 목록
      → **앱을 죽이고 網을 끊어도 그대로 뜬다.** id 만이 아니라 이름·주소를 함께 담기 때문 (D-60)
- [x] **오프라인 캐시 (Room v2)** — 지역·장소·건수 사본 + 상단 오프라인 배너 (`spec.md §5.3`)
      → 網이 끊겨도 **홈·목록·상세가 마지막 조회 결과로 뜬다.** 배너에 받아 둔 시각이 찍힌다
      → 상세는 **받아 둔 시각(배너)** 과 **원본 기준일(하단)** 을 나란히 보여준다 — 둘은 다른 값이다
      → 즐겨찾기 스냅샷에 출처·기준일을 더해 **D-61 의 한계를 닫았다.** 캐시가 밀려 나가도 열린다
      → 빈 사본은 "없다"가 아니라 **캐시 미스**로 둔다 (D-62 — D-53 과 같은 규칙의 세 번째 적용)
      → **버전 1 DB 가 깔린 기기에 덮어 설치해서** 마이그레이션·즐겨찾기 보존까지 실측했다
      → S-01 의 최근 지역 빈틈(網 복구 후 칩이 안 돌아오던 것)도 함께 고쳤다
- [x] **Pretendard 번들** (`spec.md §6.3`) — 가변 폰트 파일 하나로 굵기 4단을 낸다 (D-67)
      → **서브셋을 만들지 않는다.** 장소명이 공공데이터에서 오므로 어떤 음절이 나올지 모른다 (D-68)
      → 건수·전화번호에 tabular figures. release APK 2.82MB → **5.79MB** (예산 30MB)
      → 시스템 글꼴 200% 에서 레이아웃이 깨지지 않는 것까지 확인했다
- [ ] 지도(S-02) — **대기가 끝났다.** 네이티브 앱 키 발급 + 콘솔 등록까지 마쳤다 (2026-09-06)
      목록은 이미 있으니 바텀시트에 넣으면 된다. **1단계에 남은 것은 이것 하나다**

> ⚠️ **화면 설계가 2026-09-05 에 바뀌었다.** 하단 탭 4개 + 홈 허브(S-00) 신설,
> 별점·후기·장소 사진은 후순위. **`DECISIONS.md` §26·§30 (D-39·D-40·D-47) 을 먼저 읽을 것.**
> 시안 원본은 `이태우_디자인시안/` 에 있다 (참고용이며 확정안이 아니다).

### 병행 트랙

- [x] 공공데이터포털 인증키 — 발급 완료 (국가동물보호정보시스템 15098931 · TourAPI 15101578)
- [ ] **LOCALDATA 인증키** — localdata.go.kr 은 공공데이터포털과 **별도 사이트·별도 가입**
- [x] **카카오 개발자 키 2종** — 앱 하나에서 둘 다 나온다. 2026-09-06 에 둘 다 끝났다
      · **REST API 키** — ✅ `etl/.env` (좌표 ETL 이 쓴다)
      · **네이티브 앱 키** — ✅ 발급 + **콘솔에 Android 플랫폼 등록까지 완료**
        패키지명 `io.github.junkie300.petapp` · debug 키 해시 `Ch4If1Ec+BZq1osS7SK/wnmCwOo=`
        ⚠️ **키 값 자체는 저장소에 없다.** `이태우메모(건드리지말것).txt` 에 있고,
        쓸 때는 `app/local.properties` 에 넣는다 (anon 키와 같은 방식 — D-69)
      · 길찾기·전화는 이 키 없이도 이미 된다 (D-57)
- [ ] **테스터 12명 명단** — 개발과 무관하게 리드타임이 가장 길다 (`plan.md` §3)

---

## 이어서 작업하기

### 1. 환경 되살리기

```
cd D:\pet\etl
.venv\Scripts\activate          # 없으면: python -m venv .venv && pip install -r requirements.txt
```

`etl/.env` 는 커밋되지 않는다. **PC를 옮겼다면 다시 만들어야 한다** (`.env.example` 참고).

| 키 | 상태 | 없으면 막히는 것 |
|---|---|---|
| `SUPABASE_URL` | ✅ | 전부 |
| `SUPABASE_SERVICE_ROLE_KEY` | ✅ | 전부 (반드시 **legacy `service_role`**) |
| `DATA_GO_KR_KEY` | ✅ | `mapping` (APMS·TourAPI) |
| `KAKAO_REST_API_KEY` | ✅ | `coords` (읍면동 중심좌표) |
| `LOCALDATA_API_KEY` | ❌ | 2단계 미용시설 (`localdata_cd` 는 문서로 이미 끝냈다) |

앱은 `.env` 가 아니라 **`app/local.properties`** 를 쓴다 (`app/local.properties.example` 참고).

| 키 | 상태 | 없으면 막히는 것 |
|---|---|---|
| `SUPABASE_URL` | ✅ | — |
| `SUPABASE_ANON_KEY` | ✅ | 앱 화면에 데이터가 안 뜬다 (앱은 켜지고 안내 문구가 나온다) |
| `KAKAO_NATIVE_APP_KEY` | ◐ | 지도(S-02). **값은 이태우 메모에 있고 아직 넣지 않았다** — D-69 |

⚠️ 세 키 모두 **APK 안에 그대로 들어가는 공개 키**다. 안전은 숨겨서가 아니라 서버 쪽 제약이
지킨다 — Supabase 는 RLS 로, 카카오는 **패키지명 + 키 해시 대조**로 막는다 (D-69).

### 2. 지금 어디까지 왔는지 확인 — **여기서 시작한다**

```
python run.py status
```

환경변수 유무(값은 출력하지 않는다) · `regions` 적재 · 0단계 컬럼별 충족률 ·
최근 ETL 이력 · 다음에 칠 명령을 한 화면에 보여준다.

```
python -m unittest discover -s tests    # 72개 통과해야 함 (네트워크·DB 불필요)
```

DB 를 더 자세히 보려면 Supabase SQL Editor 에 `supabase/verify.sql` 을 붙여넣는다.
스키마·RLS·계층·매핑·좌표 범위를 한 번에 점검한다.

**앱 쪽은 이렇게 확인한다** (자세한 것은 [app/README.md](app/README.md)):

```
cd D:\pet\app
gradlew testDebugUnitTest     # 33개 통과해야 함 (기기·네트워크 불필요)
gradlew installDebug          # 에뮬레이터/기기에 설치
```

⚠️ **색·형태·화면 흐름은 빌드가 아니라 화면으로만 검증된다** (D-45·D-55·D-56 이 전부
에뮬레이터에서 잡힌 것이다). 고쳤으면 `adb exec-out screencap -p > shot.png` 로 눈으로 본다.

### 3. 지금 있는 ETL 명령

| 명령 | 하는 일 | 필요한 키 |
|---|---|---|
| `python run.py status` | 현재 상태 요약 | Supabase |
| `python run.py regions` | 법정동코드 → `regions` (**로컬 전용**, `etl/data/` 파일 필요) | Supabase |
| `python run.py mapping` | APMS·TourAPI 지역코드 매핑 | `DATA_GO_KR_KEY` |
| `python run.py coords --limit 50` | 읍면동 중심좌표 | `KAKAO_REST_API_KEY` |
| `python run.py localdata` | LOCALDATA 자치단체코드 매핑 | **없음** (`etl/docs/` 엑셀) |
| `python run.py hospitals` | 동물병원 → `places` (10,617건, 약 10분) | `DATA_GO_KR_KEY` + `KAKAO_REST_API_KEY` |

`--dry-run` 을 붙이면 DB 에 쓰지 않는다. `mapping`·`coords` 는 GitHub Actions
(`.github/workflows/etl.yml`)에서 수동 실행할 수도 있다 — Secrets 에 같은 이름으로 넣어둘 것.

### 4. 다음에 할 일

**1단계 화면은 지도(S-02) 하나 남았고, 막고 있던 키는 2026-09-06 에 풀렸다.**
홈 → 목록 → 상세 → 즐겨찾기가 전부 실제 데이터로 이어지고, 網이 끊겨도 사본으로 뜬다.

#### 지금 바로 할 수 있는 것 (대기 없음)

1. **지도(S-02)** ← **여기서 시작하는 것을 권한다.** 1단계에 남은 마지막 화면이다
   · 시작 전에 `app/local.properties` 에 `KAKAO_NATIVE_APP_KEY` 를 넣는다
     (값은 `이태우메모(건드리지말것).txt` 에 있다. **저장소에 커밋하지 않는다** — D-69)
   · 한 번에 다 만들지 말고 **① SDK 연동 + 핀 표시까지 띄워 화면으로 확인** →
     ② 바텀시트 3단 → ③ 카테고리 필터 칩 → ④ 클러스터링 순으로 나눈다
   · 목록·상세·카드(`ui/place/`)는 이미 있다. **바텀시트 안에 그대로 들어간다**
   · ⚠️ 키가 틀리거나 콘솔 등록이 어긋나면 **빌드는 성공하고 지도만 까맣게** 뜬다.
     그때는 콘솔의 패키지명·키 해시부터 대조한다 (D-69)
2. **캐시를 먼저 그리고 뒤에서 갱신 (cache-first)**
   지금은 오프라인에서 사본이 뜨기까지 8~16초 걸린다 (아래 '알려진 빈틈'). 저장소가 값을
   두 번 흘려보내야 해서 `suspend` → `Flow` 전환이 따라온다. **배너는 요청이 실패한 뒤에**
   띄워야 한다 — 網이 멀쩡한데 "오프라인"이 잠깐 보이면 그게 더 나쁘다 (D-66)
3. **거리 표기** — 위치 권한이 붙는 시점에 목록 카드에 더한다 (`spec.md §6.4`, D-59).
   지도의 "현재 위치로" 버튼과 **같은 권한**을 쓰므로 S-02 와 함께 하는 것이 자연스럽다
4. **앱 아이콘** — 서체까지 왔으니 남은 임시값은 아이콘 하나다

#### 승인·발급 대기 (개발과 병행)

5. **LOCALDATA 가입 + 인증키** — https://www.localdata.go.kr (공공데이터포털 키는 안 통한다).
   **2단계 미용시설**에 필요하다. `localdata_cd` 는 문서로 이미 끝냈으므로 급하지 않다
6. **테스터 12명 명단** — 리드타임이 가장 길다 (`plan.md` §3). 개발과 무관하게 지금부터 모은다
7. **KIPRIS 상표 조회** — 「미리펫」을 제9류(소프트웨어)·제42류(SaaS)로 확인 (D-43).
   스토어 등록 전까지만 하면 된다

### 5. 알려진 빈틈 (고쳐야 하지만 급하지 않은 것)

- **오프라인에서 사본이 뜨기까지 8~16초 걸린다** (D-66 아래 '알려진 한계').
  서버 요청이 타임아웃(8초)을 다 쓰고 **실패해야** 캐시로 넘어간다. 홈은 지역 8초 + 건수 8초다.
  실제 기기에서 圈外면 DNS 가 즉시 실패해 훨씬 빠르지만 그건 운이다.
  → **사본을 먼저 그리고 뒤에서 갱신**하는 구조로 바꾸면 사라진다 (위 '다음에 할 일' 2번)
- **버전 1 때 담은 즐겨찾기는 오프라인 상세가 열리지 않는다.** 스냅샷에 출처가 없기 때문이다 (D-64).
  목록에는 그대로 뜨고, 온라인에서 한 번 열면 최신으로 덮여 그 뒤로는 열린다. **의도한 동작이다**
- **시스템 글꼴 200% 에서 하단 탭 4개의 라벨이 서로 닿을 듯이 좁다.** 깨지지는 않는다
  (`spec.md §6.5` 는 "레이아웃이 깨지지 않을 것"을 요구한다). 탭 이름을 줄이거나 아이콘만
  남기는 선택지가 있는데, 아이콘만으로는 "구조·입양"과 "더보기"가 구분되지 않는다 — D-39
- **`places.region_code` 가 NULL 인 행이 영업중 기준 27건(0.5%)** 남아 있다.
  주소가 아예 없거나 개편 이전 주소라 카카오도 못 읽는 건이다. 지역 필터에는 안 잡히지만
  좌표가 있으면 반경 검색에는 잡힌다. **시군구 코드로 대충 채우면 안 된다** (D-51)

> ⚠️ **LOCALDATA API 는 변경분만 준다.** 전체 데이터는 API 가 아니라 다운로드 페이지에서
> 받아야 한다(전체분 매월 2일 배포). 1단계 ETL 은 **최초 1회 전체분 → 이후 API 증분**
> 2단 구조로 짠다. API 만으로 전국을 긁으려 하면 안 된다.

### 먼저 밟은 함정 (다시 만나지 않도록)

| 증상 | 원인 / 대응 |
|---|---|
| `SupabaseException: Invalid API key` | supabase-py 2.15.1 은 키를 **JWT 정규식으로 검사**한다. 새 형식 `sb_secret_...` 은 통과 못 한다 → **Legacy API keys 의 `service_role`**(`eyJ...`) 을 쓴다 |
| `PGRST125 Invalid path` | `SUPABASE_URL` 에 `/rest/v1` 이 붙어 있었다 → 지금은 `config.normalize_url()` 이 자동으로 떼어낸다 |
| `42501 violates row-level security policy` | `anon` 키를 넣었다. ETL 은 `service_role` 필요 → 지금은 `db.get_client()` 가 실행 전에 걸러낸다 |
| 광역시도가 17개가 아니라 **16개** | 오류가 아니다. 광주광역시+전라남도 → **전남광주통합특별시**(코드 12). `DECISIONS.md` D-29 참고 |
| `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` | 공공데이터포털 **Encoding 키**를 넣었다. `%2F` 가 `%252F` 로 이중 인코딩된다 → **Decoding 키**를 쓴다 (지금은 `config.normalize_service_key()` 가 자동으로 되돌린다) |
| `NO_OPENAPI_SERVICE_ERROR` | TourAPI 는 `KorService1` → **`KorService2`** 로 바뀌었다. 시군구 목록은 `sigunguCode2` 가 아니라 **`areaCode2` 에 `areaCode` 를 넘겨서** 받는다 |
| 앱 빌드가 `role='service_role' 키가 들어 있습니다` 로 실패 | `SUPABASE_ANON_KEY` 에 `service_role` 키를 넣었다. 대시보드에서 두 키가 나란히 있고 **둘 다 `eyJ...`** 로 시작해 구분이 안 된다 → **처음부터 값이 보이는 `anon (public)`** 쪽이다. 가려져 있어 👁 를 눌러야 보이는 쪽은 `service_role` 이다 |
| `build.gradle.kts` 에서 `Unresolved reference: util` | Gradle Kotlin DSL 에서 `java` 는 JavaPluginExtension 접근자로 잡혀 **`java.util` 패키지를 가린다** → 파일 맨 위에 `import java.util.Base64` 를 두고 짧은 이름으로 쓴다 |
| 동물병원 API 가 `NO_OPENAPI_SERVICE_ERROR` | 오퍼레이션명이 **`info`** 다(`.../animal_hospitals/info`). 파라미터도 `_type` 이 아니라 **`returnType`**, `resultCode` 는 **`"0"`** 한 자리다. 오퍼레이션명은 추측으로 못 맞춘다 — 개발계정 상세 > 상세기능정보 > **[미리보기]** 의 URL 을 봐야 한다 |
| 지도에 찍은 병원이 **한 블록씩 어긋남** | `CRD_INFO_X/Y` 는 경위도가 아니라 평면직각좌표다. **EPSG:5174** 를 쓴다. **EPSG:2097 은 겉보기엔 맞는데 일관되게 321m 어긋나서 눈으로 못 잡는다** (D-50) |
| 좌표가 제주 남서 해상에 찍힘 | `CRD_INFO_X` 가 `"0"` 이었다. (0,0)이 EPSG:5174 에서 (33.48, 124.85)로 변환돼 한반도 범위 검사를 통과한다 → 1000 미만은 값 없음으로 본다 |
| 홈 탭을 눌렀는데 **지도가 뜬다** | 홈 타일에서 지도를 `navigate()` 로 홈 **위에** 얹었다. 그러면 지도가 홈 탭의 저장된 백스택에 딸려 들어가 `restoreState` 로 되살아난다 → **탭인 화면은 다른 탭 위에 얹지 않는다.** `switchTab()` 하나로 모았다 (D-55) |
| 다크 모드에서 **건수가 안 보인다** | `spec.md §6.2` 의 카테고리 5색은 **라이트 배경 기준**이다. 어두운 카드 위에 딥그린을 올리면 대비가 무너진다 → 5색에 다크 짝을 만든다 (D-56). D-45 와 같은 교훈이다 |
| 카카오 콘솔에 **`앱 설정 > 플랫폼`** 메뉴가 없음 | 콘솔이 개편됐다. 지금은 **[앱] > [플랫폼 키] > [네이티브 앱 키]** 안에서 패키지명·키 해시를 넣는다. 앱 목록은 https://developers.kakao.com/console/app (로그인해야 보인다) |
| Room 마이그레이션이 **버전 1 기기에서만** 죽음 | 손으로 쓴 `CREATE TABLE` 이 Room 이 만드는 문장과 한 글자 다르면 `Migration didn't properly handle` 이 난다. **새로 깐 기기에서는 절대 안 드러난다** → 문장을 `MIGRATION_1_2_SQL` 목록으로 빼고 `MigrationSqlTest` 가 `schemas/.../2.json` 과 대조한다 (D-65) |
| 오프라인인데 목록이 **"이 지역에는 없습니다"** 로 뜸 | 빈 사본을 성공으로 돌려줬다. 없다는 것을 안 게 아니라 **모르는 것**이다 → 빈 사본은 캐시 미스로 둔다 (D-62) |
| 병원 주소가 `regions` 에 없음 | 인허가 데이터에 **폐지된 법정동**이 남아 있다(화성시 동탄구 오산동 → 여울동). 우리 ETL 이 틀린 게 아니다 → 카카오 `b_code` 로 현행 코드를 받는다 |
| PostgREST 조회가 1,000행에서 잘림 | 읍면동은 5,067행이다. `.range()` 로 페이지네이션해야 한다 (`mapping.select_all`) |

> ⚠️ **D-29·D-30 은 앞으로 가장 조심할 지점이다.** 공공 API 들이 행정구역 개편을 **같은 속도로
> 반영하지 않는다.** 2026-09-02 실측 기준 APMS 는 반영했고 **TourAPI 만 옛 체계**다.
> 그래서 `전남광주통합특별시 ↔ 광주+전남`, `제물포구·영종구 ↔ 중구` 같은 1:N 구간이 생긴다.
> 단순 1:1 조인은 데이터를 절반 날린다. → `mapping.py` 의 예외표 두 개로 처리한다.
>
> ⚠️ **D-31.** TourAPI 는 **코드만 옛 체계이고 주소(`addr1`)는 새 체계**다.
> 5단계에서 TourAPI POI 를 읍면동에 배정할 때는 **코드가 아니라 주소 문자열을 파싱**해야 한다.
