# -*- coding: utf-8 -*-
"""읍면동 중심좌표 모듈의 순수 로직 테스트.

카카오 API 호출 없이 검증할 수 있는 부분만 담는다.
"""

import logging
import unittest

from petetl.sources import coords


def region(sido, sigungu, dong, full=None):
    return {
        "code": "0000000000",
        "sido_name": sido,
        "sigungu_name": sigungu,
        "dong_name": dong,
        "full_name": full or " ".join(x for x in (sido, sigungu, dong) if x),
    }


class QueryCandidatesTest(unittest.TestCase):
    def test_첫_후보는_full_name_그대로다(self):
        r = region("서울특별시", "강남구", "역삼동")
        self.assertEqual(coords.query_candidates(r)[0], "서울특별시 강남구 역삼동")

    def test_시도가_바뀐_곳은_옛_이름도_시도한다(self):
        # D-29: 카카오가 '전남광주통합특별시' 를 아직 모를 수 있다
        got = coords.query_candidates(region("전남광주통합특별시", "목포시", "용당동"))
        self.assertIn("광주광역시 목포시 용당동", got)
        self.assertIn("전라남도 목포시 용당동", got)

    def test_시군구가_바뀐_곳은_옛_이름도_시도한다(self):
        # D-30: 제물포구는 옛 중구·동구다
        got = coords.query_candidates(region("인천광역시", "제물포구", "북성동1가"))
        self.assertIn("인천광역시 중구 북성동1가", got)
        self.assertIn("인천광역시 동구 북성동1가", got)

    def test_마지막_수단으로_시도를_뗀다(self):
        got = coords.query_candidates(region("경기도", "성남시 분당구", "정자동"))
        self.assertEqual(got[-1], "성남시 분당구 정자동")

    def test_후보에_중복이_없고_순서가_유지된다(self):
        got = coords.query_candidates(region("서울특별시", "강남구", "역삼동"))
        self.assertEqual(len(got), len(set(got)))
        self.assertEqual(got[0], "서울특별시 강남구 역삼동")


class PickDocumentTest(unittest.TestCase):
    def test_읍면동_이름이_정확히_맞는_것을_우선한다(self):
        docs = [
            {"x": "127.0", "y": "37.0", "address": {"region_3depth_name": "삼성동"}},
            {"x": "127.1", "y": "37.1", "address": {"region_3depth_name": "역삼동"}},
        ]
        self.assertEqual(coords.pick_document(docs, "역삼동")["x"], "127.1")

    def test_일치하는_것이_없으면_첫번째를_쓴다(self):
        docs = [{"x": "127.0", "y": "37.0", "address": {"region_3depth_name": "삼성동"}}]
        self.assertEqual(coords.pick_document(docs, "역삼동")["x"], "127.0")

    def test_비어_있으면_None(self):
        self.assertIsNone(coords.pick_document([], "역삼동"))


class ToLatLngTest(unittest.TestCase):
    def setUp(self):
        # '범위 밖 좌표' 경고가 테스트 출력에 섞이지 않게 한다
        logging.disable(logging.WARNING)
        self.addCleanup(logging.disable, logging.NOTSET)

    def test_x가_경도_y가_위도다(self):
        # 뒤집어 쓰면 좌표가 동해로 간다. 이 테스트가 그걸 막는다
        self.assertEqual(coords.to_latlng({"x": "127.0276", "y": "37.4979"}),
                         (37.4979, 127.0276))

    def test_대한민국_범위를_벗어나면_버린다(self):
        self.assertIsNone(coords.to_latlng({"x": "37.4979", "y": "127.0276"}))  # 뒤바뀐 값
        self.assertIsNone(coords.to_latlng({"x": "0", "y": "0"}))

    def test_값이_없거나_숫자가_아니면_None(self):
        self.assertIsNone(coords.to_latlng({}))
        self.assertIsNone(coords.to_latlng({"x": "abc", "y": "37.5"}))
        self.assertIsNone(coords.to_latlng({"x": None, "y": None}))


if __name__ == "__main__":
    unittest.main()
