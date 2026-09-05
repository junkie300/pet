"""ETL 실행 진입점.

사용 예:
    python run.py regions --dry-run --sample 5   # 인증키 없이 파싱 결과만 확인
    python run.py regions                        # Supabase 에 적재
    python run.py mapping --dry-run              # 외부 코드 매핑 결과만 확인
    python run.py mapping                        # regions 에 외부 코드 반영
    python run.py coords --limit 50              # 읍면동 중심좌표 (빈 곳만, 50개씩)
    python run.py localdata --dry-run           # LOCALDATA 자치단체코드 매핑 (인증키 불필요)
    python run.py hospitals --limit 200 --dry-run  # 동물병원 변환 결과만 확인
    python run.py status                         # 지금 어디까지 왔는지 (작업 재개 시 첫 명령)
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from petetl.config import ConfigError
from petetl.sources import coords, hospitals, localdata, mapping, regions


def _setup_logging() -> None:
    # 윈도우 콘솔에서 한글이 깨지지 않도록
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8", errors="replace")
    logging.basicConfig(level=logging.INFO, format="%(levelname)-7s %(message)s")
    # supabase 클라이언트가 요청마다 URL 을 통째로 찍는다. ETL 로그가 파묻힌다.
    for noisy in ("httpx", "hpack", "httpcore"):
        logging.getLogger(noisy).setLevel(logging.WARNING)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="run.py", description="공공데이터 ETL")
    sub = parser.add_subparsers(dest="command", required=True)

    p_regions = sub.add_parser("regions", help="0단계: 법정동코드로 regions 테이블 적재")
    p_regions.add_argument("--file", type=Path, default=None, help="법정동코드 전체자료 txt 경로")
    p_regions.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 결과만 확인")
    p_regions.add_argument("--sample", type=int, default=0, help="결과 중 N행을 레벨별로 출력")

    p_mapping = sub.add_parser("mapping", help="0단계: regions 에 APMS·TourAPI 지역코드 매핑")
    p_mapping.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 결과만 확인")

    p_coords = sub.add_parser("coords", help="0단계: 읍면동 중심좌표 채우기 (카카오 로컬 API)")
    p_coords.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 결과만 확인")
    p_coords.add_argument("--limit", type=int, default=None, help="한 번에 처리할 최대 개수")
    p_coords.add_argument("--all", action="store_true",
                          help="이미 좌표가 있는 곳도 다시 조회한다 (기본은 빈 곳만)")

    p_local = sub.add_parser(
        "localdata",
        help="0단계: LOCALDATA 자치단체코드 매핑 (etl/docs/ 엑셀만 있으면 되고 인증키는 불필요)",
    )
    p_local.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 결과만 확인")

    p_hosp = sub.add_parser("hospitals", help="1단계: 동물병원 → places (행안부 15154952)")
    p_hosp.add_argument("--dry-run", action="store_true", help="DB 에 쓰지 않고 결과만 확인")
    p_hosp.add_argument("--limit", type=int, default=None, help="앞에서 N건만 처리 (확인용)")

    sub.add_parser("status", help="지금 어디까지 왔는지 한 화면에 출력 (작업 재개용)")

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

    elif args.command == "mapping":
        # 매핑은 regions 를 읽어야 하므로 dry-run 에서도 DB 연결이 필요하다.
        from petetl.db import get_client

        mapping.run(client=get_client(), dry_run=args.dry_run)

    elif args.command == "coords":
        from petetl.db import get_client

        coords.run(client=get_client(), dry_run=args.dry_run,
                   limit=args.limit, only_missing=not args.all)

    elif args.command == "localdata":
        # 매핑과 같은 이유로 dry-run 이어도 regions 를 읽어야 한다.
        from petetl.db import get_client

        localdata.run(client=get_client(), dry_run=args.dry_run)

    elif args.command == "hospitals":
        from petetl.db import get_client

        hospitals.run(client=get_client(), dry_run=args.dry_run, limit=args.limit)

    elif args.command == "status":
        from petetl.db import get_client
        from petetl.status import report

        print(report(get_client()))

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
