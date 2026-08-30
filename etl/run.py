"""ETL 실행 진입점.

사용 예:
    python run.py regions --dry-run --sample 5   # 인증키 없이 파싱 결과만 확인
    python run.py regions                        # Supabase 에 적재
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from petetl.config import ConfigError
from petetl.sources import regions


def _setup_logging() -> None:
    # 윈도우 콘솔에서 한글이 깨지지 않도록
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8", errors="replace")
    logging.basicConfig(level=logging.INFO, format="%(levelname)-7s %(message)s")


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="run.py", description="공공데이터 ETL")
    sub = parser.add_subparsers(dest="command", required=True)

    p_regions = sub.add_parser("regions", help="0단계: 법정동코드로 regions 테이블 적재")
    p_regions.add_argument("--file", type=Path, default=None, help="법정동코드 전체자료 txt 경로")
    p_regions.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 결과만 확인")
    p_regions.add_argument("--sample", type=int, default=0, help="결과 중 N행을 레벨별로 출력")

    return parser


def _print_sample(rows: list[dict], n: int) -> None:
    if n <= 0:
        return
    for level in (1, 2, 3):
        picked = [r for r in rows if r["level"] == level][:n]
        print(f"\n[level {level}]")
        for row in picked:
            print(
                "  {code}  parent={parent:<10}  {full_name}"
                "   (시도={sido_name} / 시군구={sigungu_name} / 읍면동={dong_name})".format(
                    parent=row["parent_code"] or "-", **row
                )
            )


def main() -> int:
    _setup_logging()
    args = _build_parser().parse_args()

    if args.command == "regions":
        client = None
        if not args.dry_run:
            from petetl.db import get_client

            client = get_client()
        rows = regions.run(client=client, path=args.file, dry_run=args.dry_run)
        _print_sample(rows, args.sample)

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ConfigError as exc:
        logging.error("%s", exc)
        sys.exit(2)
    except FileNotFoundError as exc:
        logging.error("%s", exc)
        sys.exit(2)
