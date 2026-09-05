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
  run.py                 진입점 (status · regions · mapping · coords)
  petetl/status.py       "지금 어디까지 왔나" 요약
  petetl/publicapi.py    공공데이터포털 공통 클라이언트
  petetl/sources/        소스별 ETL
app/                   안드로이드 앱 (Kotlin + Compose)   → app/README.md
  gradle/libs.versions.toml   의존성 버전은 전부 여기서만 바꾼다
  local.properties.example    Supabase 접속 정보 — 복사해서 local.properties 로
  app/src/main/java/.../
    data/                     regions·places 조회 · 최근 지역 저장
    data/PlaceCategory.kt     카테고리 5종 + **적재 여부**(loaded) — D-53
    ui/nav/PetApp.kt          하단 탭 4개 + NavHost. 탭 전환은 여기 switchTab() 하나로 — D-55
    ui/home/                  S-00 홈 허브 (지역 칩 · 카테고리 6칸 · 건수)
    ui/place/                 장소 목록 · S-03 상세 (길찾기·전화·공유 — 키 불필요, D-57)
    ui/common/CategoryUi.kt   카테고리 라벨·아이콘·색 — 세 화면이 이 표 하나를 읽는다
    ui/region/                S-01 지역 선택 화면
    ui/more/                  더보기 (즐겨찾기 자리 · 데이터 출처 · 앱 정보)
    ui/common/UiState.kt      로딩 / 없음 / 실패를 타입으로 구분
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

### 1단계 — 동물병원 ◐ 지도만 남았다

- [x] 안드로이드 프로젝트 생성 — `app/`. **debug·release 빌드와 단위 테스트 12개 통과 확인**
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
- [ ] **S-07 즐겨찾기 (Room)** ← 막힌 것 없음. **다음은 여기다**
- [ ] 지도(S-02) ← 카카오 **네이티브 앱 키** 대기. 목록은 이미 있으니 바텀시트에 넣으면 된다

> ⚠️ **화면 설계가 2026-09-05 에 바뀌었다.** 하단 탭 4개 + 홈 허브(S-00) 신설,
> 별점·후기·장소 사진은 후순위. **`DECISIONS.md` §26·§30 (D-39·D-40·D-47) 을 먼저 읽을 것.**
> 시안 원본은 `이태우_디자인시안/` 에 있다 (참고용이며 확정안이 아니다).

### 병행 트랙

- [x] 공공데이터포털 인증키 — 발급 완료 (국가동물보호정보시스템 15098931 · TourAPI 15101578)
- [ ] **LOCALDATA 인증키** — localdata.go.kr 은 공공데이터포털과 **별도 사이트·별도 가입**
- ◐ **카카오 개발자 키 2종** — 앱 하나에서 둘 다 나온다
      · **REST API 키** — ✅ 발급 완료 (`etl/.env`)
      · **네이티브 앱 키** — ❌ 지도(S-02) 붙일 때 필요. **길찾기에는 필요 없다** (D-57)
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

**데이터도 찼고 뼈대도 섰다. 홈에서 장소로 들어가는 길만 아직 비어 있다.**

#### 지금 바로 할 수 있는 것 (대기 없음)

1. **S-07 즐겨찾기** (Room) ← **여기서 시작하는 것을 권한다**
   - 로그인 불필요. 로컬 저장이라 서버·키를 기다릴 것이 없다
   - 상세 화면에 버튼 자리가 이미 잡혀 있다(길찾기·전화·공유 옆)
   - 더보기 탭의 `즐겨찾기 · 준비 중` 줄이 이걸 기다리고 있다
2. **오프라인 캐시** — 아래 '알려진 빈틈' 참고
3. **Pretendard 번들** — 화면이 확정됐으니 지금이 적기다 (`Type.kt` 의 TODO)
4. **거리 표기** — 위치 권한이 붙는 시점에 목록 카드에 더한다 (`spec.md §6.4`, D-59)

#### 승인·발급 대기 (개발과 병행)

6. **카카오 네이티브 앱 키** — 지도(S-02)에 필요. REST 키와 **같은 앱**에서 나온다
   (developers.kakao.com > 내 애플리케이션 > **앱 키 > 네이티브 앱 키**)
7. **LOCALDATA 가입 + 인증키** — https://www.localdata.go.kr (공공데이터포털 키는 안 통한다).
   **2단계 미용시설**에 필요하다. `localdata_cd` 는 문서로 이미 끝냈으므로 급하지 않다
8. **테스터 12명 명단** — 리드타임이 가장 길다 (`plan.md` §3). 개발과 무관하게 지금부터 모은다
9. **KIPRIS 상표 조회** — 「미리펫」을 제9류(소프트웨어)·제42류(SaaS)로 확인 (D-43).
   스토어 등록 전까지만 하면 된다

### 5. 알려진 빈틈 (고쳐야 하지만 급하지 않은 것)

- **오프라인으로 앱을 시작하면 네트워크가 복구돼도 S-01 의 최근 지역 칩이 안 돌아온다.**
  `RegionPickerViewModel.observeRecent` 가 실패 시 빈 목록을 넣고 다시 시도하지 않는다.
  홈(S-00)은 `다시 시도` 버튼으로 회복되는 것을 확인했지만 **S-01 에는 그 버튼이 없다.**
  `spec.md §5.3` 의 오프라인 캐시와 함께 처리하는 것이 맞다
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
| 병원 주소가 `regions` 에 없음 | 인허가 데이터에 **폐지된 법정동**이 남아 있다(화성시 동탄구 오산동 → 여울동). 우리 ETL 이 틀린 게 아니다 → 카카오 `b_code` 로 현행 코드를 받는다 |
| PostgREST 조회가 1,000행에서 잘림 | 읍면동은 5,067행이다. `.range()` 로 페이지네이션해야 한다 (`mapping.select_all`) |

> ⚠️ **D-29·D-30 은 앞으로 가장 조심할 지점이다.** 공공 API 들이 행정구역 개편을 **같은 속도로
> 반영하지 않는다.** 2026-09-02 실측 기준 APMS 는 반영했고 **TourAPI 만 옛 체계**다.
> 그래서 `전남광주통합특별시 ↔ 광주+전남`, `제물포구·영종구 ↔ 중구` 같은 1:N 구간이 생긴다.
> 단순 1:1 조인은 데이터를 절반 날린다. → `mapping.py` 의 예외표 두 개로 처리한다.
>
> ⚠️ **D-31.** TourAPI 는 **코드만 옛 체계이고 주소(`addr1`)는 새 체계**다.
> 5단계에서 TourAPI POI 를 읍면동에 배정할 때는 **코드가 아니라 주소 문자열을 파싱**해야 한다.
