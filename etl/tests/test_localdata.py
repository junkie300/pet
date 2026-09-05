# -*- coding: utf-8 -*-
"""localdata 매핑 규칙 테스트. 네트워크·DB 없이 돈다."""

import unittest

from petetl.sources import localdata


class SigunguKeyTest(unittest.TestCase):
    def test_접두어와_이름을_붙인다(self):
        self.assertEqual(localdata.sigungu_key("서울특별시", "강남구", "서울"), "서울강남구")
        self.assertEqual(localdata.sigungu_key("경기도", "고양시", "경기"), "경기고양시")

    def test_일반구는_모시로_접는다(self):
        # LOCALDATA 에는 '경기성남시' 만 있고 '경기성남시분당구' 는 없다.
        self.assertEqual(localdata.sigungu_key("경기도", "성남시 분당구", "경기"), "경기성남시")

    def test_인천_신설_자치구는_옛_이름으로_되돌린다(self):
        # D-30. LOCALDATA 는 아직 개편 전 체계다.
        self.assertEqual(localdata.sigungu_key("인천광역시", "제물포구", "인천"), "인천중구")
        self.assertEqual(localdata.sigungu_key("인천광역시", "영종구", "인천"), "인천중구")
        self.assertEqual(localdata.sigungu_key("인천광역시", "서해구", "인천"), "인천서구")
        self.assertEqual(localdata.sigungu_key("인천광역시", "검단구", "인천"), "인천서구")

    def test_세종은_접두어가_비어_이름이_그대로_쓰인다(self):
        prefixes = localdata.SIDO_PREFIX["세종특별자치시"]
        self.assertEqual(prefixes, ("",))
        self.assertEqual(
            localdata.sigungu_key("세종특별자치시", "세종특별자치시", prefixes[0]),
            "세종특별자치시",
        )


class SidoPrefixTest(unittest.TestCase):
    def test_통합시도는_접두어가_둘이다(self):
        # D-29. LOCALDATA 에는 통합 전 '광주'·'전남' 이 따로 있다.
        self.assertEqual(localdata.SIDO_PREFIX["전남광주통합특별시"], ("광주", "전남"))

    def test_regions_의_시도_16개를_모두_안다(self):
        # 예외표에 빠진 시도가 있으면 그 시도의 시군구가 통째로 미매핑된다.
        expected = {
            "서울특별시", "부산광역시", "대구광역시", "인천광역시", "대전광역시",
            "울산광역시", "세종특별자치시", "경기도", "강원특별자치도", "충청북도",
            "충청남도", "전북특별자치도", "경상북도", "경상남도", "제주특별자치도",
            "전남광주통합특별시",
        }
        self.assertEqual(set(localdata.SIDO_PREFIX), expected)
        self.assertEqual(len(expected), 16)  # D-29 — 17개가 아니다


class LoadCodesTest(unittest.TestCase):
    def test_전체행은_시도급으로_따로_모은다(self):
        path = localdata.find_document()
        codes, sido_codes = localdata.load_codes(path)

        # 시군구급에는 _ALL 이 섞이지 않는다.
        self.assertTrue(all(not c.endswith("_ALL") for c in codes.values()))
        # 시도급은 '전체' 접미사를 뗀 이름으로 찾을 수 있다.
        self.assertEqual(sido_codes["서울특별시"], "6110000_ALL")
        # 강원은 접두어 없는 시도 행이 없어서 _ALL 이 유일한 경로다.
        self.assertIn("강원특별자치도", sido_codes)
        self.assertNotIn("강원특별자치도", codes)
        # 표본 몇 개
        self.assertEqual(codes["서울강남구"], "3220000")
        self.assertEqual(codes["인천강화군"], "3570000")


if __name__ == "__main__":
    unittest.main()
