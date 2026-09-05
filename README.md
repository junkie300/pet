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
    data/                     regions 조회 · 최근 지역 저장
    ui/region/                S-01 지역 선택 화면
    ui/common/UiState.kt      로딩 / 없음 / 실패를 타입으로 구분
.github/workflows/     test.yml · etl.yml(mapping·coords 수동 실행 가능)
```

---

## 진행 상황 (2026-09-05)

### 0단계 — 지역코드 정규화 ◐ 거의 완료

- [x] Supabase 프로젝트 생성 (`pet-app`, 리전 Seoul), PostGIS·pg_trgm 활성화
- [x] DB 스키마 전체 적용 — `regions` `places` `shelters` `animals` `sync_logs` + RLS + `places_nearby` RPC
- [x] 법정동코드 → `regions` ETL 작성 및 **적재 완료**
      → **시도 16 · 시군구 256 · 읍면동 5,067**
- [x] 국가동물보호정보시스템 코드 매핑 (`apms_upr_cd`, `apms_org_cd`) — **미매핑 0**
- [x] TourAPI 코드 매핑 (`tour_area_cd`, `tour_sigungu_cd`) — **미매핑 0**
- [ ] LOCALDATA 지역코드 매핑 (`localdata_cd`) ← **2단계 미용시설에 필요. LOCALDATA 인증키 대기**
- [ ] 읍면동 중심좌표 (`center_lat`, `center_lng`) ← **키 확보 완료. `run.py coords` 실행만 남음**

### 1단계 — 동물병원 ◐ 앱 뼈대 착수

- [x] 안드로이드 프로젝트 생성 — `app/`. **debug·release 빌드와 단위 테스트 5개 통과 확인**
      (release APK 2.5MB — spec 의 30MB 예산 안)
- [x] Supabase Kotlin SDK 연결. anon 키는 `local.properties` 에서 읽어 `BuildConfig` 로 넣는다
- [x] S-01 지역 선택 — 시도→시군구→읍면동 3단 드롭다운 · 지역명 검색 · 최근 지역 3개
- [x] 로딩 / 데이터 없음 / 불러오기 실패를 **타입으로 구분** (`UiState`) · 다크 모드
- [x] Supabase anon 키 투입 — **debug 빌드 성공**. anon 키로 `regions` 질의 4종 + RLS 쓰기 차단까지 실측 확인
- [ ] **화면을 실제로 띄워 보기** ← **기기·에뮬레이터 대기** (`adb devices` 가 비어 있다)
- [ ] 동물병원 ETL ← **행안부 15154952 활용신청 대기** (D-37 — 신청해야 엔드포인트가 열린다)
- [ ] 지도(S-02)·상세(S-03) ← 카카오 **네이티브 앱 키** 대기

> 앱이 부르는 4가지 `regions` 질의(시도 목록 · 하위 목록 · 이름 검색 · 코드 조회)를
> **앱과 같은 anon 키·같은 컬럼 목록으로 실측했다** — 전부 200. `INSERT` 는 RLS 가 401 로 막았다.
> 읽기만 되고 쓰기는 막힌, 앱에 넣기 맞는 키다. 남은 것은 기기에 올려 띄우는 것뿐이다.

> **화면 설계가 2026-09-05 에 바뀌었다** — 하단 탭 4개 + 홈 허브(S-00) 신설,
> 별점·후기는 후순위. `DECISIONS.md` §26 (D-39·D-40·D-41) 을 먼저 읽을 것.

### 병행 트랙

- [x] 공공데이터포털 인증키 — 발급 완료 (국가동물보호정보시스템 15098931 · TourAPI 15101578)
- [ ] **LOCALDATA 인증키** — localdata.go.kr 은 공공데이터포털과 **별도 사이트·별도 가입**
- ◐ **카카오 개발자 키 2종** — 앱 하나에서 둘 다 나온다
      · **REST API 키** — ✅ 발급 완료 (`etl/.env`)
      · **네이티브 앱 키** — ❌ 지도(S-02) 붙일 때 필요
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
| `LOCALDATA_API_KEY` | ❌ | `localdata_cd` · 2단계 미용시설 |

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
python -m unittest discover -s tests    # 44개 통과해야 함 (네트워크·DB 불필요)
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

`--dry-run` 을 붙이면 DB 에 쓰지 않는다. `mapping`·`coords` 는 GitHub Actions
(`.github/workflows/etl.yml`)에서 수동 실행할 수도 있다 — Secrets 에 같은 이름으로 넣어둘 것.

### 4. 다음에 할 일 (순서대로)

> 1~2 는 **오늘 안에 끝나는 것들**이다. 승인 대기가 없다.

1. **`python run.py coords`** — 읍면동 중심좌표 5,067건. 카카오 REST 키가 이미 있다.
   `--limit 50` 으로 표본을 먼저 확인한 뒤 전량 돌린다 (D-33)
2. **기기 또는 에뮬레이터 연결** — `gradlew installDebug` 로 지역 선택 화면을 띄운다.
   빌드와 anon 키는 이미 검증됐다. `adb devices` 가 비어 있는 것만 남았다
3. **`15154952` 활용신청** — https://www.data.go.kr/data/15154952/openapi.do 자동승인.
   ⚠️ **신청해야 엔드포인트 명세가 열린다** (D-37). 승인 후 마이페이지 > 오픈API > 개발계정에서
   **참고문서와 요청 URL 을 복사해 올 것.** 그게 있어야 동물병원 ETL 을 쓸 수 있다
4. **LOCALDATA 가입 + 인증키** — https://www.localdata.go.kr (공공데이터포털 키는 여기서 안 통한다).
   2단계 미용시설과 `localdata_cd` 에 결국 필요. 발급 후 인증키 **조회번호를 따로 저장**할 것

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
| PostgREST 조회가 1,000행에서 잘림 | 읍면동은 5,067행이다. `.range()` 로 페이지네이션해야 한다 (`mapping.select_all`) |

> ⚠️ **D-29·D-30 은 앞으로 가장 조심할 지점이다.** 공공 API 들이 행정구역 개편을 **같은 속도로
> 반영하지 않는다.** 2026-09-02 실측 기준 APMS 는 반영했고 **TourAPI 만 옛 체계**다.
> 그래서 `전남광주통합특별시 ↔ 광주+전남`, `제물포구·영종구 ↔ 중구` 같은 1:N 구간이 생긴다.
> 단순 1:1 조인은 데이터를 절반 날린다. → `mapping.py` 의 예외표 두 개로 처리한다.
>
> ⚠️ **D-31.** TourAPI 는 **코드만 옛 체계이고 주소(`addr1`)는 새 체계**다.
> 5단계에서 TourAPI POI 를 읍면동에 배정할 때는 **코드가 아니라 주소 문자열을 파싱**해야 한다.
