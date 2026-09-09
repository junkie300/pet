# -*- coding: utf-8 -*-
"""`python run.py status` — 지금 어디까지 왔는지 한 화면에 보여준다.

작업을 며칠 쉬었다 돌아왔을 때 가장 먼저 실행하는 명령이다.
"무엇이 채워졌고, 무엇이 왜 막혀 있는지"를 답한다.

비밀값은 절대 출력하지 않는다. 있고 없고만 표시한다.
"""

from __future__ import annotations

import os
import unicodedata

from .config import normalize_service_key
from .db import jwt_role

LEVEL_NAMES = {1: "시도", 2: "시군구", 3: "읍면동"}


def _width(text: str) -> int:
    """터미널에서 차지하는 칸 수. 한글·한자는 두 칸이다."""
    return sum(2 if unicodedata.east_asian_width(ch) in "WF" else 1 for ch in text)


def _pad(text: str, width: int) -> str:
    """한글이 섞여도 열이 맞도록 채운다. str.ljust 는 글자 수로 세어서 어긋난다."""
    return text + " " * max(0, width - _width(text))

# (환경변수, 없으면 무엇이 막히는가)
ENV_KEYS = [
    ("SUPABASE_URL", "전부 — DB 에 접속할 수 없다"),
    ("SUPABASE_SERVICE_ROLE_KEY", "전부 — 쓰기가 RLS 에 막힌다"),
    ("DATA_GO_KR_KEY", "mapping · hospitals · grooming — 공공데이터포털 전부"),
    ("KAKAO_REST_API_KEY", "coords — 읍면동 중심좌표"),
]
# ⚠️ LOCALDATA_API_KEY 는 없앴다. **LOCALDATA(localdata.go.kr) 는 2026-04-16 에 닫혔고**
# 인허가 데이터는 공공데이터포털로 옮겨졌다 (D-93). 우리가 쓰는 동물병원(15154952)은
# 처음부터 공공데이터포털이었고, 2단계 미용업(15154944)도 같은 키로 부른다.

# (컬럼, 라벨, 모수 level) — level 이 None 이면 regions 전체가 모수다.
# 중심좌표는 읍면동에만 채운다. 모수를 전체(5,339)로 잡으면 시도·시군구 272행 때문에
# 다 채워도 영원히 "진행중" 으로 보인다.
COLUMNS = [
    ("apms_upr_cd", "APMS 시도", None),
    ("apms_org_cd", "APMS 시군구", None),
    ("tour_area_cd", "TourAPI area", None),
    ("tour_sigungu_cd", "TourAPI sigungu", None),
    # 컬럼 이름은 그대로 두되 라벨은 바꿨다 — LOCALDATA 라는 사이트가 이제 없다 (D-93).
    ("localdata_cd", "자치단체코드", None),
    ("center_lat", "중심좌표", 3),
]


def _count(client, **filters) -> int:
    query = client.table("regions").select("code", count="exact")
    for key, value in filters.items():
        if key.endswith("__notnull"):
            query = query.not_.is_(key[: -len("__notnull")], "null")
        elif key.endswith("__isnull"):
            query = query.is_(key[: -len("__isnull")], "null")
        else:
            query = query.eq(key, value)
    return query.limit(1).execute().count or 0


def _places_count(client, **filters) -> int:
    query = client.table("places").select("id", count="exact")
    for key, value in filters.items():
        if key.endswith("__notnull"):
            query = query.not_.is_(key[: -len("__notnull")], "null")
        elif key.endswith("__isnull"):
            query = query.is_(key[: -len("__isnull")], "null")
        else:
            query = query.eq(key, value)
    return query.limit(1).execute().count or 0


def _places_report(client) -> list[str]:
    """1단계 이후 상태. places 가 비어 있으면 '미착수' 한 줄로 끝낸다."""
    total = _places_count(client)
    lines = ["", "=== places 적재 ==="]
    if not total:
        lines.append("  아직 없음 → python run.py hospitals")
        return lines

    lines.append(f"  전체 {total:,}행")
    for category, label in (("hospital", "동물병원"), ("grooming", "미용"),
                            ("restaurant", "식당"), ("tour", "관광"),
                            ("wildlife_center", "야생동물구조센터")):
        n = _places_count(client, category=category)
        if not n:
            continue
        open_n = _places_count(client, category=category, status="open")
        # 실사용에 영향을 주는 것은 '영업중인데 지역이 없는' 행뿐이다 (D-51).
        orphan = _places_count(client, category=category, status="open", region_code__isnull=None)
        no_geo = _places_count(client, category=category, status="open", lat__isnull=None)
        lines.append(
            f"  {_pad(label, 18)}{n:>7,}행 · 영업중 {open_n:,}"
            f" · 지역 미배정 {orphan}"
            f" · 좌표 없음 {no_geo}"
        )
    return lines


def _env_report() -> list[str]:
    lines = ["=== 환경 (.env) ==="]
    for name, blocked in ENV_KEYS:
        raw = os.getenv(name, "").strip()
        if not raw:
            lines.append(f"  {_pad(name, 28)}없음    → 막힘: {blocked}")
            continue
        note = ""
        if name == "SUPABASE_SERVICE_ROLE_KEY":
            role = jwt_role(raw)
            note = f"({role})" if role else "(JWT 아님 — legacy service_role 키를 쓸 것)"
        elif name == "DATA_GO_KR_KEY" and "%" in raw:
            note = "(Encoding 키 — 실행 시 자동으로 Decoding 으로 바꿔 쓴다)"
        elif name == "DATA_GO_KR_KEY":
            note = f"({len(normalize_service_key(raw))}자)"
        lines.append(f"  {_pad(name, 28)}있음    {note}")
    return lines


def report(client) -> str:
    lines: list[str] = []
    lines += _env_report()

    lines.append("")
    lines.append("=== regions 적재 ===")
    total = _count(client)
    per_level = {lv: _count(client, level=lv) for lv in (1, 2, 3)}
    lines.append(
        "  " + " · ".join(f"{LEVEL_NAMES[lv]} {per_level[lv]:,}" for lv in (1, 2, 3))
        + f"   (총 {total:,}행)"
    )
    orphan = (
        client.table("regions").select("code", count="exact")
        .gt("level", 1).is_("parent_code", "null").limit(1).execute().count or 0
    )
    lines.append(f"  계층 끊김(parent_code 없는 시군구·읍면동): {orphan}"
                 + ("  ← 확인 필요" if orphan else "  OK"))

    lines.append("")
    lines.append("=== 0단계 외부 코드 · 좌표 ===")
    for column, label, level in COLUMNS:
        scope = {"level": level} if level is not None else {}
        denominator = _count(client, **scope) if level is not None else total
        filled = _count(client, **scope, **{f"{column}__notnull": None})
        # 모수 전체가 대상인 컬럼은 시도·세종 때문에 최대 50행이 비는 것이 정상이다(아래 ※).
        slack = 0 if level is not None else 50
        mark = ("완료" if filled >= denominator - slack
                else "미착수" if filled == 0 else "진행중")
        suffix = "  (읍면동만)" if level == 3 else ""
        lines.append(f"  {_pad(label, 18)}{filled:>5,} / {denominator:,}   {mark}{suffix}")
    lines.append("  ※ apms_org_cd 의 미충족 50 = 시도 16 + 세종 34 (세종은 시군구가 없다). 정상이다.")

    lines += _places_report(client)

    lines.append("")
    lines.append("=== 최근 ETL (sync_logs) ===")
    rows = (
        client.table("sync_logs")
        .select("source,status,finished_at,rows_upserted")
        .order("id", desc=True).limit(5).execute().data
    )
    if not rows:
        lines.append("  기록 없음")
    for row in rows:
        finished = (row.get("finished_at") or "")[:16].replace("T", " ")
        lines.append(
            f"  {_pad(row['source'], 15)}{_pad(row['status'], 9)}{_pad(finished, 18)}"
            f" {row.get('rows_upserted') or 0:,}행"
        )

    lines.append("")
    lines.append("=== 다음 명령 ===")
    lines += _next_steps(client)
    lines.append("  · 자세한 순서는 README.md '다음에 할 일'")
    return "\n".join(lines)


def _next_steps(client) -> list[str]:
    """지금 상태에서 실제로 막혀 있는 것만 알려준다. 끝난 일을 계속 권하지 않는다."""
    steps: list[str] = []

    if _count(client, level=3, center_lat__isnull=None):
        steps.append("  · python run.py coords          (중심좌표가 빈 읍면동이 남아 있다)")
    # 통합시도 1행은 코드가 둘로 갈려 비는 것이 정상이다 (D-48).
    if _count(client, localdata_cd__isnull=None) > 1:
        steps.append("  · python run.py localdata       (자치단체코드가 빈 행이 있다)")
    if not _places_count(client, category="hospital"):
        steps.append("  · python run.py hospitals       (동물병원이 아직 없다)")

    if not steps:
        steps.append("  · ETL 은 할 일이 없다. 1단계 앱 화면은 다 찼다 (D-91·D-92 는 실기기 확인만 남았다)")
        steps.append("  · 2단계(미용)는 공공데이터포털 15154944 활용신청(자동승인)이면 시작할 수 있다 (D-93)")
    return steps
