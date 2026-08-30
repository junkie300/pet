"""0단계 — regions 테이블 적재.

기준 데이터는 행정표준코드관리시스템의 **법정동코드 전체자료** 텍스트 파일이다.
(https://www.code.go.kr 접속 후 법정동코드 전체자료 내려받기)

파일 형식: 탭 구분, cp949, 헤더 1줄
    법정동코드<TAB>법정동명<TAB>폐지여부
    1100000000    서울특별시                존재
    1111000000    서울특별시 종로구         존재
    1111010100    서울특별시 종로구 청운동  존재

코드 10자리 구조: 시도(2) 시군구(3) 읍면동(3) 리(2)
    - 리(마지막 2자리가 00이 아닌 행)는 적재하지 않는다. 앱의 최소 단위는 읍면동이다.
    - 폐지된 행은 제외한다.

주의: 이 모듈은 계층과 명칭만 채운다. 외부 코드 매핑(localdata_cd / apms_* / tour_*)과
읍면동 중심좌표는 인증키가 발급된 뒤 별도 모듈에서 UPDATE 한다. (plan.md 0단계)
"""

from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from pathlib import Path

from ..config import DATA_DIR
from ..db import upsert
from ..synclog import SyncRun

log = logging.getLogger(__name__)

SOURCE = "ldong_code"
TABLE = "regions"
DEFAULT_FILENAME = "법정동코드 전체자료.txt"
ENCODINGS = ("cp949", "utf-8-sig", "utf-8")

DOWNLOAD_GUIDE = """
법정동코드 전체자료 파일이 없습니다.

  1. https://www.code.go.kr 접속
  2. 법정동코드 > 전체자료 내려받기
  3. 받은 txt 파일을 아래 경로에 두세요 (파일명 그대로)
       {path}
""".strip()

_WS = re.compile(r"\s+")


# --- 코드 구조 -------------------------------------------------------------

def sido_code(code: str) -> str:
    return code[:2] + "0" * 8


def sigungu_code(code: str) -> str:
    return code[:5] + "0" * 5


def classify(code: str) -> int | None:
    """법정동코드에서 레벨을 판정한다. 리(里)면 None."""
    sgg, emd, ri = code[2:5], code[5:8], code[8:10]
    if ri != "00":
        return None
    if sgg == "000":
        return 1
    if emd == "000":
        return 2
    return 3


def _norm(name: str) -> str:
    return _WS.sub(" ", name).strip()


def _first_token(name: str) -> str:
    return name.split(" ")[0] if name else name


def _strip_prefix(full: str, prefix: str) -> str:
    """full_name 에서 상위 지역명을 떼어낸다.

    파일이 '경기도 성남시수정구' 처럼 붙여 쓰든 '경기도 성남시 수정구' 처럼 띄어 쓰든
    상위 행의 full_name 을 그대로 접두사로 쓰므로 양쪽 다 동작한다.
    """
    if prefix and full.startswith(prefix):
        return full[len(prefix):].strip()
    return full


# --- 파싱 -----------------------------------------------------------------

def read_lines(path: Path) -> list[str]:
    last_error: UnicodeDecodeError | None = None
    for encoding in ENCODINGS:
        try:
            return path.read_text(encoding=encoding).splitlines()
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"{path} 인코딩을 판별하지 못했습니다 (시도: {ENCODINGS})") from last_error


def parse_file(path: Path) -> list[tuple[str, str]]:
    """(법정동코드, 법정동명) 목록. 폐지분과 형식 불량 행은 버린다."""
    rows: list[tuple[str, str]] = []
    dropped_abolished = 0

    for raw in read_lines(path)[1:]:  # 첫 줄은 헤더
        if not raw.strip():
            continue
        parts = raw.split("\t")
        if len(parts) < 2:
            continue

        code = parts[0].strip()
        name = _norm(parts[1])
        status = parts[2].strip() if len(parts) > 2 else "존재"

        if len(code) != 10 or not code.isdigit() or not name:
            continue
        if status and status != "존재":
            dropped_abolished += 1
            continue
        rows.append((code, name))

    log.info("파싱 %d행 (폐지 제외 %d행)", len(rows), dropped_abolished)
    return rows


# --- 계층 구성 -------------------------------------------------------------

def build_regions(raw_rows: list[tuple[str, str]]) -> tuple[list[dict], list[str]]:
    """법정동코드 목록을 regions 행으로 변환한다. (rows, warnings) 를 돌려준다."""
    warnings: list[str] = []
    nodes: dict[str, dict] = {}

    for code, name in raw_rows:
        level = classify(code)
        if level is None or code in nodes:
            continue
        nodes[code] = {"code": code, "level": level, "full_name": name}

    # 1) 빠진 상위 행 합성.
    #    세종특별자치시처럼 시도 행이 파일에 없는 경우가 있다. 3단 콤보가 끊기지 않게 만들어 준다.
    for node in list(nodes.values()):
        if node["level"] >= 2:
            code = sido_code(node["code"])
            if code not in nodes:
                name = _first_token(node["full_name"])
                nodes[code] = {"code": code, "level": 1, "full_name": name}
                warnings.append("시도 행 합성: {} {}".format(code, name))

    for node in list(nodes.values()):
        if node["level"] == 3:
            code = sigungu_code(node["code"])
            if code not in nodes:
                sido = nodes[sido_code(node["code"])]["full_name"]
                rest = _first_token(_strip_prefix(node["full_name"], sido))
                name = "{} {}".format(sido, rest).strip()
                nodes[code] = {"code": code, "level": 2, "full_name": name}
                warnings.append("시군구 행 합성: {} {}".format(code, name))

    # 2) 부모 연결
    for node in nodes.values():
        if node["level"] == 1:
            node["parent_code"] = None
        elif node["level"] == 2:
            node["parent_code"] = sido_code(node["code"])
        else:
            node["parent_code"] = sigungu_code(node["code"])

    # 3) 자식 없는 시군구 제거.
    #    '경기도 성남시'(41130)처럼 산하 법정동이 없는 우산 행이 존재한다. 남겨 두면
    #    시군구를 골랐는데 읍면동 목록이 비는 막다른 길이 생긴다. 실제 행정구역은
    #    '성남시수정구'(41131)처럼 자식 있는 행으로 남는다.
    has_child = {node["parent_code"] for node in nodes.values() if node["parent_code"]}
    childless = [c for c, n in nodes.items() if n["level"] == 2 and c not in has_child]
    for code in childless:
        del nodes[code]
    if childless:
        log.info("자식 없는 시군구 %d행 제외 (예: 성남시 -> 성남시수정구로 대체)", len(childless))

    # 4) 명칭 채우기
    now = datetime.now(timezone.utc).isoformat()
    rows: list[dict] = []
    for node in nodes.values():
        code, level, full_name = node["code"], node["level"], node["full_name"]
        sido_name = nodes[sido_code(code)]["full_name"]

        if level == 1:
            sigungu_name = None
            dong_name = None
        elif level == 2:
            sigungu_name = _strip_prefix(full_name, sido_name) or full_name
            dong_name = None
        else:
            parent = nodes[node["parent_code"]]
            sigungu_name = _strip_prefix(parent["full_name"], sido_name) or parent["full_name"]
            dong_name = _strip_prefix(full_name, parent["full_name"]) or full_name

        rows.append(
            {
                "code": code,
                "level": level,
                "parent_code": node["parent_code"],
                "sido_name": sido_name,
                "sigungu_name": sigungu_name,
                "dong_name": dong_name,
                "full_name": full_name,
                "updated_at": now,
            }
        )

    rows.sort(key=lambda r: (r["level"], r["code"]))
    return rows, warnings


# --- 실행 -----------------------------------------------------------------

def summarize(rows: list[dict]) -> str:
    counts = {1: 0, 2: 0, 3: 0}
    for row in rows:
        counts[row["level"]] += 1
    return "시도 {} · 시군구 {} · 읍면동 {} (총 {})".format(
        counts[1], counts[2], counts[3], len(rows)
    )


def resolve_source_path(path: Path | None = None) -> Path:
    """원본 파일 경로를 찾는다.

    내려받은 파일명이 사이트 버전에 따라 조금씩 다르므로,
    기본 이름이 없으면 data/ 안의 '법정동'이 들어간 txt 를 찾아 쓴다.
    """
    if path is not None:
        return path

    default = DATA_DIR / DEFAULT_FILENAME
    if default.exists():
        return default

    candidates = sorted(p for p in DATA_DIR.glob("*.txt") if "법정동" in p.name)
    if candidates:
        log.info("파일 자동 선택: %s", candidates[0].name)
        return candidates[0]

    return default  # 없음. 아래에서 안내 메시지와 함께 실패한다.


def run(client=None, path: Path | None = None, dry_run: bool = False) -> list[dict]:
    source_path = resolve_source_path(path)
    if not source_path.exists():
        raise FileNotFoundError(DOWNLOAD_GUIDE.format(path=source_path))

    rows, warnings = build_regions(parse_file(source_path))
    log.info("구성 완료 — %s", summarize(rows))
    for message in warnings:
        log.warning("  %s", message)

    if dry_run or client is None:
        log.info("dry-run: DB 에 쓰지 않고 종료합니다.")
        return rows

    # parent_code 외래키 때문에 반드시 상위 레벨부터 넣는다.
    with SyncRun(client, SOURCE) as run_log:
        for level in (1, 2, 3):
            batch = [r for r in rows if r["level"] == level]
            log.info("level %d — %d행 upsert", level, len(batch))
            run_log.add(upsert(client, TABLE, batch, on_conflict="code"))
        log.info("총 %d행 반영", run_log.rows_upserted)

    return rows
