# -*- coding: utf-8 -*-
"""0단계 — regions 의 외부 코드 매핑 (APMS / TourAPI).

regions 는 법정동코드가 기준이다. 외부 API 들은 각자 다른 지역코드를 쓰므로,
여기서 regions 행에 그 코드들을 채워 넣는다. 이후 단계는 regions 만 보면 된다.

    apms_upr_cd / apms_org_cd        국가동물보호정보시스템 (data.go.kr 15098931)
    tour_area_cd / tour_sigungu_cd   한국관광공사 국문 관광정보 (data.go.kr 15101578)

두 API 모두 같은 DATA_GO_KR_KEY 를 쓴다 (포털 계정당 인증키 1개).

## 매핑이 1:1 이 아닌 이유 세 가지 (실측 2026-09-02)

1. **일반구** — regions 는 '성남시 분당구'까지 내려가지만 두 API 는 '성남시'까지만 있다.
   → 일반구는 모시(母市)로 접어서 맞춘다. 결과적으로 N:1 매핑이 된다.
   경기도만 봐도 regions 47 · APMS 33 · TourAPI 31 이다.

2. **행정구역 개편을 API 들이 같은 속도로 반영하지 않는다** (DECISIONS D-29).
   APMS 는 반영했고 TourAPI 는 아직 옛 체계다.

       광주광역시 + 전라남도 -> 전남광주통합특별시    TourAPI 는 여전히 5 / 38 로 분리
       인천 중구+동구 -> 제물포구, 중구 -> 영종구     TourAPI 는 여전히 중구 / 동구
       인천 서구 -> 서해구 + 검단구                  TourAPI 는 여전히 서구

   → 아래 예외표 두 개로 손으로 잇는다. 규칙으로 풀리지 않으므로 표가 정답이다.

3. **쓰레기 행** — APMS 경상남도 하위에 orgCd=6489999 처럼 이름 없는 행이 섞여 있다.
   이름이 없으면 버린다.

읍면동(level 3)에는 부모 시군구의 코드를 그대로 내려 쓴다. 두 API 모두 읍면동 코드를
제공하지 않고, 앱은 읍면동을 고른 뒤 시군구 단위로 외부 API 를 부르기 때문이다.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

from ..config import load_data_go_kr_key
from ..db import upsert
from ..publicapi import get, items
from ..synclog import SyncRun

log = logging.getLogger(__name__)

SOURCE = "code_mapping"
TABLE = "regions"

APMS_BASE = "https://apis.data.go.kr/1543061/abandonmentPublicService_v2"
TOUR_BASE = "https://apis.data.go.kr/B551011/KorService2"

# TourAPI 는 시도명을 접미사 없이 짧게 쓴다('서울특별시' -> '서울').
# 규칙으로 못 맞추는 것만 손으로 잇는다. 값이 2개면 1:N 구간이다.
TOUR_SIDO_ALIAS: dict[str, tuple[str, ...]] = {
    "전남광주통합특별시": ("광주", "전라남도"),  # D-29
}

# TourAPI 에 아직 없는 신설 자치구 -> 그 자리에 있던 옛 자치구 이름.
TOUR_SIGUNGU_ALIAS: dict[tuple[str, str], str] = {
    ("인천광역시", "제물포구"): "중구",
    ("인천광역시", "영종구"): "중구",
    ("인천광역시", "서해구"): "서구",
    ("인천광역시", "검단구"): "서구",
}


# --- 이름 정규화 -----------------------------------------------------------

def parent_city(sigungu_name: str) -> str:
    """'성남시 분당구' -> '성남시'. 일반구는 외부 API 에 없으므로 모시로 접는다."""
    return sigungu_name.split(" ")[0] if " " in sigungu_name else sigungu_name


def tour_sido_names(sido_name: str) -> tuple[str, ...]:
    """regions 시도명에 대응하는 TourAPI 시도명 후보."""
    if sido_name in TOUR_SIDO_ALIAS:
        return TOUR_SIDO_ALIAS[sido_name]
    for suffix in ("특별시", "광역시"):
        if sido_name.endswith(suffix):
            return (sido_name, sido_name[: -len(suffix)])
    return (sido_name,)


def tour_sigungu_name(sido_name: str, sigungu_name: str) -> str:
    """TourAPI 쪽 시군구 이름. 신설 자치구는 옛 이름으로 되돌린다."""
    alias = TOUR_SIGUNGU_ALIAS.get((sido_name, sigungu_name))
    return alias if alias else parent_city(sigungu_name)


# --- 외부 코드 수집 ---------------------------------------------------------

def fetch_apms_sido(key: str) -> dict[str, str]:
    body = get(f"{APMS_BASE}/sido_v2", key, numOfRows=200, pageNo=1)
    return {i["orgdownNm"]: i["orgCd"] for i in items(body) if i.get("orgdownNm")}


def fetch_apms_sigungu(key: str, upr_cd: str) -> dict[str, str]:
    body = get(f"{APMS_BASE}/sigungu_v2", key, numOfRows=300, pageNo=1, upr_cd=upr_cd)
    result: dict[str, str] = {}
    for i in items(body):
        name = i.get("orgdownNm")
        if not name:  # 6489999 처럼 이름 없는 행이 섞여 있다
            log.debug("  APMS 이름 없는 행 무시: %s", i)
            continue
        result.setdefault(name, i["orgCd"])
    return result


def fetch_tour_area(key: str, area_code: str | None = None) -> dict[str, str]:
    """areaCode2 는 areaCode 없이 부르면 시도, 주면 그 시도의 시군구를 준다."""
    params: dict = {"MobileOS": "ETC", "MobileApp": "petapp", "numOfRows": 200, "pageNo": 1}
    if area_code:
        params["areaCode"] = area_code
    body = get(f"{TOUR_BASE}/areaCode2", key, **params)
    result: dict[str, str] = {}
    for i in items(body):
        result.setdefault(i["name"], i["code"])
    return result


# --- 매핑 -------------------------------------------------------------------

PAGE = 1000  # PostgREST 는 한 번에 최대 1000행만 준다 (읍면동은 5000행이 넘는다)


def select_all(client, columns: str, **filters) -> list[dict]:
    """regions 를 페이지 단위로 끝까지 읽는다.

    .range() 없이 부르면 1000행에서 조용히 잘린다. 읍면동이 그대로 누락되므로
    반드시 여기를 거친다.
    """
    rows: list[dict] = []
    offset = 0
    while True:
        query = client.table(TABLE).select(columns)
        for column, value in filters.items():
            query = query.eq(column, value)
        page = query.order("code").range(offset, offset + PAGE - 1).execute().data
        rows.extend(page)
        if len(page) < PAGE:
            return rows
        offset += PAGE


class Unmapped(list):
    """미매핑 목록. 조용히 NULL 로 남기지 않는다 (DECISIONS D-29 §18.3)."""

    def note(self, region: dict, target: str) -> None:
        self.append("{} {} — {}".format(region["code"], region["full_name"], target))


def build_updates(client, key: str) -> tuple[list[dict], Unmapped]:
    """regions 전체를 훑어 외부 코드를 채운 행 목록을 만든다."""
    unmapped = Unmapped()
    now = datetime.now(timezone.utc).isoformat()

    sidos = select_all(client, "code,sido_name,full_name", level=1)
    apms_sido = fetch_apms_sido(key)
    tour_sido = fetch_tour_area(key)
    log.info(
        "외부 시도 목록 — APMS %d · TourAPI %d (regions %d)",
        len(apms_sido), len(tour_sido), len(sidos),
    )

    updates: list[dict] = []

    for sido in sidos:
        sido_name = sido["sido_name"]

        apms_upr = apms_sido.get(sido_name)
        if not apms_upr:
            unmapped.note(sido, "apms_upr_cd")

        tour_codes = [tour_sido[n] for n in tour_sido_names(sido_name) if n in tour_sido]
        if not tour_codes:
            unmapped.note(sido, "tour_area_cd")

        # 시도 행. 통합 지역은 TourAPI 코드가 2개라 한 칸에 담을 수 없다.
        # 앱은 시군구 단위로만 외부 API 를 부르므로 여기서는 비워 둔다.
        if len(tour_codes) > 1:
            log.info(
                "  [%s] TourAPI 코드가 %s 로 갈립니다 — 시도 행은 비우고 시군구에서 나눕니다.",
                sido_name, "+".join(tour_codes),
            )

        updates.append(
            {
                "code": sido["code"],
                # level·sido_name·full_name 은 NOT NULL 이라 upsert 페이로드에 있어야 한다.
                # 없으면 ON CONFLICT 로 가기 전에 INSERT 튜플이 제약에 걸린다.
                "level": 1,
                "sido_name": sido_name,
                "full_name": sido["full_name"],
                "apms_upr_cd": apms_upr,
                "apms_org_cd": None,
                "tour_area_cd": tour_codes[0] if len(tour_codes) == 1 else None,
                "tour_sigungu_cd": None,
                "updated_at": now,
            }
        )

        # 외부 시군구 목록은 시도별로 한 번씩만 받는다.
        apms_sgg = fetch_apms_sigungu(key, apms_upr) if apms_upr else {}
        tour_sgg: dict[str, tuple[str, str]] = {}
        for area in tour_codes:
            for name, code in fetch_tour_area(key, area).items():
                tour_sgg.setdefault(name, (area, code))

        sigungus = select_all(
            client, "code,sido_name,sigungu_name,full_name", parent_code=sido["code"]
        )

        for sgg in sigungus:
            apms_org = apms_sgg.get(parent_city(sgg["sigungu_name"]))
            # 세종특별자치시는 시군구가 없어 APMS 하위 목록이 빈다. 미매핑이 아니다.
            if not apms_org and apms_sgg:
                unmapped.note(sgg, "apms_org_cd")

            tour_hit = tour_sgg.get(tour_sigungu_name(sido_name, sgg["sigungu_name"]))
            if not tour_hit:
                unmapped.note(sgg, "tour_sigungu_cd")

            updates.append(
                {
                    "code": sgg["code"],
                    "level": 2,
                    "sido_name": sgg["sido_name"],
                    "full_name": sgg["full_name"],
                    "apms_upr_cd": apms_upr,
                    "apms_org_cd": apms_org,
                    "tour_area_cd": tour_hit[0] if tour_hit else None,
                    "tour_sigungu_cd": tour_hit[1] if tour_hit else None,
                    "updated_at": now,
                }
            )

        log.info(
            "  [%s] 시군구 %d — APMS %d · TourAPI %d",
            sido_name, len(sigungus), len(apms_sgg), len(tour_sgg),
        )

    # 읍면동은 부모 시군구의 코드를 그대로 물려받는다.
    by_code = {u["code"]: u for u in updates}
    dongs = select_all(client, "code,parent_code,sido_name,full_name", level=3)
    for dong in dongs:
        parent = by_code.get(dong["parent_code"])
        if parent is None:
            unmapped.note(dong, "부모 시군구 없음")
            continue
        updates.append(
            {
                "code": dong["code"],
                "level": 3,
                "sido_name": dong["sido_name"],
                "full_name": dong["full_name"],
                "apms_upr_cd": parent["apms_upr_cd"],
                "apms_org_cd": parent["apms_org_cd"],
                "tour_area_cd": parent["tour_area_cd"],
                "tour_sigungu_cd": parent["tour_sigungu_cd"],
                "updated_at": now,
            }
        )

    log.info("읍면동 %d행에 부모 시군구 코드를 물려줬습니다.", len(dongs))
    return updates, unmapped


# --- 실행 -------------------------------------------------------------------

COLUMNS = ("apms_upr_cd", "apms_org_cd", "tour_area_cd", "tour_sigungu_cd")


def summarize(updates: list[dict]) -> str:
    filled = {c: sum(1 for u in updates if u[c]) for c in COLUMNS}
    return " · ".join(f"{c} {n}" for c, n in filled.items()) + f" (총 {len(updates)}행)"


def run(client=None, dry_run: bool = False) -> list[dict]:
    if client is None:
        raise RuntimeError("mapping 은 regions 를 읽어야 하므로 DB 연결이 필요합니다.")

    key = load_data_go_kr_key()
    updates, unmapped = build_updates(client, key)

    log.info("매핑 결과 — %s", summarize(updates))

    if unmapped:
        log.warning("미매핑 %d건:", len(unmapped))
        for line in unmapped[:50]:
            log.warning("  %s", line)
        if len(unmapped) > 50:
            log.warning("  ... 외 %d건", len(unmapped) - 50)
    else:
        log.info("미매핑 0건 — 0단계 DoD 충족 (plan.md).")

    if dry_run:
        log.info("dry-run: DB 에 쓰지 않고 종료합니다.")
        return updates

    with SyncRun(client, SOURCE) as run_log:
        run_log.add(upsert(client, TABLE, updates, on_conflict="code"))
        log.info("총 %d행 반영", run_log.rows_upserted)

    return updates
