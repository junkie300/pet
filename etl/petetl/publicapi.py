"""공공데이터포털(data.go.kr) 공통 호출 헬퍼.

포털 API 들은 실패해도 HTTP 200 을 주고 본문에만 오류를 담는 경우가 많다.
그래서 status_code 만 보면 빈 데이터를 정상으로 착각한다. 여기서 본문까지 검사한다.

응답 형태가 두 가지다.
    정상  {"response": {"header": {"resultCode": "00"}, "body": {...}}}
    오류  {"OpenAPI_ServiceResponse": {"cmmMsgHeader": {"errMsg": ...}}}
resultCode 는 서비스마다 "00"(APMS) 과 "0000"(TourAPI) 으로 자릿수가 다르다.

requests 는 get() 안에서만 import 한다. db.py 와 같은 이유로,
의존성 없이도 응답 판별 로직(_check/items)을 테스트할 수 있어야 한다.
"""

from __future__ import annotations

import logging
import time
from typing import Any

log = logging.getLogger(__name__)

TIMEOUT = 30
RETRIES = 3
RETRY_WAIT = 2  # 초. 포털은 순간 부하로 간헐 500 을 낸다

# 서비스마다 자릿수가 다르다 — APMS "00" · TourAPI "0000" · 행안부 동물병원 "0".
OK_CODES = {"0", "00", "0000"}


class PublicApiError(RuntimeError):
    """포털이 오류 본문을 돌려준 경우."""


def _check(payload: dict, url: str) -> dict:
    err = payload.get("OpenAPI_ServiceResponse")
    if err:
        header = err.get("cmmMsgHeader", {})
        raise PublicApiError(
            "{} → {} ({})".format(
                url,
                header.get("returnAuthMsg") or header.get("errMsg"),
                header.get("returnReasonCode"),
            )
            + _hint(header.get("errMsg", ""))
        )

    response = payload.get("response")
    if response is None:
        raise PublicApiError(f"{url} → 예상 밖의 응답 형태: {str(payload)[:200]}")

    code = str(response.get("header", {}).get("resultCode", ""))
    if code not in OK_CODES:
        message = response.get("header", {}).get("resultMsg", "")
        raise PublicApiError(f"{url} → resultCode={code} {message}" + _hint(message))

    return response.get("body") or {}


def _hint(message: str) -> str:
    if "SERVICE_KEY_IS_NOT_REGISTERED" in message:
        return (
            "\n  → 이 데이터에 '활용신청'을 하지 않았거나, Encoding 키를 넣었을 수 있습니다."
            "\n    마이페이지 > 오픈API > 인증키 발급현황에서 Decoding 키를 확인하세요."
        )
    if "LIMITED_NUMBER_OF_SERVICE_REQUESTS" in message:
        return "\n  → 일일 트래픽 한도를 넘겼습니다. 내일 다시 시도하거나 증량을 신청하세요."
    if "NO_OPENAPI_SERVICE_ERROR" in message:
        return "\n  → 엔드포인트 경로나 오퍼레이션 이름이 틀렸습니다."
    return ""


def get(url: str, service_key: str, **params: Any) -> dict:
    """포털 API 를 호출하고 body 를 돌려준다."""
    import requests

    params.setdefault("_type", "json")
    params.update(serviceKey=service_key)

    last: Exception | None = None
    for attempt in range(1, RETRIES + 1):
        try:
            response = requests.get(url, params=params, timeout=TIMEOUT)
            # 헤더에 charset 이 없어서 requests 가 ISO-8859-1 로 잡는다. 한글이 깨진다.
            response.encoding = "utf-8"
            try:
                payload = response.json()
            except ValueError:
                raise PublicApiError(f"{url} → JSON 이 아닙니다: {response.text[:200]}")
            return _check(payload, url)
        except (requests.RequestException, PublicApiError) as exc:
            last = exc
            if isinstance(exc, PublicApiError) and "SERVICE_KEY" in str(exc):
                raise  # 키 문제는 재시도해도 소용없다
            if attempt < RETRIES:
                log.warning("  호출 실패 (%d/%d) — %s초 뒤 재시도", attempt, RETRIES, RETRY_WAIT)
                time.sleep(RETRY_WAIT)
    raise last  # type: ignore[misc]


def items(body: dict) -> list[dict]:
    """body 에서 항목 목록을 꺼낸다.

    결과가 0건이면 items 가 {} 또는 "" 로 오고, 1건이면 리스트가 아니라
    단일 객체로 오는 서비스가 있다. 양쪽 다 리스트로 정규화한다.
    """
    container = body.get("items")
    if not container:
        return []
    item = container.get("item") if isinstance(container, dict) else container
    if not item:
        return []
    return item if isinstance(item, list) else [item]
