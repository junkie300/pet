# -*- coding: utf-8 -*-
"""적재 직전의 안전장치 — "성공했다는 거짓말"을 막는다.

upsert 는 지우지 않는다. 그래서 원본이 0건을 돌려줘도 ETL 은 조용히 끝나고
`sync_logs` 에 **success 가 찍힌다**. DB 에는 옛 데이터가 그대로 남아 있는데
앱은 그 행을 읽어 "기준일"을 오늘로 표시한다 (spec.md §4) — **낡은 데이터에
새 날짜가 붙는다.** 사람이 보고 있으면 로그에서 알아채지만, cron 으로 매일
돌기 시작하면 며칠씩 모른 채 지나간다.

그래서 여기서 **터뜨린다.** SyncRun 안에서 올라가므로 sync_logs 에는 failed 로
남고, run.py 는 0이 아닌 코드로 끝나 워크플로가 실패한다(= 이슈 알림).

판정은 둘뿐이다.

1. **0건이면 언제나 실패다.** 아무것도 싣지 않는 적재가 옳은 경우는 없다.
2. **직전 성공의 절반 밑으로 줄면 실패다.** 원본이 진짜로 줄어드는 일도 있으므로
   `--allow-shrink` 로 넘길 수 있다. 자동으로 판단하지 않는다 — 사람이 정한다.

첫 실행에는 비교 대상이 없다. 그때는 1번만 본다.
"""

from __future__ import annotations

import logging
from typing import Any

log = logging.getLogger(__name__)

# 직전 성공 대비 이 비율 밑으로 줄면 사고로 본다.
SHRINK_RATIO = 0.5


class SanityError(RuntimeError):
    """적재하면 안 되는 상태. 메시지가 그대로 sync_logs.error 와 이슈 본문에 실린다."""


def last_success_rows(client: Any, source: str) -> int | None:
    """그 source 의 마지막 성공이 몇 행을 실었는지. 기록이 없으면 None.

    읽기에 실패해도 ETL 을 멈추지 않는다 — 안전장치가 본작업을 무너뜨리면 안 된다.
    """
    try:
        rows = (
            client.table("sync_logs")
            .select("rows_upserted")
            .eq("source", source)
            .eq("status", "success")
            .order("id", desc=True)
            .limit(1)
            .execute()
            .data
        )
    except Exception:
        log.exception("sync_logs 를 읽지 못해 증감 비교를 건너뜁니다 (source=%s)", source)
        return None
    if not rows:
        return None
    return int(rows[0].get("rows_upserted") or 0)


def check_row_count(
    count: int,
    previous: int | None,
    *,
    source: str,
    allow_shrink: bool = False,
    ratio: float = SHRINK_RATIO,
) -> None:
    """적재해도 되는 건수인지 본다. 아니면 SanityError 를 올린다."""
    if count <= 0:
        raise SanityError(
            f"[{source}] 실을 행이 0건입니다. 원본이 비었거나 응답 형식이 바뀐 것입니다."
            " DB 는 그대로 두고 멈춥니다 — 그냥 두면 낡은 데이터에 오늘 날짜가 붙습니다."
        )

    if previous is None or previous <= 0:
        return  # 첫 실행. 비교할 것이 없다.

    floor = previous * ratio
    if count >= floor:
        return

    message = (
        f"[{source}] 행 수가 직전 성공의 절반 밑으로 줄었습니다 — {previous:,}행 → {count:,}행"
        f" ({count / previous:.0%}). 원본이 정말 줄었다면 --allow-shrink 로 다시 실행하세요."
    )
    if allow_shrink:
        log.warning("%s (--allow-shrink 로 넘어갑니다)", message)
        return
    raise SanityError(message)


def guard(
    client: Any,
    source: str,
    count: int,
    *,
    allow_shrink: bool = False,
) -> None:
    """`check_row_count` 에 직전 성공 건수를 물어다 붙인 것. 호출부는 이것만 쓴다."""
    previous = last_success_rows(client, source)
    if previous:
        log.info("직전 성공 %s행 대비 이번 %s행", f"{previous:,}", f"{count:,}")
    check_row_count(count, previous, source=source, allow_shrink=allow_shrink)
