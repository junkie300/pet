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

### 아직 남은 것 (인증키 필요)

`regions` 의 아래 컬럼은 비어 있다. 인증키가 나오면 소스별 매핑 모듈로 채운다.

- `localdata_cd` — LOCALDATA 지역코드
- `apms_upr_cd` / `apms_org_cd` — 국가동물보호정보시스템 시도·시군구
- `tour_area_cd` / `tour_sigungu_cd` — TourAPI areaCode·sigunguCode
- `center_lat` / `center_lng` — 읍면동 중심좌표

## 테스트

```bash
python -m unittest discover -s tests -v
```

의존성 설치 없이 돌아간다. `tests/fixtures/sample_ldong.txt` 는 실제 파일에서
까다로운 경우(리·폐지·세종·우산 행)만 추린 축소판이다.
