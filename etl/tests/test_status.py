# -*- coding: utf-8 -*-
"""status 화면의 정렬 계산 테스트.

DB 조회 부분은 담지 않는다. 한글이 섞인 표가 어긋나지 않는지만 고정한다.
"""

import unittest

from petetl.status import _pad, _width


class WidthTest(unittest.TestCase):
    def test_한글은_두_칸이다(self):
        self.assertEqual(_width("가"), 2)
        self.assertEqual(_width("중심좌표"), 8)

    def test_영문과_숫자는_한_칸이다(self):
        self.assertEqual(_width("LOCALDATA"), 9)
        self.assertEqual(_width("2026"), 4)

    def test_섞이면_더한다(self):
        self.assertEqual(_width("APMS 시도"), 4 + 1 + 4)


class PadTest(unittest.TestCase):
    def test_표시_폭_기준으로_채운다(self):
        # str.ljust 는 글자 수로 세어서 한글이 섞이면 열이 어긋난다
        self.assertEqual(_width(_pad("중심좌표", 18)), 18)
        self.assertEqual(_width(_pad("LOCALDATA", 18)), 18)
        self.assertEqual(_width(_pad("APMS 시군구", 18)), 18)

    def test_이미_길면_자르지_않는다(self):
        self.assertEqual(_pad("LOCALDATA", 3), "LOCALDATA")


if __name__ == "__main__":
    unittest.main()
