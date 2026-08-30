"""환경 설정 로딩.

.env 는 etl/ 바로 아래에 둔다 (.env.example 참고).
GitHub Actions 에서는 .env 없이 환경변수(secrets)로 주입된다.
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from pathlib import Path

log = logging.getLogger(__name__)

try:
    from dotenv import load_dotenv
except ImportError:  # 의존성 설치 전에도 --dry-run 은 돌아가야 한다
    def load_dotenv(*_args, **_kwargs) -> bool:
        return False

ETL_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ETL_ROOT / "data"

load_dotenv(ETL_ROOT / ".env")


class ConfigError(RuntimeError):
    """필수 환경변수가 없을 때."""


@dataclass(frozen=True)
class Settings:
    supabase_url: str
    supabase_service_role_key: str


def normalize_url(url: str) -> str:
    """프로젝트 URL 만 남긴다.

    대시보드에서 REST 엔드포인트(.../rest/v1)까지 복사해 오기 쉬운데, 클라이언트가
    거기에 /rest/v1 을 다시 붙이면서 PGRST125(Invalid path) 로 실패한다.
    """
    url = url.strip().strip('"').strip("'").rstrip("/")
    for suffix in ("/rest/v1", "/rest", "/auth/v1", "/storage/v1"):
        if url.endswith(suffix):
            url = url[: -len(suffix)]
            log.warning("SUPABASE_URL 에서 '%s' 를 떼어냈습니다 → %s", suffix, url)
    return url


def load_settings() -> Settings:
    url = normalize_url(os.getenv("SUPABASE_URL", ""))
    key = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "").strip().strip('"').strip("'")

    missing = [n for n, v in (("SUPABASE_URL", url), ("SUPABASE_SERVICE_ROLE_KEY", key)) if not v]
    if missing:
        raise ConfigError(
            "환경변수가 없습니다: "
            + ", ".join(missing)
            + f"\n  → {ETL_ROOT / '.env'} 를 만들고 값을 채우세요 (.env.example 참고)."
            + "\n  → 키 없이 파싱 결과만 확인하려면 --dry-run 을 쓰세요."
        )
    return Settings(supabase_url=url, supabase_service_role_key=key)
