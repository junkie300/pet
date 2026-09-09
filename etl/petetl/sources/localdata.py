# -*- coding: utf-8 -*-
"""0단계 · regions.localdata_cd — 개방자치단체코드 매핑.

지역코드 목록은 API 가 아니라 문서로 배포된다. 그래서 이 모듈만 인증키 없이 돈다 —
`etl/docs/` 에 커밋된 엑셀을 읽어 `regions` 에 코드를 채운다.

⚠️ **LOCALDATA(localdata.go.kr) 는 2026-04-16 에 닫혔다** (D-93). 인허가 데이터는
공공데이터포털로 옮겨졌고, 우리가 부르는 API(동물병원 15154952 · 미용업 15154944)는
처음부터 그쪽이라 **이 매핑은 이미 다 채워져 있고 갱신할 곳도 없다.**
같은 코드표가 공공데이터포털의 각 서비스 참고문서로도 배포되므로, 다시 돌릴 일이
생기면 `etl/docs/` 의 엑셀을 그대로 쓴다 — 받아 둔 사본이 유일한 원본이다.

⚠️ **LOCALDATA 는 행정구역 개편을 아직 반영하지 않았다** (2026-09-05 실측).
`전남광주통합특별시` 가 없고 `광주광역시`·`전라남도` 가 그대로 있으며,
인천 신설 자치구(제물포·영종·서해·검단)도 없다. D-29·D-30 과 같은 유형이라
`mapping.py` 처럼 예외표로 처리한다.
"""

from __future__ import annotations

import glob
import logging
import os
from datetime import datetime, timezone

from ..db import upsert
from ..synclog import SyncRun
from .mapping import Unmapped, parent_city, select_all

log = logging.getLogger(__name__)

SOURCE = "localdata_code"
TABLE = "regions"
SHEET = "1. 개방자치단체코드"

# 문서 파일은 이름이 바뀔 수 있어 패턴으로 찾는다. etl/docs/README.md 참고.
DOC_PATTERN = "docs/*자치단체코드*.xlsx"

# regions 시도명 -> 시군구 코드 이름의 접두어. 값이 2개면 1:N 구간이다.
# 예) '서울강남구' = '서울' + '강남구'
SIDO_PREFIX: dict[str, tuple[str, ...]] = {
    "서울특별시": ("서울",),
    "부산광역시": ("부산",),
    "대구광역시": ("대구",),
    "인천광역시": ("인천",),
    "대전광역시": ("대전",),
    "울산광역시": ("울산",),
    # 세종은 시군구가 없다. regions 는 자기 이름으로 된 시군구 행을 하나 두므로
    # 접두어를 빈 문자열로 둬서 '세종특별자치시' 가 그대로 이름이 되게 한다.
    "세종특별자치시": ("",),
    "경기도": ("경기",),
    "강원특별자치도": ("강원",),
    "충청북도": ("충북",),
    "충청남도": ("충남",),
    "전북특별자치도": ("전북",),
    "경상북도": ("경북",),
    "경상남도": ("경남",),
    "제주특별자치도": ("제주",),
    # D-29. LOCALDATA 는 통합 전 체계라 광주와 전남이 따로 있다.
    "전남광주통합특별시": ("광주", "전남"),
}

# D-30. LOCALDATA 에 아직 없는 신설 자치구 -> 그 자리에 있던 옛 자치구 이름.
SIGUNGU_ALIAS: dict[tuple[str, str], str] = {
    ("인천광역시", "제물포구"): "중구",
    ("인천광역시", "영종구"): "중구",
    ("인천광역시", "서해구"): "서구",
    ("인천광역시", "검단구"): "서구",
}


def find_document(base_dir: str | None = None) -> str:
    """커밋된 개방자치단체코드 엑셀 경로. 없으면 무엇을 어디에 두라고 알려준다."""
    root = base_dir or os.getcwd()
    hits = sorted(glob.glob(os.path.join(root, DOC_PATTERN)))
    if not hits:
        raise FileNotFoundError(
            f"개방자치단체코드 엑셀을 찾지 못했습니다: {os.path.join(root, DOC_PATTERN)}\n"
            # ⚠️ 받아 오던 곳(localdata.go.kr)이 없어졌다 (D-93). 저장소의 사본이 원본이다.
            "  → 저장소에 커밋된 사본입니다. 지웠다면 git 에서 되살리거나,\n"
            "     공공데이터포털 15154952 · 15154944 의 '참고문서'에서 같은 엑셀을 받습니다.\n"
            "  → 자세한 것은 etl/docs/README.md"
        )
    return hits[0]


def load_codes(path: str) -> tuple[dict[str, str], dict[str, str]]:
    """엑셀 -> (시군구급 {이름: 코드}, 시도급 {시도명: `_ALL` 코드}).

    시도 행에는 `_ALL`("서울특별시 전체" = `6110000_ALL`)을 쓴다. 그게 '그 시도 전체'라는
    뜻이라 시도 행의 의미와 정확히 맞고, **접두어 없는 시도명이 없는 시도가 있다**
    (강원특별자치도는 `_ALL` 만 있다).
    """
    import openpyxl  # 이 모듈에서만 쓴다. 다른 명령이 openpyxl 없이도 돌게 한다.

    workbook = openpyxl.load_workbook(path, data_only=True, read_only=True)
    if SHEET not in workbook.sheetnames:
        raise ValueError(f"'{SHEET}' 시트가 없습니다. 시트 이름: {workbook.sheetnames}")

    codes: dict[str, str] = {}
    sido_codes: dict[str, str] = {}
    for row in workbook[SHEET].iter_rows(min_row=2, values_only=True):
        if len(row) < 3 or not row[1] or not row[2]:
            continue
        name, code = str(row[1]).strip(), str(row[2]).strip()
        if code.endswith("_ALL"):
            sido_codes[name.removesuffix(" 전체").strip()] = code
        else:
            codes[name] = code
    return codes, sido_codes


def sigungu_key(sido_name: str, sigungu_name: str, prefix: str) -> str:
    """LOCALDATA 쪽 시군구 이름. 신설 자치구는 옛 이름으로, 일반구는 모시로 접는다."""
    alias = SIGUNGU_ALIAS.get((sido_name, sigungu_name))
    base = alias if alias else parent_city(sigungu_name)
    return prefix + base


def build_updates(
    client, codes: dict[str, str], sido_codes: dict[str, str]
) -> tuple[list[dict], Unmapped]:
    """regions 전체를 훑어 localdata_cd 를 채운 행 목록을 만든다."""
    unmapped = Unmapped()
    now = datetime.now(timezone.utc).isoformat()
    updates: list[dict] = []

    sidos = select_all(client, "code,sido_name,full_name", level=1)
    log.info(
        "LOCALDATA 자치단체 %d개(+시도 전체 %d개) · regions 시도 %d개",
        len(codes), len(sido_codes), len(sidos),
    )

    for sido in sidos:
        sido_name = sido["sido_name"]
        prefixes = SIDO_PREFIX.get(sido_name)
        if prefixes is None:
            unmapped.note(sido, "SIDO_PREFIX 에 없음 — 예외표를 갱신할 것")
            prefixes = ()

        # 시도 행. 통합 지역은 LOCALDATA 코드가 2개라 한 칸에 담을 수 없다.
        # 앱과 ETL 은 시군구 단위로만 LOCALDATA 를 부르므로 여기서는 비워 둔다 (mapping.py 와 같은 판단).
        sido_code = sido_codes.get(sido_name)
        if sido_code is None and len(prefixes) > 1:
            log.info(
                "  [%s] LOCALDATA 는 통합 전 체계입니다 — 시도 행은 비우고 시군구에서 %s 로 나눕니다.",
                sido_name, "+".join(prefixes),
            )
        elif sido_code is None:
            unmapped.note(sido, "localdata_cd")

        updates.append(
            {
                # level·sido_name·full_name 은 NOT NULL 이라 upsert 페이로드에 있어야 한다.
                "code": sido["code"],
                "level": 1,
                "sido_name": sido_name,
                "full_name": sido["full_name"],
                "localdata_cd": sido_code,
                "updated_at": now,
            }
        )

        sigungus = select_all(
            client, "code,sido_name,sigungu_name,full_name", parent_code=sido["code"]
        )
        hit = 0
        for sgg in sigungus:
            code = None
            for prefix in prefixes:
                code = codes.get(sigungu_key(sido_name, sgg["sigungu_name"], prefix))
                if code:
                    break
            if code:
                hit += 1
            else:
                unmapped.note(sgg, "localdata_cd")

            updates.append(
                {
                    "code": sgg["code"],
                    "level": 2,
                    "sido_name": sgg["sido_name"],
                    "full_name": sgg["full_name"],
                    "localdata_cd": code,
                    "updated_at": now,
                }
            )
        log.info("  [%s] 시군구 %d 중 %d 매핑", sido_name, len(sigungus), hit)

    # 읍면동은 부모 시군구의 코드를 그대로 물려받는다. LOCALDATA 는 읍면동 코드가 없다.
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
                "localdata_cd": parent["localdata_cd"],
                "updated_at": now,
            }
        )
    log.info("읍면동 %d행에 부모 시군구 코드를 물려줬습니다.", len(dongs))

    return updates, unmapped


def summarize(updates: list[dict]) -> str:
    filled = sum(1 for u in updates if u.get("localdata_cd"))
    return f"localdata_cd {filled}/{len(updates)}"


def run(client=None, dry_run: bool = False, base_dir: str | None = None) -> list[dict]:
    path = find_document(base_dir)
    log.info("문서: %s", os.path.basename(path))
    codes, sido_codes = load_codes(path)

    if client is None:
        raise RuntimeError("localdata 는 regions 를 읽어야 하므로 DB 연결이 필요합니다.")

    updates, unmapped = build_updates(client, codes, sido_codes)
    log.info(summarize(updates))

    if unmapped:
        # 조용히 NULL 로 남기지 않는다 (D-29 §18.3).
        log.warning("미매핑 %d건:", len(unmapped))
        for line in unmapped[:20]:
            log.warning("  %s", line)
        if len(unmapped) > 20:
            log.warning("  ... 외 %d건", len(unmapped) - 20)
    else:
        log.info("미매핑 0건 — 0단계 DoD 충족 (plan.md).")

    if dry_run:
        log.info("dry-run: DB 에 쓰지 않고 종료합니다.")
        return updates

    with SyncRun(client, SOURCE) as run_log:
        run_log.add(upsert(client, TABLE, updates, on_conflict="code"))
        log.info("총 %d행 반영", run_log.rows_upserted)

    return updates
