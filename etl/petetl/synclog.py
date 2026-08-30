"""sync_logs 기록.

앱은 이 테이블의 최신 success 행을 읽어 "기준일"을 표시한다 (spec.md §4).
따라서 ETL 이 성공했는지 여부는 반드시 여기에 남아야 한다.
"""

from __future__ import annotations

import logging
import traceback
from datetime import datetime, timezone
from typing import Any

log = logging.getLogger(__name__)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class SyncRun:
    """with 블록으로 감싸면 시작·종료·실패가 sync_logs 에 기록된다.

    client 가 None 이면(=dry-run) 아무것도 기록하지 않는다.
    """

    def __init__(self, client: Any | None, source: str) -> None:
        self.client = client
        self.source = source
        self.log_id: int | None = None
        self.rows_upserted = 0

    def __enter__(self) -> "SyncRun":
        if self.client is None:
            return self
        response = (
            self.client.table("sync_logs")
            .insert({"source": self.source, "started_at": _now(), "status": "running"})
            .execute()
        )
        self.log_id = (response.data or [{}])[0].get("id")
        return self

    def add(self, n: int) -> None:
        self.rows_upserted += n

    def __exit__(self, exc_type, exc, tb) -> bool:
        if self.client is None or self.log_id is None:
            return False

        if exc is None:
            patch = {"status": "success", "error": None}
        else:
            patch = {
                "status": "failed",
                "error": "".join(traceback.format_exception_only(exc_type, exc)).strip()[:2000],
            }
        patch.update(finished_at=_now(), rows_upserted=self.rows_upserted)

        try:
            self.client.table("sync_logs").update(patch).eq("id", self.log_id).execute()
        except Exception:  # 로그 기록 실패가 ETL 결과를 덮어쓰지 않도록 한다
            log.exception("sync_logs 기록 실패 (id=%s)", self.log_id)
        return False  # 예외를 삼키지 않는다
