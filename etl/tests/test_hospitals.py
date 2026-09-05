# -*- coding: utf-8 -*-
"""동물병원 변환 규칙 테스트. 네트워크·DB 없이 돈다."""

import unittest

from petetl.sources import hospitals

# 미리보기 응답에서 가져온 실제 행 (etl/docs/15154952-동물병원-응답스키마.md)
SAMPLE = {
    "BPLC_NM": "광명온동물병원",
    "MNG_NO": "390000001020260003",
    "TELNO": "02-6951-1003",
    "LOTNO_ADDR": "경기도 광명시 광명동 155-3",
    "ROAD_NM_ADDR": "경기도 광명시 광이로 10, 1층 (광명동)",
    "ROAD_NM_ZIP": "14221",
    "CRD_INFO_X": "187381.282564181",
    "CRD_INFO_Y": "441908.62126562",
    "SALS_STTS_CD": "01",
    "SALS_STTS_NM": "영업/정상",
    "DTL_SALS_STTS_NM": "정상",
    "LCPMT_YMD": "2026-09-03",
    "CLSBIZ_YMD": "",
    "OPN_ATMY_GRP_CD": "3900000",
    "DAT_UPDT_PNT": "2026-09-04 21:02:00",
}


class CoordinateTest(unittest.TestCase):
    """좌표계를 잘못 잡으면 조용히 321m 어긋난다. 실측 기준점으로 고정한다."""

    def setUp(self):
        self.transformer = hospitals.make_transformer()

    def test_광명동_좌표가_카카오_지오코딩과_1m_이내로_맞는다(self):
        lat, lng = hospitals.to_latlng(
            self.transformer, "187381.282564181", "441908.62126562"
        )
        # 카카오 '경기도 광명시 광이로 10' = 37.47927, 126.85812
        self.assertAlmostEqual(lat, 37.47927, places=4)
        self.assertAlmostEqual(lng, 126.85812, places=4)

    def test_논현동_좌표(self):
        lat, lng = hospitals.to_latlng(
            self.transformer, "202875.328642756", "445077.204272347"
        )
        self.assertAlmostEqual(lat, 37.50790, places=4)
        self.assertAlmostEqual(lng, 127.03331, places=4)

    def test_빈_좌표는_None(self):
        self.assertIsNone(hospitals.to_latlng(self.transformer, "", ""))
        self.assertIsNone(hospitals.to_latlng(self.transformer, None, None))

    def test_숫자가_아니면_None(self):
        self.assertIsNone(hospitals.to_latlng(self.transformer, "abc", "def"))

    def test_한반도_밖이면_None(self):
        # 변환식을 잘못 잡았을 때 조용히 통과하지 않게 막는 그물.
        self.assertIsNone(hospitals.to_latlng(self.transformer, "0", "0"))


class RegionMatchTest(unittest.TestCase):
    INDEX = {
        "경기도 광명시 광명동": "4121010200",
        "충청북도 청주시 흥덕구 송절동": "4311313400",
        "세종특별자치시 아름동": "3611011500",
        "전남광주통합특별시 광산구 송정동": "1233010100",
        "인천광역시 강화군 강화읍": "2871025000",
    }

    def test_토큰_3개(self):
        self.assertEqual(
            hospitals.match_region("경기도 광명시 광명동 155-3", self.INDEX), "4121010200"
        )

    def test_일반구가_있으면_토큰_4개(self):
        self.assertEqual(
            hospitals.match_region("충청북도 청주시 흥덕구 송절동 679", self.INDEX),
            "4311313400",
        )

    def test_세종은_토큰_2개(self):
        # 시군구가 없어서 '시도 + 동' 이다. 3개만 보면 영영 못 맞춘다.
        self.assertEqual(
            hospitals.match_region("세종특별자치시 아름동 1362", self.INDEX), "3611011500"
        )

    def test_통합시도는_주소의_옛_이름을_되돌린다(self):
        # D-29. regions 는 '전남광주통합특별시', 인허가 주소는 '광주광역시'.
        self.assertEqual(
            hospitals.match_region("광주광역시 광산구 송정동 100", self.INDEX), "1233010100"
        )

    def test_읍_단위도_맞는다(self):
        # 리(里)는 regions 에 없다. 읍에서 멈춰야 한다.
        self.assertEqual(
            hospitals.match_region("인천광역시 강화군 강화읍 남산리 442-4", self.INDEX),
            "2871025000",
        )

    def test_빈_주소는_None(self):
        self.assertIsNone(hospitals.match_region("", self.INDEX))
        self.assertIsNone(hospitals.match_region(None, self.INDEX))

    def test_폐지된_법정동은_이름으로_못_맞춘다(self):
        # 카카오 보완(lookup_kakao)이 필요한 경우. 여기서 None 이 나오는 게 정상이다.
        self.assertIsNone(
            hospitals.match_region("경기도 화성시 동탄구 오산동 1088-1", self.INDEX)
        )


class StatusTest(unittest.TestCase):
    def test_영업만_open(self):
        self.assertEqual(hospitals.STATUS_MAP["01"], "open")

    def test_휴업은_suspended(self):
        self.assertEqual(hospitals.STATUS_MAP["02"], "suspended")

    def test_나머지는_전부_closed(self):
        for code in ("03", "04", "05", "06"):
            self.assertEqual(hospitals.STATUS_MAP[code], "closed")

    def test_모르는_코드는_closed_로_본다(self):
        # open 으로 두면 폐업이 지도에 남는다. plan.md 1단계 완료 기준.
        place = hospitals.to_place({**SAMPLE, "SALS_STTS_CD": "99"}, None, None)
        self.assertEqual(place["status"], "closed")

    def test_빈_코드도_closed(self):
        place = hospitals.to_place({**SAMPLE, "SALS_STTS_CD": ""}, None, None)
        self.assertEqual(place["status"], "closed")


class ToPlaceTest(unittest.TestCase):
    def test_표본이_스키마대로_변환된다(self):
        place = hospitals.to_place(SAMPLE, "4121010200", (37.47927, 126.85812))
        self.assertEqual(place["category"], "hospital")
        self.assertEqual(place["source_id"], "390000001020260003")
        self.assertEqual(place["name"], "광명온동물병원")
        self.assertEqual(place["tel"], "02-6951-1003")
        self.assertEqual(place["address_jibun"], "경기도 광명시 광명동 155-3")
        self.assertEqual(place["region_code"], "4121010200")
        self.assertEqual(place["status"], "open")
        self.assertEqual(place["extra"]["opn_atmy_grp_cd"], "3900000")

    def test_빈_전화번호는_None(self):
        place = hospitals.to_place({**SAMPLE, "TELNO": ""}, None, None)
        self.assertIsNone(place["tel"])

    def test_기준일이_ISO8601_로_바뀐다(self):
        place = hospitals.to_place(SAMPLE, None, None)
        self.assertTrue(place["source_updated_at"].startswith("2026-09-04T21:02:00"))

    def test_기준일_형식이_어긋나면_None(self):
        place = hospitals.to_place({**SAMPLE, "DAT_UPDT_PNT": "어제"}, None, None)
        self.assertIsNone(place["source_updated_at"])


if __name__ == "__main__":
    unittest.main()
