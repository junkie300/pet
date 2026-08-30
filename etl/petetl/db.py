"""Supabase 클라이언트와 공용 upsert 헬퍼.

supabase 패키지는 get_client() 안에서만 import 한다.
그래야 의존성을 설치하지 않은 상태에서도 --dry-run 으로 파싱 결과를 확인할 수 있다.
"""

from __future__ import annotations

import base64
import json
import logging
from typing import Any, Iterable, Sequence

from .config import ConfigError, load_settings

log = logging.getLogger(__name__)

DEFAULT_CHUNK_SIZE = 500


def jwt_role(key: str) -> str | None:
    """legacy JWT 키에서 role 클레임을 읽는다. JWT 가 아니면 None."""
    parts = key.split(".")
    if len(parts) != 3:
        return None  # sb_secret_... 같은 새 형식은 판별할 수 없다
    payload = parts[1] + "=" * (-len(parts[1]) % 4)
    try:
        return json.loads(base64.urlsafe_b64decode(payload)).get("role")
    except Exception:
        return None


def get_client() -> Any:
    """service_role 키로 접속한다. RLS 를 우회하므로 ETL 에서만 쓴다.

    anon 키를 넣으면 SELECT 는 되고 INSERT 만 RLS 에 막혀서, 한참 뒤에야
    '42501 violates row-level security policy' 로 터진다. 여기서 미리 잡는다.
    """
    from supabase import create_client

    settings = load_settings()

    role = jwt_role(settings.supabase_service_role_key)
    if role is not None and role != "service_role":
        raise ConfigError(
            f"SUPABASE_SERVICE_ROLE_KEY 에 role='{role}' 키가 들어 있습니다. ETL 은 service_role 키가 필요합니다."
            "\n  → Supabase 대시보드 > Project Settings > API Keys > Legacy API keys 에서"
            "\n    'service_role' (secret 이라고 표시된 쪽) 키를 복사하세요."
            "\n  → 'anon' (public) 키는 안드로이드 앱이 쓸 키입니다."
        )

    return create_client(settings.supabase_url, settings.supabase_service_role_key)


def chunked(rows: Sequence[dict[str, Any]], size: int) -> Iterable[Sequence[dict[str, Any]]]:
    for i in range(0, len(rows), size):
        yield rows[i : i + size]


def upsert(
    client: Any,
    table: str,
    rows: Sequence[dict[str, Any]],
    on_conflict: str,
    chunk_size: int = DEFAULT_CHUNK_SIZE,
) -> int:
    """rows 를 나눠서 upsert 하고 반영된 행 수를 돌려준다.

    한 번에 수만 건을 보내면 PostgREST 가 타임아웃되므로 반드시 쪼갠다.
    """
    total = 0
    batch_count = -(-len(rows) // chunk_size) if rows else 0
    for i, batch in enumerate(chunked(rows, chunk_size), start=1):
        response = client.table(table).upsert(list(batch), on_conflict=on_conflict).execute()
        n = len(response.data or [])
        total += n
        log.info("  %s upsert %d/%d 배치 — %d행", table, i, batch_count, n)
    return total
