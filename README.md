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

## 진행 상황 (2026-08-30)

### 0단계 — 지역코드 정규화 ◐ 진행 중

- [x] Supabase 프로젝트 생성 (`pet-app`, 리전 Seoul), PostGIS·pg_trgm 활성화
- [x] DB 스키마 전체 적용 — `regions` `places` `shelters` `animals` `sync_logs` + RLS + `places_nearby` RPC
- [x] 법정동코드 → `regions` ETL 작성 및 **적재 완료**
      → **시도 16 · 시군구 256 · 읍면동 5,067**
- [ ] LOCALDATA 지역코드 매핑 (`localdata_cd`) ← **1단계에 바로 필요**
- [ ] 국가동물보호정보시스템 코드 매핑 (`apms_upr_cd`, `apms_org_cd`)
- [ ] TourAPI 코드 매핑 (`tour_area_cd`, `tour_sigungu_cd`)
- [ ] 읍면동 중심좌표 (`center_lat`, `center_lng`)

> 남은 4개는 **전부 공공데이터 인증키가 있어야** 진행된다.

### 병행 트랙

- [ ] 공공데이터포털 인증키 신청 — LOCALDATA / 국가동물보호정보시스템 / TourAPI
- [ ] 카카오 개발자 네이티브 앱 키 (1단계 지도용)
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
python -m unittest discover -s tests    # 9개 통과해야 함
```

### 다음에 할 일 (순서대로)

1. **인증키 신청** — 승인 대기가 있으므로 가장 먼저 걸어둔다
2. **매핑 ETL 작성** — `etl/petetl/sources/` 에 소스별 모듈 추가.
   `regions` 의 외부 코드 컬럼을 UPDATE 한다
3. **매핑 검증** — 미매핑 지역이 **0** 이어야 0단계 완료 (`plan.md` 0단계 DoD)
4. 1단계 착수 — LOCALDATA 동물병원 ETL + 안드로이드 앱 뼈대

### 먼저 밟은 함정 (다시 만나지 않도록)

| 증상 | 원인 / 대응 |
|---|---|
| `SupabaseException: Invalid API key` | supabase-py 2.15.1 은 키를 **JWT 정규식으로 검사**한다. 새 형식 `sb_secret_...` 은 통과 못 한다 → **Legacy API keys 의 `service_role`**(`eyJ...`) 을 쓴다 |
| `PGRST125 Invalid path` | `SUPABASE_URL` 에 `/rest/v1` 이 붙어 있었다 → 지금은 `config.normalize_url()` 이 자동으로 떼어낸다 |
| `42501 violates row-level security policy` | `anon` 키를 넣었다. ETL 은 `service_role` 필요 → 지금은 `db.get_client()` 가 실행 전에 걸러낸다 |
| 광역시도가 17개가 아니라 **16개** | 오류가 아니다. 광주광역시+전라남도 → **전남광주통합특별시**(코드 12). `DECISIONS.md` D-29 참고 |

> ⚠️ **D-29 는 앞으로 가장 조심할 지점이다.** 공공 API 들이 이 개편을 같은 속도로 반영하지 않으므로,
> 매핑에서 **새 코드 1개 ↔ 옛 코드 2개** 구간이 생긴다. 단순 1:1 조인은 데이터를 절반 날린다.
