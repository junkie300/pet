"""환경 설정 로딩.

.env 는 etl/ 바로 아래에 둔다 (.env.example 참고).
GitHub Actions 에서는 .env 없이 환경변수(secrets)로 주입된다.
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote

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


def normalize_service_key(key: str) -> str:
    """공공데이터포털 인증키를 Decoding 형태로 맞춘다.

    포털은 같은 키를 Encoding/Decoding 두 벌로 보여준다. Encoding 쪽을 그대로 쓰면
    requests 가 한 번 더 인코딩해서 '%2B' 가 '%252B' 가 되고,
    SERVICE_KEY_IS_NOT_REGISTERED_ERROR 로 떨어진다.

    키는 base64(A-Za-z0-9+/=)라 '%' 가 들어갈 수 없다. 있으면 Encoding 키다.
    """
    key = key.strip().strip('"').strip("'")
    if "%" in key:
        key = unquote(key)
        log.warning("DATA_GO_KR_KEY 가 Encoding 키였습니다 — Decoding 형태로 바꿔서 씁니다.")
    return key


def load_data_go_kr_key() -> str:
    """공공데이터포털 인증키. 계정당 1개이며 APMS·TourAPI 가 공유한다."""
    key = normalize_service_key(os.getenv("DATA_GO_KR_KEY", ""))
    if not key:
        raise ConfigError(
            "환경변수가 없습니다: DATA_GO_KR_KEY"
            "\n  → {} 에 넣으세요 (.env.example 참고).".format(ETL_ROOT / ".env")
            + "\n  → https://www.data.go.kr 마이페이지 > 오픈API > 인증키 발급현황의"
            "\n    'Decoding' 키를 씁니다."
        )
    return key


def load_kakao_rest_key() -> str:
    """카카오 로컬 API(주소→좌표) 용 REST API 키.

    안드로이드 지도 SDK 가 쓰는 '네이티브 앱 키' 와 다른 키다. 같은 앱에서 둘 다 발급된다.
    """
    key = os.getenv("KAKAO_REST_API_KEY", "").strip().strip('"').strip("'")
    if not key:
        raise ConfigError(
            "환경변수가 없습니다: KAKAO_REST_API_KEY"
            "\n  → {} 에 넣으세요 (.env.example 참고).".format(ETL_ROOT / ".env")
            + "\n  → https://developers.kakao.com 내 애플리케이션 > 앱 키 의 'REST API 키'."
            "\n    '네이티브 앱 키'(안드로이드용)가 아닙니다."
        )
    return key


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
