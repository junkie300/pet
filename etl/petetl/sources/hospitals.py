# -*- coding: utf-8 -*-
"""1단계 · places(category='hospital') — 행안부 동물병원 조회서비스(15154952).

응답 필드와 함정은 `etl/docs/15154952-동물병원-응답스키마.md` 에 정리해 두었다.
요약하면 셋이다.

1. **좌표가 경위도가 아니다.** EPSG:5174 평면직각좌표다. 카카오 지오코딩 대비
   오차 1m 이내로 검산했다(EPSG:2097 은 321m 어긋난다). 빈 값인 행도 있다.
2. **읍면동은 코드가 아니라 주소로 배정한다.** `OPN_ATMY_GRP_CD` 는 시군구까지만 가리킨다.
   D-31 과 같은 판단이다.
3. **폐업을 걸러야 한다.** `SALS_STTS_CD` 가 `01` 인 것만 영업이다.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

from .. import publicapi
from ..config import load_data_go_kr_key
from ..db import upsert
from ..synclog import SyncRun
from .mapping import PAGE, select_all

log = logging.getLogger(__name__)

# places.source — 화면의 출처 표기에 쓰인다. 원본은 지방행정인허가데이터(LOCALDATA)다.
SOURCE = "localdata"
# sync_logs.source — 어떤 ETL 이 돌았는지 구분한다. places.source 와 같은 값을 쓰면
# 지역코드 매핑(localdata_code)과 섞여 이력을 못 읽는다.
SYNC_SOURCE = "animal_hospital"
DATASET = "15154952"
TABLE = "places"
CATEGORY = "hospital"

URL = "https://apis.data.go.kr/1741000/animal_hospitals/info"
ROWS_PER_PAGE = 100  # 1000 을 넣어도 100 으로 잘린다 (실측).

# LOCALDATA 평면직각좌표. 반드시 5174 다 — 2097 은 겉보기엔 비슷하지만 321m 어긋난다.
SOURCE_CRS = "EPSG:5174"

# 실제 평면좌표는 10만 단위다. 이보다 작으면 좌표가 아니라 빈 값으로 본다.
MIN_PLANE_COORD = 1000.0

# 영업상태코드 -> places.status. 코드표는 etl/docs/ 엑셀 '2. 영업상태코드' 시트.
STATUS_MAP = {
    "01": "open",       # 영업/정상
    "02": "suspended",  # 휴업
    "03": "closed",     # 폐업
    "04": "closed",     # 취소/말소/만료/정지/중지
    "05": "closed",     # 제외/삭제/전출
    "06": "closed",     # 기타
}

# D-29. regions 는 통합 후 이름을 쓰는데 인허가 주소는 통합 전 이름으로 온다.
# mapping.py 의 예외표와 **방향이 반대**다 (저기는 우리 이름 -> 외부 이름).
ADDR_SIDO_ALIAS = {
    "광주광역시": "전남광주통합특별시",
    "전라남도": "전남광주통합특별시",
}


# --- 좌표 -------------------------------------------------------------------

def make_transformer():
    from pyproj import Transformer

    return Transformer.from_crs(SOURCE_CRS, "EPSG:4326", always_xy=True)


def to_latlng(transformer, x: str | None, y: str | None) -> tuple[float, float] | None:
    """평면직각좌표 -> (위도, 경도). 빈 값이거나 한반도 밖이면 None."""
    if not x or not y:
        return None
    try:
        easting, northing = float(x), float(y)
    except (ValueError, TypeError):
        return None
    # '0' 은 좌표가 아니라 '값 없음'이다. 실제 값은 10만 단위인데, (0,0) 은 하필
    # 제주 남서 해상(33.48, 124.85)으로 변환돼 아래 범위 검사를 그냥 통과한다.
    if abs(easting) < MIN_PLANE_COORD or abs(northing) < MIN_PLANE_COORD:
        return None
    lng, lat = transformer.transform(easting, northing)
    # 변환식을 잘못 잡으면 조용히 엉뚱한 좌표가 들어간다. 범위로 한 번 막는다.
    if not (33.0 <= lat <= 38.7 and 124.5 <= lng <= 132.0):
        return None
    return lat, lng


# --- 읍면동 배정 -------------------------------------------------------------

def build_region_index(client) -> dict[str, str]:
    """{읍면동 full_name: code}. 주소 앞부분과 그대로 맞춰 보기 위한 색인."""
    rows = select_all(client, "code,full_name", level=3)
    return {row["full_name"]: row["code"] for row in rows}


def match_region(address: str | None, index: dict[str, str]) -> str | None:
    """지번주소 앞부분을 읍면동 이름과 맞춘다.

    토큰 수가 지역마다 다르다.
        4개  충청북도 청주시 흥덕구 송절동 679      (일반구가 있는 시)
        3개  경기도 광명시 광명동 155-3
        2개  세종특별자치시 아름동 1362             (시군구가 없다)
    """
    tokens = (address or "").split()
    if not tokens:
        return None
    tokens[0] = ADDR_SIDO_ALIAS.get(tokens[0], tokens[0])
    for size in (4, 3, 2):
        if len(tokens) >= size:
            code = index.get(" ".join(tokens[:size]))
            if code:
                return code
    return None


def lookup_kakao(
    queries: list[str], codes: set[str], kakao_key: str
) -> tuple[str | None, tuple[float, float] | None]:
    """주소 하나를 카카오에 물어 (법정동코드, 위경도) 를 돌려준다.

    두 가지를 한 번의 호출로 해결한다.

    - **폐지된 법정동**: 인허가 주소에는 이미 폐지된 동이 남아 있다
      (화성시 동탄구 오산동 → 여울동). 폐지분은 regions 에 없어 이름 대조로는 영영 못 맞춘다.
      카카오는 `b_code` 로 현행 법정동코드를 준다.
    - **빈 좌표**: `CRD_INFO_X` 가 비어 있는 행이 10건 중 1건꼴이다. 주소는 있으므로 지오코딩한다.

    지번과 도로명을 순서대로 시도한다. 인천 중구(→제물포구)·화성시 구 개편처럼
    **지번 주소 전체가 옛 체계라 카카오도 못 읽는** 경우가 있는데, 그때 도로명이 통하기도 한다.
    """
    from .coords import search_address

    region_code: str | None = None
    latlng: tuple[float, float] | None = None
    for query in queries:
        if not query:
            continue
        for document in search_address(kakao_key, query):
            if region_code is None:
                b_code = ((document or {}).get("address") or {}).get("b_code")
                if b_code and b_code in codes:
                    region_code = b_code
            if latlng is None:
                try:
                    latlng = (float(document["y"]), float(document["x"]))
                except (KeyError, TypeError, ValueError):
                    pass
            if region_code and latlng:
                return region_code, latlng
    return region_code, latlng


# --- 변환 -------------------------------------------------------------------

def to_place(item: dict, region_code: str | None, latlng: tuple[float, float] | None) -> dict:
    lat, lng = latlng if latlng else (None, None)
    status_code = (item.get("SALS_STTS_CD") or "").strip()
    return {
        "category": CATEGORY,
        "source": SOURCE,
        "source_id": (item.get("MNG_NO") or "").strip(),
        "name": (item.get("BPLC_NM") or "").strip(),
        "tel": (item.get("TELNO") or "").strip() or None,
        "address_road": (item.get("ROAD_NM_ADDR") or "").strip() or None,
        "address_jibun": (item.get("LOTNO_ADDR") or "").strip() or None,
        "lat": lat,
        "lng": lng,
        "region_code": region_code,
        # 모르는 코드를 open 으로 두면 폐업이 지도에 남는다. 모르면 닫힌 것으로 본다.
        "status": STATUS_MAP.get(status_code, "closed"),
        "extra": {
            "dataset": DATASET,
            "sales_status": (item.get("SALS_STTS_NM") or "").strip(),
            "detail_status": (item.get("DTL_SALS_STTS_NM") or "").strip(),
            "license_date": (item.get("LCPMT_YMD") or "").strip(),
            "closed_date": (item.get("CLSBIZ_YMD") or "").strip(),
            "zip": (item.get("ROAD_NM_ZIP") or "").strip(),
            "opn_atmy_grp_cd": (item.get("OPN_ATMY_GRP_CD") or "").strip(),
        },
        "source_updated_at": _to_timestamp(item.get("DAT_UPDT_PNT")),
        "synced_at": datetime.now(timezone.utc).isoformat(),
    }


def _to_timestamp(value: str | None) -> str | None:
    """'2026-09-04 21:02:00' -> ISO8601. 형식이 어긋나면 조용히 버린다."""
    text = (value or "").strip()
    if not text:
        return None
    try:
        return datetime.strptime(text, "%Y-%m-%d %H:%M:%S").replace(
            tzinfo=timezone.utc
        ).isoformat()
    except ValueError:
        return None


# --- 수집 -------------------------------------------------------------------

def fetch_all(key: str, limit: int | None = None) -> list[dict]:
    """전체 목록을 페이지 단위로 받는다."""
    rows: list[dict] = []
    page = 1
    while True:
        body = publicapi.get(URL, key, pageNo=page, numOfRows=ROWS_PER_PAGE, returnType="json")
        batch = publicapi.items(body)
        rows.extend(batch)
        total = int(body.get("totalCount") or 0)
        if page == 1:
            log.info("총 %d건 (페이지당 %d)", total, ROWS_PER_PAGE)
        if page % 20 == 0 or len(batch) < ROWS_PER_PAGE:
            log.info("  %d/%d 수집", len(rows), total)
        if limit and len(rows) >= limit:
            return rows[:limit]
        if len(batch) < ROWS_PER_PAGE or len(rows) >= total:
            return rows
        page += 1


# --- 실행 -------------------------------------------------------------------

def build_places(client, items_: list[dict], kakao_key: str | None) -> tuple[list[dict], dict]:
    index = build_region_index(client)
    codes = set(index.values())
    transformer = make_transformer()

    places: list[dict] = []
    stats = {
        "지역 이름매칭": 0, "지역 카카오보완": 0, "지역 미배정": 0,
        "좌표 원본": 0, "좌표 카카오보완": 0, "좌표 없음": 0,
        "폐업·휴업": 0, "영업중인데 지역 미배정": 0,
    }
    unresolved: list[str] = []

    for item in items_:
        address = (item.get("LOTNO_ADDR") or "").strip()
        region_code = match_region(address, index)
        if region_code:
            stats["지역 이름매칭"] += 1

        latlng = to_latlng(transformer, item.get("CRD_INFO_X"), item.get("CRD_INFO_Y"))
        if latlng:
            stats["좌표 원본"] += 1

        # 지역이든 좌표든 빠진 게 있으면 카카오에 한 번만 물어 둘 다 채운다.
        road = (item.get("ROAD_NM_ADDR") or "").strip().split(",")[0]
        if (region_code is None or latlng is None) and (address or road) and kakao_key:
            found_code, found_latlng = lookup_kakao([address, road], codes, kakao_key)
            if region_code is None and found_code:
                region_code = found_code
                stats["지역 카카오보완"] += 1
            if latlng is None and found_latlng:
                latlng = found_latlng
                stats["좌표 카카오보완"] += 1

        if not region_code:
            stats["지역 미배정"] += 1
            if len(unresolved) < 20:
                unresolved.append(address or "(주소 없음)")
        if latlng is None:
            stats["좌표 없음"] += 1

        place = to_place(item, region_code, latlng)
        if place["status"] != "open":
            stats["폐업·휴업"] += 1
        elif region_code is None:
            # 이것만이 실사용에 영향을 준다. 영업중인데 지역이 없으면 앱에서 안 보인다.
            stats["영업중인데 지역 미배정"] += 1
        if not place["source_id"] or not place["name"]:
            continue  # upsert 기준이 없는 행은 담지 않는다
        places.append(place)

    if unresolved:
        log.warning("지역을 배정하지 못한 주소 (앞 %d건):", len(unresolved))
        for address in unresolved:
            log.warning("  %s", address)
    return places, stats


def run(client=None, dry_run: bool = False, limit: int | None = None) -> list[dict]:
    if client is None:
        raise RuntimeError("hospitals 는 regions 를 읽어야 하므로 DB 연결이 필요합니다.")

    key = load_data_go_kr_key()
    try:
        from ..config import load_kakao_rest_key

        kakao_key = load_kakao_rest_key()
    except Exception:  # 카카오 키가 없어도 이름 매칭만으로 99% 는 배정된다
        kakao_key = None
        log.warning("카카오 키가 없어 폐지된 법정동 주소는 배정하지 못합니다.")

    items_ = fetch_all(key, limit=limit)
    places, stats = build_places(client, items_, kakao_key)

    log.info("변환 %d건 — %s", len(places), " · ".join(f"{k} {v}" for k, v in stats.items()))

    if dry_run:
        log.info("dry-run: DB 에 쓰지 않고 종료합니다.")
        return places

    with SyncRun(client, SYNC_SOURCE) as run_log:
        run_log.add(upsert(client, TABLE, places, on_conflict="source,source_id", chunk_size=PAGE // 2))
        log.info("총 %d행 반영", run_log.rows_upserted)
    return places
