"""regions 계층 구성 회귀 테스트.

의존성 없이 표준 라이브러리만으로 돌아간다.
    cd etl && python -m unittest discover tests

fixtures/sample_ldong.txt 는 실제 파일에서 까다로운 경우만 추린 축소판이다(cp949).
    - 리(里) 행           4183025021 양근리          -> 제외되어야 함
    - 폐지 행             1111010300 궁정동(폐지)     -> 제외되어야 함
    - 시도 행 없는 세종   3611000000               -> 시도 행이 합성되어야 함
    - 우산 행 성남시      4113000000               -> 자식이 없으므로 제외되어야 함
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from petetl.sources import regions  # noqa: E402

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "sample_ldong.txt"


class ClassifyTest(unittest.TestCase):
    def test_levels(self) -> None:
        self.assertEqual(regions.classify("1100000000"), 1)  # 시도
        self.assertEqual(regions.classify("1111000000"), 2)  # 시군구
        self.assertEqual(regions.classify("1111010100"), 3)  # 읍면동
        self.assertIsNone(regions.classify("4183025021"))    # 리

    def test_parent_codes(self) -> None:
        self.assertEqual(regions.sido_code("4113110100"), "4100000000")
        self.assertEqual(regions.sigungu_code("4113110100"), "4113100000")


class BuildRegionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rows, cls.warnings = regions.build_regions(regions.parse_file(FIXTURE))
        cls.by_code = {r["code"]: r for r in cls.rows}

    def test_counts(self) -> None:
        self.assertEqual(len(self.by_code), 14)

    def test_ri_and_abolished_excluded(self) -> None:
        self.assertNotIn("4183025021", self.by_code)  # 양근리
        self.assertNotIn("1111010300", self.by_code)  # 폐지된 궁정동

    def test_childless_sigungu_dropped(self) -> None:
        """'경기도 성남시'는 산하 법정동이 없으므로 막다른 길이 되면 안 된다."""
        self.assertNotIn("4113000000", self.by_code)
        self.assertIn("4113100000", self.by_code)  # 성남시수정구는 남는다

    def test_missing_sido_synthesized(self) -> None:
        """세종특별자치시는 파일에 시도 행이 없다. 3단 콤보가 끊기면 안 된다."""
        sido = self.by_code["3600000000"]
        self.assertEqual(sido["level"], 1)
        self.assertEqual(sido["full_name"], "세종특별자치시")
        self.assertEqual(self.by_code["3611000000"]["parent_code"], "3600000000")
        self.assertTrue(any("합성" in w for w in self.warnings))

    def test_names_split_without_spaces(self) -> None:
        """'경기도 성남시수정구'처럼 붙여 쓴 이름도 올바르게 쪼개져야 한다."""
        dong = self.by_code["4113110100"]
        self.assertEqual(dong["sido_name"], "경기도")
        self.assertEqual(dong["sigungu_name"], "성남시수정구")
        self.assertEqual(dong["dong_name"], "신흥동")

    def test_every_parent_exists(self) -> None:
        """parent_code 가 끊긴 곳이 없어야 한다 (plan.md 0단계 완료 기준)."""
        for row in self.rows:
            if row["parent_code"] is not None:
                self.assertIn(row["parent_code"], self.by_code, row["full_name"])

    def test_insert_order_is_top_down(self) -> None:
        """외래키 때문에 상위 레벨이 먼저 나와야 한다."""
        levels = [r["level"] for r in self.rows]
        self.assertEqual(levels, sorted(levels))


if __name__ == "__main__":
    unittest.main()
