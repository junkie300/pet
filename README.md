# 반려동물 동반 외출·여행 준비 앱

반려동물과 낯선 지역에 갈 때, 그 지역의 동물병원·미용·동반 식당·관광지를
**읍면동 단위로 한 화면에서 미리 확인하는** 안드로이드 앱.

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
  verify.sql           스키마가 제대로 들어갔는지 점검
etl/                   공공데이터 → Supabase 파이썬 ETL   → etl/README.md
app/                   안드로이드 앱 (1단계에서 생성 예정)
.github/workflows/     test.yml(동작 중) · etl.yml(1단계에서 활성화)
```

---

## 진행 상황 (2026-09-02)

### 0단계 — 지역코드 정규화 ◐ 거의 완료

- [x] Supabase 프로젝트 생성 (`pet-app`, 리전 Seoul), PostGIS·pg_trgm 활성화
- [x] DB 스키마 전체 적용 — `regions` `places` `shelters` `animals` `sync_logs` + RLS + `places_nearby` RPC
- [x] 법정동코드 → `regions` ETL 작성 및 **적재 완료**
      → **시도 16 · 시군구 256 · 읍면동 5,067**
- [x] 국가동물보호정보시스템 코드 매핑 (`apms_upr_cd`, `apms_org_cd`) — **미매핑 0**
- [x] TourAPI 코드 매핑 (`tour_area_cd`, `tour_sigungu_cd`) — **미매핑 0**
- [ ] LOCALDATA 지역코드 매핑 (`localdata_cd`) ← **1단계에 바로 필요. LOCALDATA 인증키 대기**
- [ ] 읍면동 중심좌표 (`center_lat`, `center_lng`) ← **카카오 REST API 키 대기** (ETL 작성 완료)

### 병행 트랙

- [x] 공공데이터포털 인증키 — 발급 완료 (국가동물보호정보시스템 15098931 · TourAPI 15101578)
- [ ] **LOCALDATA 인증키** — localdata.go.kr 은 공공데이터포털과 **별도 사이트·별도 가입**
- [ ] **카카오 개발자 키 2종** — 앱 하나에서 둘 다 나온다. 즉시 발급
      · **REST API 키** — 읍면동 중심좌표·지오코딩 (0단계 잔여분에 지금 필요)
      · **네이티브 앱 키** — 카카오맵 Android SDK (1단계)
- [ ] **테스터 12명 명단** — 개발과 무관하게 리드타임이 가장 길다 (`plan.md` §3)

---

## 이어서 작업하기

### 환경 되살리기

```
cd D:\pet\etl
.venv\Scripts\activate          # 없으면: python -m venv .venv && pip install -r requirements.txt
```

`etl/.env` 는 커밋되지 않으므로 **PC를 옮겼다면 다시 만들어야 한다.** (`.env.example` 참고)

```
python -c "from petetl.config import load_settings; from petetl.db import jwt_role; s=load_settings(); print(s.supabase_url, jwt_role(s.supabase_service_role_key))"
```

→ `https://xxxxx.supabase.co service_role` 이 나오면 정상.

```
python -m unittest discover -s tests    # 28개 통과해야 함
```

### 다음에 할 일 (순서대로)

1. **카카오 REST API 키** — https://developers.kakao.com 에서 앱 추가하면 즉시 나온다.
   `.env` 에 `KAKAO_REST_API_KEY` 를 넣고 `python run.py coords --limit 50` 으로 소량 먼저 확인
2. **`15154952` 활용신청** — https://www.data.go.kr/data/15154952/openapi.do 자동승인.
   행안부 동물병원 조회서비스. **LOCALDATA 없이도 1단계를 시작할 수 있다**
3. **LOCALDATA 가입 + 인증키** — https://www.localdata.go.kr (공공데이터포털 키는 여기서 안 통한다).
   2단계 미용시설과 `localdata_cd` 에 결국 필요. 발급 후 인증키 **조회번호를 따로 저장**할 것
4. 1단계 착수 — 동물병원 ETL + 안드로이드 앱 뼈대

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
| PostgREST 조회가 1,000행에서 잘림 | 읍면동은 5,067행이다. `.range()` 로 페이지네이션해야 한다 (`mapping.select_all`) |

> ⚠️ **D-29·D-30 은 앞으로 가장 조심할 지점이다.** 공공 API 들이 행정구역 개편을 **같은 속도로
> 반영하지 않는다.** 2026-09-02 실측 기준 APMS 는 반영했고 **TourAPI 만 옛 체계**다.
> 그래서 `전남광주통합특별시 ↔ 광주+전남`, `제물포구·영종구 ↔ 중구` 같은 1:N 구간이 생긴다.
> 단순 1:1 조인은 데이터를 절반 날린다. → `mapping.py` 의 예외표 두 개로 처리한다.
>
> ⚠️ **D-31.** TourAPI 는 **코드만 옛 체계이고 주소(`addr1`)는 새 체계**다.
> 5단계에서 TourAPI POI 를 읍면동에 배정할 때는 **코드가 아니라 주소 문자열을 파싱**해야 한다.
