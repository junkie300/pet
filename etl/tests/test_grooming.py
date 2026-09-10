# -*- coding: utf-8 -*-
"""2단계 미용업 테스트. 네트워크·DB 없이 돈다.

여기서 지키는 것은 둘이다.

1. **변환 코드를 베끼지 않았다** — 병원과 같은 함수를 쓰고 다른 것은 `Dataset` 뿐이다.
2. **가려진 주소를 다루는 규칙** — 읍면동은 배정하되 **좌표는 지어내지 않는다** (D-95).
"""

import unittest

from petetl.sources import grooming, hospitals

# 실제 응답에서 가져온 행. 번지가 `***` 로 가려져 있고 전화가 비어 있다.
SAMPLE = {
    "BPLC_NM": "댕댕그리",
    "MNG_NO": "400000004920260006",
    "TELNO": "",
    "LOTNO_ADDR": "경기도 오산시 갈곶동 *** *층",
    "ROAD_NM_ADDR": "경기도 오산시 오산로 **, *층 (갈곶동)",
    "ROAD_NM_ZIP": "18147",
    "CRD_INFO_X": "206044.269924547",
    "CRD_INFO_Y": "403605.843656149",
    "SALS_STTS_CD": "01",
    "SALS_STTS_NM": "영업/정상",
    "DTL_SALS_STTS_NM": "정상",
    "LCPMT_YMD": "2026-09-08",
    "CLSBIZ_YMD": "",
    "OPN_ATMY_GRP_CD": "4000000",
    "DAT_UPDT_PNT": "2026-09-09 21:32:50",
}


class DatasetTest(unittest.TestCase):
    def test_병원과_다른_것은_Dataset_뿐이다(self):
        # 이 시험이 깨지면 2단계가 "설정 추가"가 아니라 "코드 복제"가 된 것이다 (plan.md).
        self.assertIsInstance(grooming.GROOMING, hospitals.Dataset)

    def test_카테고리와_데이터셋_번호(self):
        self.assertEqual(grooming.GROOMING.category, "grooming")
        self.assertEqual(grooming.GROOMING.dataset, "15154944")

    def test_sync_source_가_병원과_갈린다(self):
        # 같으면 sync_logs 에서 어느 ETL 이 돌았는지 못 읽는다.
        self.assertNotEqual(grooming.GROOMING.sync_source, hospitals.SYNC_SOURCE)

    def test_좌표_지오코딩을_끄고_있다(self):
        # ⚠️ 이 값을 참으로 바꾸면 읍면동 중심이 가게 좌표로 실린다 (D-95).
        self.assertFalse(grooming.GROOMING.geocode_coords)
        self.assertTrue(hospitals.HOSPITAL.geocode_coords)


class MaskedAddressTest(unittest.TestCase):
    """번지가 가려져도 **읍면동은 앞 토큰으로 배정된다.** 거기까지는 안 가려진다."""

    def setUp(self):
        self.index = {
            "경기도 오산시 갈곶동": "4137010800",
            "부산광역시 중구 부평동1가": "2611011100",
            "부산광역시 중구 부평동2가": "2611011200",
        }

    def test_가려진_번지여도_읍면동이_잡힌다(self):
        self.assertEqual(
            hospitals.match_region("경기도 오산시 갈곶동 *** *층", self.index),
            "4137010800",
        )

    def test_동_이름_속_숫자까지_가려지면_못_잡는다(self):
        # `부평동4가` 가 `부평동*가` 로 온다. 1가~4가 중 무엇인지 알 길이 없다.
        # **찍어서 채우지 않는다** — 틀린 동에 넣느니 비워 둔다 (D-51 과 같은 판단).
        self.assertIsNone(
            hospitals.match_region("부산광역시 중구 부평동*가 **-*", self.index)
        )


class ToPlaceTest(unittest.TestCase):
    def setUp(self):
        transformer = hospitals.make_transformer()
        self.latlng = hospitals.to_latlng(
            transformer, SAMPLE["CRD_INFO_X"], SAMPLE["CRD_INFO_Y"]
        )
        self.place = hospitals.to_place(
            SAMPLE, "4137010800", self.latlng, grooming.GROOMING
        )

    def test_카테고리가_grooming_이다(self):
        self.assertEqual(self.place["category"], "grooming")

    def test_출처는_병원과_같다(self):
        # 둘 다 지방행정 인허가데이터다. 화면의 출처 표기가 갈리면 안 된다 (spec.md §8).
        self.assertEqual(self.place["source"], hospitals.SOURCE)

    def test_데이터셋_번호가_extra_에_남는다(self):
        self.assertEqual(self.place["extra"]["dataset"], "15154944")

    def test_빈_전화번호는_None(self):
        # 미용업은 3분의 2가 전화가 없다. 빈 문자열로 넣으면 상세에서 빈 버튼이 생긴다.
        self.assertIsNone(self.place["tel"])

    def test_가려진_주소도_그대로_싣는다(self):
        # 지어내지도, 버리지도 않는다. 원본이 그렇다는 것을 화면이 보여주면 된다.
        self.assertEqual(self.place["address_jibun"], "경기도 오산시 갈곶동 *** *층")

    def test_좌표는_원본_평면좌표에서_온다(self):
        lat, lng = self.latlng
        self.assertAlmostEqual(lat, 37.14, places=1)
        self.assertAlmostEqual(lng, 127.05, places=1)


class NoFakeCoordinateTest(unittest.TestCase):
    """좌표가 빈 행에 **카카오 좌표를 채워 넣지 않는다.**"""

    def setUp(self):
        self.calls = []
        self._saved = (hospitals.build_region_index, hospitals.lookup_kakao)
        hospitals.build_region_index = lambda client: {"경기도 오산시 갈곶동": "4137010800"}

        def fake_lookup(queries, codes, key):
            self.calls.append(queries)
            return "4137010800", (37.0, 127.0)  # 카카오가 읍면동 중심을 돌려준 셈

        hospitals.lookup_kakao = fake_lookup

    def tearDown(self):
        hospitals.build_region_index, hospitals.lookup_kakao = self._saved

    def _run(self, ds, address="경기도 오산시 갈곶동 *** *층"):
        item = dict(SAMPLE, CRD_INFO_X="", CRD_INFO_Y="", LOTNO_ADDR=address,
                    ROAD_NM_ADDR=address)  # 좌표가 빈 행
        places, stats = hospitals.build_places(None, [item], "카카오키", ds)
        return places[0], stats

    def test_미용은_좌표를_비운_채_싣는다(self):
        place, stats = self._run(grooming.GROOMING)
        self.assertIsNone(place["lat"])
        self.assertIsNone(place["lng"])
        self.assertEqual(stats["좌표 카카오보완"], 0)
        # 읍면동은 이름으로 이미 잡혔으므로 카카오를 부를 일도 없다.
        self.assertEqual(self.calls, [])

    def test_병원은_지금까지처럼_채운다(self):
        # 같은 코드가 소스에 따라 다르게 굴러야 한다 — 병원 쪽 동작이 바뀌면 안 된다.
        # 병원 주소는 가려지지 않으므로(실측 0%) 온전한 주소로 시험한다.
        place, stats = self._run(hospitals.HOSPITAL, "경기도 광명시 광명동 155-3")
        self.assertEqual((place["lat"], place["lng"]), (37.0, 127.0))
        self.assertEqual(stats["좌표 카카오보완"], 1)


if __name__ == "__main__":
    unittest.main()


class ReverseLookupTest(unittest.TestCase):
    """`동*가` 는 이름으로 영영 못 맞춘다. **좌표로 되찾는다** (D-95)."""

    MASKED = dict(
        SAMPLE,
        BPLC_NM="평화동가게",
        MNG_NO="520000004920260001",
        LOTNO_ADDR="전북특별자치도 전주시 완산구 평화동*가 ***-*",
        ROAD_NM_ADDR="전북특별자치도 전주시 완산구 어딘가로 **",
    )

    def setUp(self):
        self.reverse_calls = []
        self.address_calls = []
        self._saved = (hospitals.build_region_index,
                       hospitals.reverse_lookup_kakao, hospitals.lookup_kakao)
        hospitals.build_region_index = lambda client: {
            "전북특별자치도 전주시 완산구 평화동1가": "5211113200",
        }

        def fake_reverse(latlng, codes, key):
            self.reverse_calls.append(latlng)
            return "5211113200"

        def fake_address(queries, codes, key):
            self.address_calls.append(queries)
            return "9999999999", (37.0, 127.0)

        hospitals.reverse_lookup_kakao = fake_reverse
        hospitals.lookup_kakao = fake_address

    def tearDown(self):
        (hospitals.build_region_index, hospitals.reverse_lookup_kakao,
         hospitals.lookup_kakao) = self._saved

    def test_좌표로_읍면동을_되찾는다(self):
        places, stats = hospitals.build_places(
            None, [self.MASKED], "카카오키", grooming.GROOMING
        )
        self.assertEqual(places[0]["region_code"], "5211113200")
        self.assertEqual(stats["지역 좌표역추적"], 1)

    def test_좌표로_찾았으면_주소는_묻지_않는다(self):
        # 가려진 주소를 또 묻는 것은 호출만 늘고 답은 안 좋아진다.
        hospitals.build_places(None, [self.MASKED], "카카오키", grooming.GROOMING)
        self.assertEqual(len(self.reverse_calls), 1)
        self.assertEqual(self.address_calls, [])

    def test_좌표가_없으면_되찾을_것도_없다(self):
        item = dict(self.MASKED, CRD_INFO_X="", CRD_INFO_Y="")
        places, stats = hospitals.build_places(
            None, [item], "카카오키", grooming.GROOMING
        )
        self.assertEqual(self.reverse_calls, [])
        self.assertEqual(stats["지역 좌표역추적"], 0)
        # 주소가 가려져 있으므로 주소로도 묻지 않는다 (카카오는 0건을 준다 — 실측).
        # 남는 것은 미배정이다. 지어내는 것보다 낫다.
        self.assertEqual(self.address_calls, [])
        self.assertIsNone(places[0]["region_code"])
        self.assertEqual(stats["지역 미배정"], 1)
