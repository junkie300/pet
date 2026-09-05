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
    ("DATA_GO_KR_KEY", "mapping — APMS·TourAPI 지역코드"),
    ("KAKAO_REST_API_KEY", "coords — 읍면동 중심좌표"),
    ("LOCALDATA_API_KEY", "localdata_cd · 1·2단계 동물병원/미용"),
]

# (컬럼, 라벨, 모수 level) — level 이 None 이면 regions 전체가 모수다.
# 중심좌표는 읍면동에만 채운다. 모수를 전체(5,339)로 잡으면 시도·시군구 272행 때문에
# 다 채워도 영원히 "진행중" 으로 보인다.
COLUMNS = [
    ("apms_upr_cd", "APMS 시도", None),
    ("apms_org_cd", "APMS 시군구", None),
    ("tour_area_cd", "TourAPI area", None),
    ("tour_sigungu_cd", "TourAPI sigungu", None),
    ("localdata_cd", "LOCALDATA", None),
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
    if not os.getenv("KAKAO_REST_API_KEY", "").strip():
        lines.append("  · 카카오 REST API 키를 .env 에 넣으면 → python run.py coords --limit 50")
    else:
        lines.append("  · python run.py coords --limit 50   (소량 확인 후 python run.py coords)")
    if not os.getenv("LOCALDATA_API_KEY", "").strip():
        lines.append("  · LOCALDATA 키를 받으면 → localdata_cd 매핑 모듈 작성")
    lines.append("  · 자세한 순서는 README.md '다음에 할 일'")
    return "\n".join(lines)
