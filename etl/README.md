# ETL

공공데이터를 Supabase 로 옮기는 파이썬 스크립트 모음.
서버는 없다. 로컬에서 수동 실행하거나 GitHub Actions cron 이 돌린다.

```
etl/
  run.py                     실행 진입점 (python run.py <소스>)
  requirements.txt
  .env.example               → .env 로 복사해서 채운다 (커밋 금지)
  data/                      원본 파일 두는 곳 (커밋 제외)
  petetl/
    config.py                환경변수 로딩
    db.py                    Supabase 클라이언트 + 청크 upsert
    synclog.py               sync_logs 기록 (앱의 "기준일" 표기 근거)
    sources/
      regions.py             0단계 · 법정동코드 → regions
      mapping.py             0단계 · APMS·TourAPI 지역코드 → regions 외부코드 컬럼
      coords.py              0단계 · 읍면동 중심좌표 (카카오 로컬 API)
  tests/                     표준 라이브러리 unittest (의존성 불필요)
```

## 준비

```bash
cd etl
python -m venv .venv
.venv\Scripts\activate          # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt

copy .env.example .env          # macOS/Linux: cp
# .env 에 SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY 를 채운다
```

> `SUPABASE_SERVICE_ROLE_KEY` 는 RLS 를 우회하는 관리자 키다.
> **ETL 전용이며 안드로이드 앱에는 절대 넣지 않는다.** 앱은 `anon` 키만 쓴다.

## 0단계 — regions 적재

1. `supabase/migrations/` 의 SQL 을 번호 순서대로 Supabase SQL Editor 에서 실행한다.
2. [행정표준코드관리시스템](https://www.code.go.kr) 에서 **법정동코드 전체자료**(txt)를 내려받아
   `etl/data/법정동코드 전체자료.txt` 로 둔다.
3. 먼저 DB 없이 결과를 확인한다.

   ```bash
   python run.py regions --dry-run --sample 5
   ```

4. 결과가 맞으면 적재한다.

   ```bash
   python run.py regions
   ```

### 이 스크립트가 처리하는 것

| 입력의 함정 | 처리 |
|---|---|
| 리(里) 행이 섞여 있다 | 제외. 앱의 최소 단위는 읍면동이다 |
| 폐지된 법정동 | 제외 |
| 세종특별자치시에 시도 행이 없다 | 시도 행을 합성해 3단 계층을 유지 (경고 로그 출력) |
| `경기도 성남시`처럼 산하 법정동이 없는 우산 행 | 제외. 남기면 시군구를 골랐는데 읍면동이 비는 막다른 길이 생긴다 |
| `성남시수정구` / `성남시 수정구` 표기 흔들림 | 상위 행의 `full_name` 을 접두사로 떼어내므로 양쪽 다 동작 |
| 파일 인코딩(cp949) | cp949 → utf-8 순서로 자동 판별 |

## 0단계 — 외부 코드 매핑

`regions` 의 외부 코드 컬럼을 채운다. `DATA_GO_KR_KEY` 가 필요하다.

```bash
python run.py mapping --dry-run    # 매핑 결과와 미매핑 목록만 확인 (DB 읽기는 함)
python run.py mapping              # regions 에 반영
```

**미매핑이 0이 아니면 절대 완료로 보지 않는다** (plan.md 0단계 DoD). 실측 결과:

```
apms_upr_cd 5339 · apms_org_cd 5289 · tour_area_cd 5338 · tour_sigungu_cd 5323 (총 5339행)
미매핑 0건
```

### 매핑이 1:1 이 아닌 이유

| 상황 | 처리 |
|---|---|
| 일반구(`성남시 분당구`)가 외부 API 에 없다 | 모시(`성남시`)로 접어서 N:1 매핑 |
| `전남광주통합특별시` ↔ TourAPI `5`+`38` | 시군구 레벨에서 갈린다. 시도 행은 비운다 (D-29) |
| 인천 `제물포구`·`영종구`·`서해구`·`검단구` | TourAPI 옛 이름(`중구`/`서구`)으로 되돌린다 (D-30) |
| 세종은 시군구가 없다 | `apms_org_cd` = NULL. 미매핑이 아니다 |
| APMS `orgCd=6489999` 처럼 이름 없는 행 | 버린다 |

> TourAPI 가 개편을 반영하면 `mapping.py` 의 `TOUR_SIDO_ALIAS` / `TOUR_SIGUNGU_ALIAS`
> 에서 해당 줄만 지우면 된다. 로직은 손대지 않는다.

## 0단계 — 읍면동 중심좌표

카카오 로컬 API(주소→좌표)로 채운다. `KAKAO_REST_API_KEY` 가 필요하다.

```bash
python run.py coords --limit 50     # 먼저 50곳만 돌려 결과를 확인한다
python run.py coords                # 나머지 전부 (빈 곳만 처리하므로 이어서 돈다)
python run.py coords --all          # 이미 채운 곳까지 다시 조회
```

- **기본은 `center_lat` 이 비어 있는 행만** 처리한다. 중간에 끊겨도 다시 돌리면 이어진다.
- **x 가 경도, y 가 위도다.** 뒤집어 쓰면 좌표가 동해로 간다.
  `to_latlng()` 가 대한민국 범위를 벗어나면 버리고, 테스트가 이를 고정한다.
- `전남광주통합특별시`·`제물포구` 처럼 카카오가 아직 모를 수 있는 이름은
  옛 명칭으로 되짚어 다시 조회한다 (D-29 / D-30).

> ⚠️ **카카오 키는 2종이다.** 이 ETL 은 **REST API 키**, 안드로이드 지도 SDK 는
> **네이티브 앱 키**를 쓴다. 바꿔 넣으면 401 이 난다.

### 아직 남은 것

- `localdata_cd` — **LOCALDATA 인증키 필요** (localdata.go.kr, 공공데이터포털과 별개)

### 인증키 함정

- 공공데이터포털은 **계정당 인증키 1개**다. 데이터별 '활용신청'을 걸면 그 키로 전부 열린다.
- **Decoding 키를 쓴다.** Encoding 키(`%2F` 가 보이는 쪽)를 넣으면 이중 인코딩으로
  `SERVICE_KEY_IS_NOT_REGISTERED_ERROR` 가 난다. `config.normalize_service_key()` 가
  `%` 를 보면 자동으로 되돌리지만, 애초에 Decoding 키를 넣는 편이 낫다.
- `shelterInfo_v2`(동물보호센터)는 구조동물(15098931)과 **다른 데이터셋**이라 별도 활용신청이 필요하다.

## 테스트

```bash
python -m unittest discover -s tests -v
```

의존성 설치 없이 돌아간다. `tests/fixtures/sample_ldong.txt` 는 실제 파일에서
까다로운 경우(리·폐지·세종·우산 행)만 추린 축소판이다.
