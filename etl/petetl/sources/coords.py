# -*- coding: utf-8 -*-
"""0단계 — 읍면동 중심좌표(center_lat / center_lng) 채우기.

카카오 로컬 API 의 '주소로 좌표 변환'을 쓴다.
    GET https://dapi.kakao.com/v2/local/search/address.json?query={주소}
    헤더: Authorization: KakaoAK {REST_API_KEY}
응답의 x 가 경도(lng), y 가 위도(lat)다. **순서가 뒤집혀 있으니 주의한다.**

## 왜 공공데이터가 아니라 카카오인가

읍면동 중심좌표를 주는 공공데이터는 사실상 전부 공간정보(shapefile) 형태다.
`전국법정구역(읍면동)정보표준데이터`(15029173)도 Vworld WMS/WFS + shp 이라
별도 키와 GIS 툴체인(GDAL 등)이 필요하다. 반면 카카오 로컬 API 는
REST 키 하나로 끝나고, 그 키는 1단계 지도·지오코딩에 어차피 필요하다.

## 이름이 안 맞을 때 (D-29 / D-30)

`regions` 는 최신 법정동코드 기준이라 `전남광주통합특별시`, `제물포구` 같은 신규 명칭을 쓴다.
카카오 주소 DB 가 이를 아직 반영하지 않았을 수 있으므로, 실패하면 옛 명칭으로 되돌려
다시 물어본다. 그래도 실패하면 시도를 떼고 '시군구 읍면동'만으로 한 번 더 시도한다.

## 재실행 안전

기본적으로 **center_lat 이 비어 있는 행만** 처리한다. 중간에 끊겨도 다시 돌리면 이어서 간다.
쿼터가 걱정되면 `--limit` 으로 한 번에 처리할 개수를 제한한다.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone

from ..config import load_kakao_rest_key
from ..db import upsert
from ..synclog import SyncRun

log = logging.getLogger(__name__)

SOURCE = "kakao_coords"
TABLE = "regions"

KAKAO_URL = "https://dapi.kakao.com/v2/local/search/address.json"
TIMEOUT = 15
RETRIES = 3
PAUSE = 0.05  # 초. 연속 호출 사이 최소 간격

PAGE = 1000

# 개편으로 이름이 바뀐 곳. 카카오가 아직 옛 이름만 알 수 있다. (D-29 / D-30)
# mapping.py 의 TOUR_* 표와 값이 비슷하지만 목적이 다르다.
# 저쪽은 'TourAPI 가 쓰는 이름', 이쪽은 '실제 옛 행정구역명'이다.
LEGACY_SIDO: dict[str, tuple[str, ...]] = {
    "전남광주통합특별시": ("광주광역시", "전라남도"),
}
LEGACY_SIGUNGU: dict[tuple[str, str], tuple[str, ...]] = {
    ("인천광역시", "제물포구"): ("중구", "동구"),
    ("인천광역시", "영종구"): ("중구",),
    ("인천광역시", "서해구"): ("서구",),
    ("인천광역시", "검단구"): ("서구",),
}


# --- 질의문 만들기 ----------------------------------------------------------

def query_candidates(region: dict) -> list[str]:
    """한 읍면동에 대해 시도할 검색어들을 우선순위 순으로 만든다."""
    sido = region["sido_name"]
    sigungu = region.get("sigungu_name") or ""
    dong = region.get("dong_name") or ""
    full = region["full_name"]

    candidates = [full]

    # 1) 시도명이 바뀐 경우 옛 이름으로
    for old in LEGACY_SIDO.get(sido, ()):
        candidates.append(" ".join(x for x in (old, sigungu, dong) if x))

    # 2) 시군구명이 바뀐 경우 옛 이름으로
    for old in LEGACY_SIGUNGU.get((sido, sigungu), ()):
        candidates.append(" ".join(x for x in (sido, old, dong) if x))
        for old_sido in LEGACY_SIDO.get(sido, ()):
            candidates.append(" ".join(x for x in (old_sido, old, dong) if x))

    # 3) 시도를 떼고 '시군구 읍면동' 만으로
    if sigungu and dong:
        candidates.append(f"{sigungu} {dong}")

    # 중복 제거하되 순서는 유지한다
    seen: set[str] = set()
    ordered: list[str] = []
    for c in candidates:
        c = c.strip()
        if c and c not in seen:
            seen.add(c)
            ordered.append(c)
    return ordered


def pick_document(documents: list[dict], dong_name: str | None) -> dict | None:
    """응답 후보 중 가장 그럴듯한 것을 고른다.

    카카오는 '동'을 검색해도 그 안의 지번 주소를 여럿 돌려준다. 읍면동 이름이
    정확히 일치하는 것을 우선하고, 없으면 첫 번째를 쓴다.
    """
    if not documents:
        return None
    if dong_name:
        for doc in documents:
            address = doc.get("address") or {}
            if address.get("region_3depth_name") == dong_name:
                return doc
    return documents[0]


def to_latlng(document: dict) -> tuple[float, float] | None:
    """x=경도, y=위도. 뒤집어 쓰면 좌표가 동해 한가운데로 간다."""
    try:
        lng = float(document["x"])
        lat = float(document["y"])
    except (KeyError, TypeError, ValueError):
        return None
    # 대한민국 범위를 크게 벗어나면 버린다
    if not (33.0 <= lat <= 39.5 and 124.0 <= lng <= 132.0):
        log.warning("  범위 밖 좌표 무시: lat=%s lng=%s", lat, lng)
        return None
    return lat, lng


# --- 호출 -------------------------------------------------------------------

def search_address(key: str, query: str) -> list[dict]:
    import requests

    headers = {"Authorization": f"KakaoAK {key}"}
    for attempt in range(1, RETRIES + 1):
        response = requests.get(
            KAKAO_URL, headers=headers, params={"query": query, "size": 10}, timeout=TIMEOUT
        )
        if response.status_code == 429:  # 쿼터/속도 제한
            wait = 2 * attempt
            log.warning("  429 — %d초 대기 후 재시도 (%s)", wait, query)
            time.sleep(wait)
            continue
        if response.status_code == 401:
            raise RuntimeError(
                "카카오 REST API 키가 거부되었습니다(401)."
                "\n  → developers.kakao.com > 내 애플리케이션 > 앱 키 의 'REST API 키' 를 씁니다."
                "\n  → 네이티브 앱 키(안드로이드용)와 다른 키입니다."
            )
        response.raise_for_status()
        return response.json().get("documents") or []
    return []


COORD2REGION_URL = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json"


def coord_to_bcode(key: str, lat: float, lng: float) -> str | None:
    """좌표 -> 법정동코드. 못 찾으면 None.

    **주소가 가려진 데이터의 마지막 수단이다** (D-95). 인허가 주소는 번지가 `***` 로
    가려져 오는 경우가 있는데(미용업은 100%), 하필 `평화동*가` 처럼 **동 이름 속 숫자까지**
    가려지면 이름으로는 영영 못 맞춘다 — 1가~4가 중 무엇인지 알 길이 없기 때문이다.

    그런데 **좌표는 가려지지 않는다.** 좌표가 있으면 그것이 정답이다. 이것은 추측이 아니라
    조회다 — 카카오가 그 점을 품는 법정동을 그대로 돌려준다 (실측: `평화동*가` → `평화동1가`).
    """
    import requests

    headers = {"Authorization": f"KakaoAK {key}"}
    for attempt in range(1, RETRIES + 1):
        response = requests.get(
            COORD2REGION_URL, headers=headers, params={"x": lng, "y": lat}, timeout=TIMEOUT
        )
        if response.status_code == 429:
            wait = 2 * attempt
            log.warning("  429 — %d초 대기 후 재시도 (역지오코딩)", wait)
            time.sleep(wait)
            continue
        if response.status_code == 401:
            raise RuntimeError("카카오 REST API 키가 거부되었습니다(401).")
        if response.status_code != 200:
            return None
        for document in response.json().get("documents") or []:
            # 'B' 가 법정동, 'H' 는 행정동이다. 우리 regions 는 법정동 기준이다.
            if document.get("region_type") == "B" and document.get("code"):
                return document["code"]
        return None
    return None


def resolve(key: str, region: dict) -> tuple[float, float] | None:
    for query in query_candidates(region):
        documents = search_address(key, query)
        document = pick_document(documents, region.get("dong_name"))
        if document is None:
            continue
        latlng = to_latlng(document)
        if latlng:
            if query != region["full_name"]:
                log.info("  대체 검색어로 찾음: %s → %s", region["full_name"], query)
            return latlng
        time.sleep(PAUSE)
    return None


# --- 대상 조회 --------------------------------------------------------------

def pending(client, only_missing: bool = True, limit: int | None = None) -> list[dict]:
    """좌표를 채워야 할 읍면동을 읽는다."""
    rows: list[dict] = []
    offset = 0
    while True:
        query = (
            client.table(TABLE)
            .select("code,level,sido_name,sigungu_name,dong_name,full_name,center_lat")
            .eq("level", 3)
        )
        if only_missing:
            query = query.is_("center_lat", "null")
        page = query.order("code").range(offset, offset + PAGE - 1).execute().data
        rows.extend(page)
        if len(page) < PAGE:
            break
        offset += PAGE
        if limit and len(rows) >= limit:
            break
    return rows[:limit] if limit else rows


# --- 실행 -------------------------------------------------------------------

def run(client=None, dry_run: bool = False, limit: int | None = None,
        only_missing: bool = True) -> list[dict]:
    if client is None:
        raise RuntimeError("coords 는 regions 를 읽어야 하므로 DB 연결이 필요합니다.")

    key = load_kakao_rest_key()
    targets = pending(client, only_missing=only_missing, limit=limit)
    log.info("좌표를 채울 읍면동 %d곳", len(targets))
    if not targets:
        log.info("채울 것이 없습니다.")
        return []

    now = datetime.now(timezone.utc).isoformat()
    updates: list[dict] = []
    failed: list[str] = []

    for i, region in enumerate(targets, start=1):
        latlng = resolve(key, region)
        if latlng is None:
            failed.append(f"{region['code']} {region['full_name']}")
        else:
            lat, lng = latlng
            updates.append(
                {
                    "code": region["code"],
                    # NOT NULL 컬럼은 upsert 페이로드에 함께 실어야 한다 (mapping.py 와 같은 이유)
                    "level": 3,
                    "sido_name": region["sido_name"],
                    "full_name": region["full_name"],
                    "center_lat": lat,
                    "center_lng": lng,
                    "updated_at": now,
                }
            )
        if i % 200 == 0:
            log.info("  %d/%d 진행 (실패 %d)", i, len(targets), len(failed))
        time.sleep(PAUSE)

    log.info("좌표 확보 %d곳 · 실패 %d곳", len(updates), len(failed))
    if failed:
        log.warning("좌표를 못 찾은 곳:")
        for line in failed[:50]:
            log.warning("  %s", line)
        if len(failed) > 50:
            log.warning("  ... 외 %d곳", len(failed) - 50)

    if dry_run:
        log.info("dry-run: DB 에 쓰지 않고 종료합니다.")
        return updates

    if updates:
        with SyncRun(client, SOURCE) as run_log:
            run_log.add(upsert(client, TABLE, updates, on_conflict="code"))
            log.info("총 %d행 반영", run_log.rows_upserted)

    return updates
